
package com.github.gradusnikov.eclipse.assistai.mcp.services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IPath;
import org.eclipse.e4.core.di.annotations.Creatable;
import org.eclipse.search.core.text.TextSearchEngine;
import org.eclipse.search.core.text.TextSearchMatchAccess;
import org.eclipse.search.core.text.TextSearchRequestor;
import org.eclipse.search.core.text.TextSearchScope;

import com.github.gradusnikov.eclipse.assistai.resources.ContentRange;
import com.github.gradusnikov.eclipse.assistai.mcp.results.Diagnostic;
import com.github.gradusnikov.eclipse.assistai.mcp.results.DiagnosticCode;
import com.github.gradusnikov.eclipse.assistai.resources.ResourceDescriptor;
import com.github.gradusnikov.eclipse.assistai.resources.ResourceReadResult;
import com.github.gradusnikov.eclipse.assistai.resources.ResourceVersion;
import com.github.gradusnikov.eclipse.assistai.resources.SourceOrigin;
import com.github.gradusnikov.eclipse.assistai.services.AiIgnoreService;
import com.github.gradusnikov.eclipse.assistai.tools.ResourceUtilities;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Service interface for resource-related operations including reading project
 * resources.
 */
@Creatable
@Singleton
public class ResourceService
{
    public static final int MAX_IMAGE_RESOURCE_BYTES = 20 * 1024 * 1024;

    public record ImageResource( byte[] data, String mimeType )
    {
    }

    @Inject
    ILog            logger;

    @Inject
    AiIgnoreService aiIgnoreService;

    /**
     * Finds workspace files matching the given glob patterns.
     *
     * @param fileNamePatterns
     *            Glob patterns (e.g. "*.java", "pom.xml"). If omitted, defaults
     *            to "*".
     * @param maxResults
     *            Maximum number of results to return (<=0 means default 200)
     * @return List of workspace-relative file paths
     */
    public List<String> findFiles( String[] fileNamePatterns, Integer maxResults )
    {
        Pattern fileNamePattern = ResourceUtilities.globPatternsToRegex( fileNamePatterns );

        int limit = ( maxResults == null || maxResults <= 0 ) ? 200 : maxResults.intValue();

        IResource[] roots = getOpenProjectsAsRoots();
        if ( roots.length == 0 )
        {
            return List.of();
        }

        TextSearchScope scope = TextSearchScope.newSearchScope( roots, fileNamePattern, true );
        TextSearchEngine engine = TextSearchEngine.createDefault();

        List<String> matches = new ArrayList<>();

        TextSearchRequestor requestor = new TextSearchRequestor()
        {
            @Override
            public boolean acceptFile( IFile file ) throws CoreException
            {
                if ( matches.size() >= limit )
                {
                    return false;
                }

                return file != null && file.isAccessible() && !aiIgnoreService.isExcluded( file );
            }

            @Override
            public boolean acceptPatternMatch( TextSearchMatchAccess matchAccess ) throws CoreException
            {
                // We only need the file, not match positions.
                IFile file = matchAccess.getFile();
                if ( file != null )
                {
                    String path = file.getFullPath().toString();
                    if ( !matches.contains( path ) )
                    {
                        matches.add( path );
                    }
                }

                return matches.size() < limit;
            }
        };

        try
        {
            // Search for a pattern that matches at least one char, to force the
            // engine to scan.
            // (We only care about file enumeration, the fileNamePattern already
            // limits the scope.)
            engine.search( scope, requestor, Pattern.compile( "." ), null );
            return matches;
        }
        catch ( Exception e )
        {
            logger.error( "Error finding files: " + e.getMessage(), e );
            throw new RuntimeException( "Error finding files: " + ExceptionUtils.getRootCauseMessage( e ), e );
        }
    }

    private static IResource[] getOpenProjectsAsRoots()
    {
        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        List<IResource> roots = new ArrayList<>();
        for ( IProject project : projects )
        {
            if ( project != null && project.exists() && project.isOpen() )
            {
                roots.add( project );
            }
        }
        return roots.toArray( IResource[]::new );
    }

    /** Reads a supported raster image from an accessible workspace project. */
    public ImageResource readImageResource( String projectName, String filePath )
    {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject( projectName );
        if ( project == null || !project.exists() )
        {
            throw new IllegalArgumentException( "Project '" + projectName + "' not found." );
        }
        if ( !project.isOpen() )
        {
            throw new IllegalArgumentException( "Project '" + projectName + "' is closed." );
        }

        IPath path = IPath.fromPath( Path.of( filePath ) );
        IFile file = project.getFile( path );
        if ( !file.exists() )
        {
            throw new IllegalArgumentException( "File '" + filePath + "' does not exist in project '" + projectName + "'." );
        }

        aiIgnoreService.assertAccessAllowed( file );
        String mimeType = imageMimeType( file );
        try (InputStream input = file.getContents())
        {
            byte[] data = input.readNBytes( MAX_IMAGE_RESOURCE_BYTES + 1 );
            if ( data.length > MAX_IMAGE_RESOURCE_BYTES )
            {
                throw new IllegalArgumentException( "Image resource exceeds the 20 MiB limit" );
            }
            return new ImageResource( data, mimeType );
        }
        catch ( IOException | CoreException e )
        {
            throw new RuntimeException( "Unable to read image resource '" + filePath + "'.", e );
        }
    }

