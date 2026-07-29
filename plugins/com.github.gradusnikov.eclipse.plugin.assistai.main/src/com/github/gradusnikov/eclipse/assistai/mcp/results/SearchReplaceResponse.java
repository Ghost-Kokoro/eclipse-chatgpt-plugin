package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;

import com.github.gradusnikov.eclipse.assistai.mcp.services.SearchService;

/**
 * The result of a workspace-wide search and replace.
 * <p>
 * Reports per file how many occurrences were found and how many were actually
 * replaced. The two can differ - a file may be read-only or excluded - and a caller
 * that only saw a total would never know which files it missed.
 */
public record SearchReplaceResponse(
    String searchText,
    String replacementText,
    int filesChanged,
    int totalMatches,
    int totalReplacements,
    List<FileReplacement> files,
    String summaryText
)
{
    public record FileReplacement(
        String projectName,
        String filePath,
        int matchesFound,
        int replacementsMade
    )
    {
        /**
         * Whether this file still holds occurrences that were not replaced. Ignored
         * by the mapper so the payload matches its schema.
         */
        @com.fasterxml.jackson.annotation.JsonIgnore
        public boolean isIncomplete()
        {
            return replacementsMade < matchesFound;
        }
    }

    public static SearchReplaceResponse from( String searchText, String replacementText,
                                              List<SearchService.SearchAndReplaceResult> results )
    {
        List<FileReplacement> files = new ArrayList<>();
        int totalMatches = 0;
        int totalReplacements = 0;
        int changed = 0;

        for ( SearchService.SearchAndReplaceResult result : results )
        {
            IFile file = result.file();
            files.add( new FileReplacement(
                    file == null ? null : file.getProject().getName(),
                    file == null ? null : file.getProjectRelativePath().toString(),
                    result.matchesFound(),
                    result.replacementsMade() ) );
            totalMatches += result.matchesFound();
            totalReplacements += result.replacementsMade();
            if ( result.replacementsMade() > 0 )
            {
                changed++;
            }
        }

        String summary = totalReplacements == 0
                ? "No replacements were made."
                : totalReplacements + ( totalReplacements == 1 ? " replacement in " : " replacements in " )
                        + changed + ( changed == 1 ? " file." : " files." );

        return new SearchReplaceResponse( searchText, replacementText, changed, totalMatches,
                totalReplacements, files, summary );
    }

}
