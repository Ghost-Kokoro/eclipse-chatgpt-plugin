package com.github.gradusnikov.eclipse.assistai.mcp.services;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.e4.core.di.annotations.Creatable;

import com.github.gradusnikov.eclipse.assistai.mcp.results.Diagnostic;
import com.github.gradusnikov.eclipse.assistai.mcp.results.DiagnosticCode;
import com.github.gradusnikov.eclipse.assistai.mcp.results.MarkdownOutlineResponse;
import com.github.gradusnikov.eclipse.assistai.resources.ContentRange;
import com.github.gradusnikov.eclipse.assistai.resources.ResourceDescriptor;
import com.github.gradusnikov.eclipse.assistai.resources.ResourceReadResult;
import com.github.gradusnikov.eclipse.assistai.resources.ResourceVersion;
import com.github.gradusnikov.eclipse.assistai.resources.SourceOrigin;
import com.github.gradusnikov.eclipse.assistai.services.AiIgnoreService;
import com.github.gradusnikov.eclipse.assistai.tools.ResourceUtilities;

import jakarta.inject.Inject;

/**
 * Navigates a Markdown document by its headings.
 * <p>
 * The outline is a list of headings with their levels and line ranges; a section is a
 * range of the file, so it is returned as a {@link ResourceReadResult} like every
 * other read. Neither carries line-number prefixes any more - the numbers are ranges
 * in the result.
 */
@Creatable
public class MarkdownService
{
    private static final Pattern ATX_HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*\\s*$");
    private static final Pattern SETEXT_H1 = Pattern.compile("^={3,}\\s*$");
    private static final Pattern SETEXT_H2 = Pattern.compile("^-{3,}\\s*$");

    @Inject
    AiIgnoreService aiIgnoreService;

    private static record HeadingInfo(int level, String text, int lineNumber) {}

    /**
     * The file, or a diagnostic saying why it could not be opened.
     * <p>
     * Resolution and reading are one step because every caller needs both the
     * {@link IFile} - for the version an edit would quote - and its lines.
     */
    private record MarkdownFile( IFile file, List<String> lines, String language, Diagnostic diagnostic )
    {
        static MarkdownFile failed( DiagnosticCode code, String message )
        {
            return new MarkdownFile( null, List.of(), null, Diagnostic.fatal( code, message ) );
        }
    }

    private MarkdownFile openFile( String projectName, String resourcePath )
    {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject( projectName );
        if ( project == null || !project.exists() )
        {
            return MarkdownFile.failed( DiagnosticCode.RESOURCE_NOT_FOUND, "Project '" + projectName + "' not found." );
        }
        if ( !project.isOpen() )
        {
            return MarkdownFile.failed( DiagnosticCode.RESOURCE_NOT_ACCESSIBLE,
                    "Project '" + projectName + "' is closed." );
        }

        IFile file = project.getFile( IPath.fromPath( Path.of( resourcePath ) ) );
        if ( !file.exists() )
        {
            return MarkdownFile.failed( DiagnosticCode.RESOURCE_NOT_FOUND,
                    "File '" + resourcePath + "' does not exist in project '" + projectName + "'." );
        }
        if ( aiIgnoreService.isExcluded( file ) )
        {
            return MarkdownFile.failed( DiagnosticCode.RESOURCE_NOT_ACCESSIBLE,
                    "'" + resourcePath + "' is excluded from AI processing by .aiignore." );
        }

        try
        {
            return new MarkdownFile( file, ResourceUtilities.readFileLines( file ),
                    ResourceUtilities.getResourceFileType( file ), null );
        }
        catch ( IOException | CoreException e )
        {
            return MarkdownFile.failed( DiagnosticCode.INTERNAL_ERROR, "Error reading file: " + e.getMessage() );
        }
    }

    private List<HeadingInfo> parseHeadings(List<String> lines)
    {
        List<HeadingInfo> headings = new ArrayList<>();
        boolean inCodeFence = false;

        for (int i = 0; i < lines.size(); i++)
        {
            String line = lines.get(i);
            String trimmed = line.trim();

            if (trimmed.startsWith("```") || trimmed.startsWith("~~~"))
            {
                inCodeFence = !inCodeFence;
                continue;
            }
            if (inCodeFence)
            {
                continue;
            }

            Matcher atxMatcher = ATX_HEADING.matcher(line);
            if (atxMatcher.matches())
            {
                int level = atxMatcher.group(1).length();
                String text = atxMatcher.group(2).trim();
                headings.add(new HeadingInfo(level, text, i + 1));
                continue;
            }

            // Setext headings: non-empty line followed by === or ---
            if (!trimmed.isEmpty() && i + 1 < lines.size())
            {
                String nextLine = lines.get(i + 1).trim();
                if (SETEXT_H1.matcher(nextLine).matches())
                {
                    headings.add(new HeadingInfo(1, trimmed, i + 1));
                }
                else if (SETEXT_H2.matcher(nextLine).matches())
                {
                    headings.add(new HeadingInfo(2, trimmed, i + 1));
                }
            }
        }

        return headings;
    }

