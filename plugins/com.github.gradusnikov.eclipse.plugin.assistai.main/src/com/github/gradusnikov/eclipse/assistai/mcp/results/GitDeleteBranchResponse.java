package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * The outcome of deleting a branch.
 * <p>
 * The tool used to answer "Deleted branch: " with JGit's deleted refs comma-joined
 * behind it, and to throw when the branch was not fully merged - the one failure here
 * whose remedy is mechanical. Refusing to delete unmerged work is the safety check
 * doing its job, not a fault, so it arrives as {@code deleted: false} with a
 * {@code BRANCH_NOT_MERGED} diagnostic naming the retry.
 *
 * @param deleted whether the branch is gone; the single question this tool answers
 * @param deletedRefs the full refs JGit removed, {@code refs/heads/…}
 * @param forced whether the caller asked for the unmerged check to be skipped
 */
public record GitDeleteBranchResponse(
    String projectName,
    String branchName,
    boolean forced,
    boolean deleted,
    List<String> deletedRefs,
    List<Diagnostic> diagnostics,
    String summaryText
)
{
    public static GitDeleteBranchResponse deleted( String projectName, String branchName, boolean forced,
            List<String> deletedRefs )
    {
        List<String> refs = deletedRefs == null ? List.of() : List.copyOf( deletedRefs );
        return new GitDeleteBranchResponse( projectName, branchName, forced, !refs.isEmpty(), refs, List.of(),
                refs.isEmpty() ? "No branch was deleted." : "Deleted " + refs.size() + " branch ref(s)." );
    }

    public static GitDeleteBranchResponse failed( String projectName, String branchName, boolean forced,
            Diagnostic diagnostic )
    {
        return new GitDeleteBranchResponse( projectName, branchName, forced, false, List.of(),
                List.of( diagnostic ), "Branch " + branchName + " was not deleted." );
    }
}
