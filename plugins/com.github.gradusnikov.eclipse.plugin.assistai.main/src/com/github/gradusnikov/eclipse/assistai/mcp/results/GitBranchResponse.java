package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.Ref;

/**
 * The branches of the repository a project is mapped into.
 * <p>
 * The listing used to mark the checked-out branch with a leading asterisk, so a caller
 * had to know that convention and strip two characters off every other line. The current
 * branch is now a flag on the branch it belongs to, and named outright in
 * {@link #currentBranch()}.
 *
 * @param branches local branches
 * @param remoteBranches remote-tracking branches, empty unless they were asked for; kept
 *            separate because only a local branch can be checked out or deleted
 */
public record GitBranchResponse(
    String projectName,
    String currentBranch,
    List<GitBranch> branches,
    List<GitBranch> remoteBranches,
    int totalBranches,
    String summaryText
)
{
    /**
     * One branch.
     *
     * @param name the short name, which is what the checkout, create and delete tools take
     * @param fullName the ref, {@code refs/heads/main} or {@code refs/remotes/origin/main}
     * @param sha the commit it points at, or null for an unborn or symbolic ref
     * @param current whether this is the checked-out branch
     */
    public record GitBranch(
        String name,
        String fullName,
        String sha,
        boolean current
    )
    {
    }

    public static GitBranchResponse from( String projectName, String currentBranch, List<Ref> refs )
    {
        List<GitBranch> local = new ArrayList<>();
        List<GitBranch> remote = new ArrayList<>();

        if ( refs != null )
        {
            for ( Ref ref : refs )
            {
                String fullName = ref.getName();
                String sha = ref.getObjectId() == null ? null : ref.getObjectId().getName();

                if ( fullName.startsWith( "refs/remotes/" ) )
                {
                    remote.add( new GitBranch( fullName.substring( "refs/remotes/".length() ), fullName, sha, false ) );
                }
                else
                {
                    String name = fullName.startsWith( "refs/heads/" ) ? fullName.substring( "refs/heads/".length() ) : fullName;
                    local.add( new GitBranch( name, fullName, sha, name.equals( currentBranch ) ) );
                }
            }
        }

        return of( projectName, currentBranch, local, remote );
    }

    public static GitBranchResponse of( String projectName, String currentBranch, List<GitBranch> branches,
            List<GitBranch> remoteBranches )
    {
        List<GitBranch> local = branches == null ? List.of() : List.copyOf( branches );
        List<GitBranch> remote = remoteBranches == null ? List.of() : List.copyOf( remoteBranches );

        return new GitBranchResponse( projectName, currentBranch, local, remote, local.size() + remote.size(),
                summarize( currentBranch, local.size(), remote.size() ) );
    }

    private static String summarize( String currentBranch, int local, int remote )
    {
        String summary = local + ( local == 1 ? " local branch" : " local branches" );
        if ( remote > 0 )
        {
            summary += ", " + remote + ( remote == 1 ? " remote-tracking branch" : " remote-tracking branches" );
        }
        return summary + ". On " + currentBranch + ".";
    }
}
