package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.ArrayList;
import java.util.List;

import com.github.gradusnikov.eclipse.assistai.mcp.results.GitStatusResponse.GitFileChange;

/**
 * The outcome of staging a patch into the index without changing the working tree.
 * <p>
 * This one is a record because of what a thrown exception cannot carry. Staging a patch
 * works by saving the working-tree content of every file the patch touches, checking
 * those files out from HEAD, applying the patch to the clean copies, staging the result
 * and putting the saved content back. If anything in the middle fails - and a
 * model-generated patch failing to apply is the common case - the caller's uncommitted
 * work is, for a moment, not on disk. {@link #workingTreePreserved()} is the answer to
 * the only question that matters then, and it has to travel with the failure rather
 * than instead of it.
 * <p>
 * A patch that parses to no file headers is a failure too, not the success that
 * "No files affected by patch." used to report: nothing was staged, and the caller's
 * next commit would have been empty or wrong.
 *
 * @param status whether the index was updated
 * @param files the index entries the patch actually created or changed, so a patch that
 *            matched nothing reports {@code totalFiles: 0} rather than a confirmation
 * @param workingTreePreserved whether every file whose content was saved got that exact
 *            content back. False is a data-loss report and always carries a diagnostic
 * @param restoredPaths the repository-relative paths that were restored
 */
public record GitStagePatchResponse(
    String projectName,
    PatchStatus status,
    int totalFiles,
    List<GitFileChange> files,
    boolean workingTreePreserved,
    List<String> restoredPaths,
    List<Diagnostic> diagnostics,
    String summaryText
)
{
    public enum PatchStatus
    {
        /** The patch applied and the index was updated. */
        STAGED,
        /** Nothing was staged. See diagnostics. */
        FAILED
    }

    public static GitStagePatchResponse staged( String projectName, List<GitFileChange> files,
            boolean workingTreePreserved, List<String> restoredPaths, List<Diagnostic> diagnostics )
    {
        List<GitFileChange> staged = files == null ? List.of() : List.copyOf( files );
        return new GitStagePatchResponse( projectName, PatchStatus.STAGED, staged.size(), staged,
                workingTreePreserved, restoredPaths == null ? List.of() : List.copyOf( restoredPaths ),
                diagnostics == null ? List.of() : List.copyOf( diagnostics ),
                summarize( PatchStatus.STAGED, staged.size(), workingTreePreserved ) );
    }

    public static GitStagePatchResponse failed( String projectName, boolean workingTreePreserved,
            List<String> restoredPaths, List<Diagnostic> diagnostics )
    {
        return new GitStagePatchResponse( projectName, PatchStatus.FAILED, 0, List.of(), workingTreePreserved,
                restoredPaths == null ? List.of() : List.copyOf( restoredPaths ),
                diagnostics == null ? List.of() : List.copyOf( diagnostics ),
                summarize( PatchStatus.FAILED, 0, workingTreePreserved ) );
    }

    /** A failure that happened before anything on disk was touched. */
    public static GitStagePatchResponse rejected( String projectName, Diagnostic... diagnostics )
    {
        return failed( projectName, true, List.of(), List.of( diagnostics ) );
    }

    private static String summarize( PatchStatus status, int files, boolean workingTreePreserved )
    {
        List<String> parts = new ArrayList<>();
        if ( status == PatchStatus.STAGED )
        {
            parts.add( "Staged " + files + " file(s) from the patch." );
        }
        else
        {
            parts.add( "The patch was not staged." );
        }
        if ( !workingTreePreserved )
        {
            parts.add( "The working tree content of some files could not be restored." );
        }
        return String.join( " ", parts );
    }
}
