package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;

import com.github.gradusnikov.eclipse.assistai.mcp.services.SearchService;

/**
 * The result of a workspace text search.
 * <p>
 * The search engine already produced one entry per match; until now they were handed
 * to a client as {@code List.toString()}, which yields
 * {@code [SearchResult[file=L/Project/src/..., lineNumber=42, ...]]} - a Java debug
 * rendering that an agent then had to unpick with a regular expression.
 */
public record SearchResponse(
    String query,
    int totalMatches,
    int filesMatched,
    List<SearchMatch> matches,
    boolean truncated,
    String summaryText
)
{
    /**
     * One matched line.
     *
     * @param projectName the Eclipse project, so the match can be fed straight back
     *            into the reading and editing tools, which address files that way
     * @param filePath path relative to the project root, matching what those tools take
     * @param lineNumber 1-based
     */
    public record SearchMatch(
        String projectName,
        String filePath,
        int lineNumber,
        String lineContent
    )
    {
    }

    /**
     * Converts the service's workspace-typed results into transport-friendly ones.
     * <p>
     * {@link IFile} cannot be serialized, and a client cannot use it anyway: what it
     * needs is the project and path pair that every other tool accepts.
     */
    public static SearchResponse from( String query, List<SearchService.SearchResult> results, int limit )
    {
        List<SearchMatch> matches = new ArrayList<>();
        Set<String> files = new LinkedHashSet<>();

        for ( SearchService.SearchResult result : results )
        {
            IFile file = result.file();
            if ( file != null )
            {
                files.add( file.getFullPath().toString() );
            }
            if ( limit <= 0 || matches.size() < limit )
            {
                matches.add( new SearchMatch(
                        file == null ? null : file.getProject().getName(),
                        file == null ? null : file.getProjectRelativePath().toString(),
                        result.lineNumber(),
                        result.lineContent() ) );
            }
        }

        boolean truncated = limit > 0 && results.size() > matches.size();

        return new SearchResponse(
                query,
                results.size(),
                files.size(),
                matches,
                truncated,
                summarize( results.size(), files.size(), truncated, matches.size() ) );
    }

    private static String summarize( int totalMatches, int filesMatched, boolean truncated, int shown )
    {
        if ( totalMatches == 0 )
        {
            return "No matches.";
        }
        String summary = totalMatches + ( totalMatches == 1 ? " match in " : " matches in " )
                + filesMatched + ( filesMatched == 1 ? " file." : " files." );
        return truncated ? summary + " Showing the first " + shown + "." : summary;
    }

}