    private String imageMimeType( IFile file )
    {
        String extension = file.getFileExtension();
        extension = extension == null ? "" : extension.toLowerCase( Locale.ROOT );
        return switch ( extension )
        {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "tif", "tiff" -> "image/tiff";
            case "ico" -> "image/vnd.microsoft.icon";
            default -> throw new IllegalArgumentException(
                    "Unsupported image resource extension: '" + extension + "'. Supported: png, jpg, jpeg, gif, bmp, tif, tiff, ico." );
        };
    }

    /**
     * Reads a workspace text resource as a structured result.
     * <p>
     * The content is exact: no Markdown fence, no header line, no line-number
     * prefixes. Where the previous rendering put the line number in front of each
     * line, {@code returnedRange.startLine} now says it once, and
     * {@code version.modificationStamp} gives the caller the token an edit quotes as
     * {@code expectedModificationStamp}. Nothing else produced that token, which is
     * why optimistic concurrency could not be used end to end before.
     *
     * @param startLine 1-based, 0 for the beginning
     * @param endLine 1-based inclusive, 0 for the end
     * @param excludeImports collapse a Java import block; the omitted lines are
     *            reported in {@code omittedRanges} rather than silently dropped
     */
    public ResourceReadResult readResource( String projectName, String filePath, int startLine, int endLine,
                                            boolean excludeImports )
    {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject( projectName );
        if ( project == null || !project.exists() )
        {
            return ResourceReadResult.failed( projectName, filePath, Diagnostic.fatal(
                    DiagnosticCode.RESOURCE_NOT_FOUND, "Project '" + projectName + "' not found." ) );
        }
        if ( !project.isOpen() )
        {
            return ResourceReadResult.failed( projectName, filePath, Diagnostic.fatal(
                    DiagnosticCode.RESOURCE_NOT_ACCESSIBLE, "Project '" + projectName + "' is closed." ) );
        }

        IFile file = project.getFile( IPath.fromPath( Path.of( filePath ) ) );
        if ( !file.exists() )
        {
            return ResourceReadResult.failed( projectName, filePath, Diagnostic.fatal(
                    DiagnosticCode.RESOURCE_NOT_FOUND,
                    "File '" + filePath + "' does not exist in project '" + projectName + "'." ) );
        }
        if ( aiIgnoreService.isExcluded( file ) )
        {
            return ResourceReadResult.failed( projectName, filePath, Diagnostic.fatal(
                    DiagnosticCode.RESOURCE_NOT_ACCESSIBLE,
                    "'" + filePath + "' is excluded from AI processing by .aiignore." ) );
        }

        try
        {
            String language = ResourceUtilities.getResourceFileType( file );
            List<String> lines = ResourceUtilities.readFileLines( file );
            int totalLines = lines.size();

            int effectiveStart = ( startLine > 0 ) ? Math.min( startLine, Math.max( 1, totalLines ) ) : 1;
            int effectiveEnd = ( endLine > 0 ) ? Math.min( endLine, totalLines ) : totalLines;

            List<ContentRange> omitted = new ArrayList<>();
            int importStart = -1;
            int importEnd = -1;
            if ( excludeImports && "java".equals( language ) )
            {
                for ( int i = 0; i < totalLines; i++ )
                {
                    if ( lines.get( i ).trim().startsWith( "import " ) )
                    {
                        if ( importStart == -1 )
                        {
                            importStart = i;
                        }
                        importEnd = i;
                    }
                }
            }

            StringBuilder content = new StringBuilder();
            for ( int i = effectiveStart - 1; i < effectiveEnd; i++ )
            {
                if ( importStart >= 0 && i >= importStart && i <= importEnd )
                {
                    continue;
                }
                content.append( lines.get( i ) ).append( "\n" );
            }

            if ( importStart >= 0 )
            {
                // Reported rather than silently dropped, so the caller knows the
                // content is not the whole range it asked for.
                omitted.add( new ContentRange( importStart + 1, 1, importEnd + 1, 1 ) );
            }

            boolean partial = effectiveStart > 1 || effectiveEnd < totalLines || !omitted.isEmpty();

            return new ResourceReadResult(
                    partial ? ResourceReadResult.ReadStatus.PARTIAL : ResourceReadResult.ReadStatus.OK,
                    ResourceDescriptor.fromWorkspaceFile( file, "readProjectResource" ).uri().toString(),
                    projectName,
                    file.getProjectRelativePath().toString(),
                    language,
                    ResourceVersion.of( file ),
                    new ContentRange( effectiveStart, 1, effectiveEnd, 1 ),
                    totalLines,
                    content.toString(),
                    SourceOrigin.WORKSPACE_SOURCE,
                    false,
                    // "we returned less than you asked for", not "this is a subset of
                    // the file". A caller that asks for lines 10-20 of a 100-line file
                    // got exactly what it requested; saying truncated there means the
                    // flag fires on every ordinary range read and stops meaning
                    // anything. status = PARTIAL already says "not the whole file".
                    endLine > 0 && effectiveEnd < endLine,
                    omitted,
                    Diagnostic.none() );
        }
        catch ( IOException | CoreException e )
        {
            logger.error( "Error reading resource: " + e.getMessage(), e );
            return ResourceReadResult.failed( projectName, filePath, Diagnostic.fatal(
                    DiagnosticCode.INTERNAL_ERROR, "Error reading file: " + e.getMessage() ) );
        }
    }
}
