package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.ArrayList;
import java.util.List;

/**
 * Workspace files matching a set of glob patterns.
 * <p>
 * Previously the tool returned {@code List.toString()}, so a caller received
 * {@code [/Project/src/A.java, /Project/pom.xml]} - bracket-and-comma syntax to parse,
 * and workspace-absolute paths that none of the reading or editing tools accept. Each
 * entry now carries the project and project-relative path those tools take.
 */
public record FileListResponse(
    List<String> patterns,
    int totalFiles,
    List<WorkspaceFile> files,
    boolean truncated,
    String summaryText
)
{
    public record WorkspaceFile(
        String projectName,
        String filePath
    )
    {
    }

    /**
     * @param workspacePaths full workspace paths, {@code /Project/dir/file.ext}
     * @param limit 0 or less for no limit
     */
    public static FileListResponse from( String[] patterns, List<String> workspacePaths, int limit )
    {
        List<WorkspaceFile> files = new ArrayList<>();
        int shown = limit > 0 ? Math.min( workspacePaths.size(), limit ) : workspacePaths.size();

        for ( int i = 0; i < shown; i++ )
        {
            files.add( split( workspacePaths.get( i ) ) );
        }

        boolean truncated = workspacePaths.size() > shown;
        String summary = workspacePaths.isEmpty()
                ? "No matching files."
                : workspacePaths.size() + ( workspacePaths.size() == 1 ? " file." : " files." )
                        + ( truncated ? " Showing the first " + shown + "." : "" );

        return new FileListResponse( List.of( patterns ), workspacePaths.size(), files, truncated, summary );
    }

    /** Splits {@code /Project/dir/file.ext} into its project and the rest. */
    private static WorkspaceFile split( String workspacePath )
    {
        String path = workspacePath.startsWith( "/" ) ? workspacePath.substring( 1 ) : workspacePath;
        int separator = path.indexOf( '/' );
        if ( separator < 0 )
        {
            return new WorkspaceFile( path, "" );
        }
        return new WorkspaceFile( path.substring( 0, separator ), path.substring( separator + 1 ) );
    }
}
