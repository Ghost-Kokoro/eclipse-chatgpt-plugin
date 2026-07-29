package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

import com.github.gradusnikov.eclipse.assistai.mcp.results.GitStatusResponse.GitFileChange;

/**
 * The outcome of switching the working tree to another branch.
 * <p>
 * Two things were lost in prose. A checkout blocked by local changes threw with JGit's
 * conflicting paths flattened into the exception message, so the one actionable fact -
 * <em>which</em> files stand in the way - had to be recovered by splitting a sentence;
 * they are now a list, each entry naming the project and project-relative path the
 * editing tools take. And a checkout rewrites the working tree of the whole repository,
 * which routinely holds more than one Eclipse project, while only the named project was
 * ever refreshed: every sibling was left stale and nothing said so.
 * {@link #refreshedProjects()} is what was refreshed, so a caller can see whether that
 * covers the files it is about to read.
 *
 * @param requestedBranch the branch that was asked for
 * @param previousBranch the branch that was checked out before
 * @param currentBranch the branch checked out afterwards - unchanged when the checkout
 *            was blocked
 * @param headSha where HEAD points afterwards, or null in a repository with no commits
 * @param blockingFiles the local changes a blocked checkout would have overwritten
 */
public record GitCheckoutResponse(
    String projectName,
    CheckoutStatus status,
    String requestedBranch,
    String previousBranch,
    String currentBranch,
    String headSha,
    List<GitFileChange> blockingFiles,
    List<String> refreshedProjects,
    List<Diagnostic> diagnostics,
    String summaryText
)
{
    public enum CheckoutStatus
    {
        /** The working tree is now on the requested branch. */
        SWITCHED,
        /** Local changes would have been overwritten; nothing was switched. */
        BLOCKED
    }

    public static GitCheckoutResponse switched( String projectName, String requestedBranch, String previousBranch,
            String currentBranch, String headSha, List<String> refreshedProjects )
    {
        List<String> refreshed = refreshedProjects == null ? List.of() : List.copyOf( refreshedProjects );
        return new GitCheckoutResponse( projectName, CheckoutStatus.SWITCHED, requestedBranch, previousBranch,
                currentBranch, headSha, List.of(), refreshed, List.of(),
                "Switched to " + currentBranch + "; refreshed " + refreshed.size() + " project(s)." );
    }

    public static GitCheckoutResponse blocked( String projectName, String requestedBranch, String currentBranch,
            String headSha, List<GitFileChange> blockingFiles, Diagnostic diagnostic )
    {
        List<GitFileChange> blocking = blockingFiles == null ? List.of() : List.copyOf( blockingFiles );
        return new GitCheckoutResponse( projectName, CheckoutStatus.BLOCKED, requestedBranch, currentBranch,
                currentBranch, headSha, blocking, List.of(), List.of( diagnostic ),
                "Still on " + currentBranch + ": " + blocking.size() + " local change(s) block the checkout." );
    }
}