    /**
     * The heading structure of a Markdown file.
     * <p>
     * A file with no headings is an empty list, not a failure: it is a fact about the
     * document, and a caller that has to tell it apart from "the file is missing" by
     * reading a sentence has been given the wrong result type.
     */
    public MarkdownOutlineResponse getOutline( String projectName, String resourcePath )
    {
        MarkdownFile opened = openFile( projectName, resourcePath );
        if ( opened.diagnostic() != null )
        {
            return MarkdownOutlineResponse.failed( projectName, resourcePath, opened.diagnostic() );
        }

        List<String> lines = opened.lines();
        List<HeadingInfo> headings = parseHeadings( lines );
        List<MarkdownOutlineResponse.Heading> entries = new ArrayList<>();

        for ( int i = 0; i < headings.size(); i++ )
        {
            HeadingInfo heading = headings.get( i );
            int endLine = ( i + 1 < headings.size() ) ? headings.get( i + 1 ).lineNumber() - 1 : lines.size();
            entries.add( new MarkdownOutlineResponse.Heading(
                    i + 1,
                    heading.level(),
                    heading.text(),
                    new ContentRange( heading.lineNumber(), 1, Math.max( heading.lineNumber(), endLine ), 1 ) ) );
        }

        return MarkdownOutlineResponse.of( projectName, opened.file().getProjectRelativePath().toString(),
                lines.size(), entries );
    }

    /**
     * One section of a Markdown file, addressed by heading text or by its 1-based
     * index in the outline.
     * <p>
     * The section is a contiguous range of a workspace file, so it is a
     * {@link ResourceReadResult}: exact content, {@code returnedRange} saying which
     * lines it is, and a {@code version} an edit can quote. {@code truncated} is false
     * - the whole section is returned; that it is not the whole file is what
     * {@code returnedRange} and the {@code PARTIAL} status are for.
     *
     * @param heading a 1-based index from {@link #getOutline}, or a case-insensitive
     *            substring of a heading's text
     * @param includeSubsections whether the section runs to the next heading of the
     *            same or a higher level rather than to the next heading of any level
     */
    public ResourceReadResult getSection( String projectName, String resourcePath, String heading,
                                          boolean includeSubsections )
    {
        MarkdownFile opened = openFile( projectName, resourcePath );
        if ( opened.diagnostic() != null )
        {
            return ResourceReadResult.failed( projectName, resourcePath, opened.diagnostic() );
        }

        IFile file = opened.file();
        String filePath = file.getProjectRelativePath().toString();
        List<String> lines = opened.lines();
        List<HeadingInfo> headings = parseHeadings( lines );

        if ( headings.isEmpty() )
        {
            return ResourceReadResult.failed( projectName, filePath, Diagnostic.fatal(
                    DiagnosticCode.RESOURCE_NOT_FOUND, "'" + resourcePath + "' has no Markdown headings." ) );
        }

        int targetIndex = indexOf( headings, heading );
        if ( targetIndex < 0 )
        {
            return ResourceReadResult.failed( projectName, filePath, Diagnostic.fatal(
                    DiagnosticCode.RESOURCE_NOT_FOUND, "No heading matches '" + heading
                            + "'. Use getMarkdownOutline to see the available headings and their indices." ) );
        }

        HeadingInfo target = headings.get( targetIndex );
        int startLine = target.lineNumber();
        int endLine = lines.size();

        for ( int i = targetIndex + 1; i < headings.size(); i++ )
        {
            HeadingInfo next = headings.get( i );
            if ( !includeSubsections || next.level() <= target.level() )
            {
                endLine = next.lineNumber() - 1;
                break;
            }
        }

        StringBuilder content = new StringBuilder();
        for ( int i = startLine - 1; i < endLine && i < lines.size(); i++ )
        {
            content.append( lines.get( i ) ).append( "\n" );
        }

        boolean whole = startLine == 1 && endLine >= lines.size();
        return new ResourceReadResult(
                whole ? ResourceReadResult.ReadStatus.OK : ResourceReadResult.ReadStatus.PARTIAL,
                ResourceDescriptor.fromWorkspaceFile( file, "getMarkdownSection" ).uri().toString(),
                projectName,
                filePath,
                opened.language(),
                ResourceVersion.of( file ),
                new ContentRange( startLine, 1, Math.max( startLine, endLine ), 1 ),
                lines.size(),
                content.toString(),
                SourceOrigin.WORKSPACE_SOURCE,
                false,
                false,
                List.of(),
                Diagnostic.none() );
    }

    /**
     * The index of the heading a caller named, or -1.
     * <p>
     * A numeric argument is taken as a 1-based index first, because two sections of a
     * long document routinely share a title and matching by text would then silently
     * fetch the wrong one.
     */
    private int indexOf( List<HeadingInfo> headings, String heading )
    {
        try
        {
            int index = Integer.parseInt( heading.trim() ) - 1;
            if ( index >= 0 && index < headings.size() )
            {
                return index;
            }
            return -1;
        }
        catch ( NumberFormatException e )
        {
            // Not a number - fall through to a text match.
        }

        String needle = heading.toLowerCase().trim();
        for ( int i = 0; i < headings.size(); i++ )
        {
            if ( headings.get( i ).text().toLowerCase().contains( needle ) )
            {
                return i;
            }
        }
        return -1;
    }
}
