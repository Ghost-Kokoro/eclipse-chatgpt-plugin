package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

import com.github.gradusnikov.eclipse.assistai.mcp.results.GitStatusResponse.GitFileChange;

/**
 * What a {@code gitStashPop} actually did.
 * <p>
 * The tool used to answer "Applied and dropped stash." - a sentence that is a lie in
 * the one case a caller must notice. A stash that does not apply cleanly throws out of
 * the apply, so the drop never runs: the entry is still there, the working tree now
 * carries conflict markers, and the caller learned none of that. Applying and dropping
 * are two facts and are reported as two.
 *
 * @param status which of the three outcomes happened
 * @param dropped whether the stash entry was removed; false after a conflict, where the
 *            entry is deliberately kept so the work can be recovered
 * @param stashRef the entry that was applied, still addressable when it was kept
 * @param conflicting the files left with conflict markers, each naming the project and
 *            project-relative path the editing tools take
 */
public record GitStashPopResponse(
    String projectName,
    PopStatus status,
    boolean dropped,
    String stashRef,
    String stashSha,
    String stashMessage,
    List<GitFileChange> conflicting,
    List<Diagnostic> diagnostics,
    String summaryText
)
{
    public enum PopStatus
    {
        /** The stash applied cleanly and was removed. */
        APPLIED,
        /** The stash applied with conflicts and was kept. */
        CONFLICTED,
        /** The stash was empty. An outcome, not a failure. */
        NOTHING_TO_APPLY
    }

    public static GitStashPopResponse applied( String projectName, String stashRef, String stashSha, String stashMessage )
    {
        return new GitStashPopResponse( projectName, PopStatus.APPLIED, true, stashRef, stashSha, stashMessage,
                List.of(), List.of(), "Applied " + stashRef + " and dropped it." );
    }

    public static GitStashPopResponse conflicted( String projectName, String stashRef, String stashSha,
            String stashMessage, List<GitFileChange> conflicting, Diagnostic diagnostic )
    {
        List<GitFileChange> conflicts = conflicting == null ? List.of() : List.copyOf( conflicting );
        return new GitStashPopResponse( projectName, PopStatus.CONFLICTED, false, stashRef, stashSha, stashMessage,
                conflicts, List.of( diagnostic ),
                "Applying " + stashRef + " left " + conflicts.size() + " conflicting file(s); the stash was kept." );
    }

    public static GitStashPopResponse nothingToApply( String projectName )
    {
        return new GitStashPopResponse( projectName, PopStatus.NOTHING_TO_APPLY, false, null, null, null,
                List.of(), List.of(), "No stash entries to apply." );
    }
}
