package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Status;

/**
 * The working tree state of the Git repository a project is mapped into.
 * <p>
 * Until now this was {@code git status} prose - "Changes to be committed:", two spaces,
 * "modified:   ", a repository-relative path - which a caller had to parse by column
 * position, and whose paths none of the reading or editing tools accept. Every entry now
 * names the Eclipse project and the project-relative path those tools take, alongside the
 * repository path Git itself uses.
 * <p>
 * A clean working tree is a result, not an absence of one: {@link #clean()} says so and
 * the four lists are empty, so a caller never has to recognize the sentence
 * "nothing to commit, working tree clean".
 *
 * @param upstreamBranch the tracking branch, or null when the branch tracks nothing
 * @param aheadCount commits the branch has that its upstream does not, null without an
 *            upstream - which is a different thing from being level with one
 * @param behindCount commits the upstream has that the branch does not, null without an
 *            upstream
 * @param totalChanges every entry across the four lists
 */
public record GitStatusResponse(
    String projectName,
    String branch,
    String upstreamBranch,
    Integer aheadCount,
    Integer behindCount,
    List<GitFileChange> staged,
    List<GitFileChange> unstaged,
    List<GitFileChange> untracked,
    List<GitFileChange> conflicting,
    int totalChanges,
    boolean clean,
    String summaryText
)
{
    /** How a file differs, in the list it appears in. */
    public enum ChangeType
    {
        ADDED,
        MODIFIED,
        DELETED,
        UNTRACKED,
        CONFLICTING
    }

    /**
     * One changed file.
     *
     * @param projectName the Eclipse project the file belongs to, or null when the file
     *            lies outside every project mapped into this repository
     * @param filePath path relative to that project, which is what the reading and
     *            editing tools take; null when projectName is
     * @param repoPath path relative to the repository root, which is what the Git tools
     *            that take a pathspec take
     */
    public record GitFileChange(
        String projectName,
        String filePath,
        String repoPath,
        ChangeType changeType
    )
    {
    }

    private static final Comparator<GitFileChange> BY_REPO_PATH =
            Comparator.comparing( GitFileChange::repoPath, Comparator.nullsLast( Comparator.naturalOrder() ) );

    /**
     * Resolves a repository-relative path against the projects mapped into that
     * repository.
     * <p>
     * Git reports paths from the repository root, but a repository commonly holds several
     * Eclipse projects, and none of the reading or editing tools can address a file that
     * way. The longest matching project prefix wins, so a project nested inside another
     * project's folder still claims its own files.
     *
     * @param projectsByPrefix repository-relative project folder to project name, where
     *            the empty string is a project sitting at the repository root
     */
    public static GitFileChange locate( String repoPath, ChangeType changeType, Map<String, String> projectsByPrefix )
    {
        String normalized = repoPath == null ? "" : repoPath.replace( '\\', '/' );

        String bestPrefix = null;
        String bestProject = null;
        if ( projectsByPrefix != null )
        {
            for ( Map.Entry<String, String> entry : projectsByPrefix.entrySet() )
            {
                String prefix = trimSlashes( entry.getKey() );
                boolean matches = prefix.isEmpty() || normalized.equals( prefix ) || normalized.startsWith( prefix + "/" );
                if ( matches && ( bestPrefix == null || prefix.length() > bestPrefix.length() ) )
                {
                    bestPrefix = prefix;
                    bestProject = entry.getValue();
                }
            }
        }

        if ( bestProject == null )
        {
            // Nothing in the workspace covers this file. Handing back a project-relative
            // path invented from the repository path would name a file no tool can open.
            return new GitFileChange( null, null, normalized, changeType );
        }

        String filePath;
        if ( bestPrefix.isEmpty() )
        {
            filePath = normalized;
        }
        else if ( normalized.length() > bestPrefix.length() )
        {
            filePath = normalized.substring( bestPrefix.length() + 1 );
        }
        else
        {
            filePath = "";
        }
        return new GitFileChange( bestProject, filePath, normalized, changeType );
    }

    private static String trimSlashes( String prefix )
    {
        if ( prefix == null )
        {
            return "";
        }
        String trimmed = prefix.replace( '\\', '/' );
        while ( trimmed.startsWith( "/" ) )
        {
            trimmed = trimmed.substring( 1 );
        }
        while ( trimmed.endsWith( "/" ) )
        {
            trimmed = trimmed.substring( 0, trimmed.length() - 1 );
        }
        return trimmed;
    }

    /**
     * Sorts JGit's status sets into the four lists a caller acts on.
     * <p>
     * Git's own categories are finer than that - added, changed and removed are all
     * staged - so the distinction is kept per entry in {@link GitFileChange#changeType()}
     * rather than lost in the grouping.
     */
    public static GitStatusResponse from( String projectName, String branch, String upstreamBranch, Integer aheadCount,
            Integer behindCount, Status status, Map<String, String> projectsByPrefix )
    {
        List<GitFileChange> staged = new ArrayList<>();
        collect( staged, status.getAdded(), ChangeType.ADDED, projectsByPrefix );
        collect( staged, status.getChanged(), ChangeType.MODIFIED, projectsByPrefix );
        collect( staged, status.getRemoved(), ChangeType.DELETED, projectsByPrefix );

        List<GitFileChange> unstaged = new ArrayList<>();
        collect( unstaged, status.getModified(), ChangeType.MODIFIED, projectsByPrefix );
        collect( unstaged, status.getMissing(), ChangeType.DELETED, projectsByPrefix );

        List<GitFileChange> untracked = new ArrayList<>();
        collect( untracked, status.getUntracked(), ChangeType.UNTRACKED, projectsByPrefix );

        List<GitFileChange> conflicting = new ArrayList<>();
        collect( conflicting, status.getConflicting(), ChangeType.CONFLICTING, projectsByPrefix );

        return of( projectName, branch, upstreamBranch, aheadCount, behindCount, staged, unstaged, untracked, conflicting );
    }

    public static GitStatusResponse of( String projectName, String branch, String upstreamBranch, Integer aheadCount,
            Integer behindCount, List<GitFileChange> staged, List<GitFileChange> unstaged, List<GitFileChange> untracked,
            List<GitFileChange> conflicting )
    {
        List<GitFileChange> sortedStaged = sorted( staged );
        List<GitFileChange> sortedUnstaged = sorted( unstaged );
        List<GitFileChange> sortedUntracked = sorted( untracked );
        List<GitFileChange> sortedConflicting = sorted( conflicting );

        int total = sortedStaged.size() + sortedUnstaged.size() + sortedUntracked.size() + sortedConflicting.size();

        return new GitStatusResponse( projectName, branch, upstreamBranch, aheadCount, behindCount, sortedStaged,
                sortedUnstaged, sortedUntracked, sortedConflicting, total, total == 0,
                summarize( branch, sortedStaged.size(), sortedUnstaged.size(), sortedUntracked.size(),
                        sortedConflicting.size() ) );
    }

    private static void collect( List<GitFileChange> target, Collection<String> repoPaths, ChangeType changeType,
            Map<String, String> projectsByPrefix )
    {
        if ( repoPaths == null )
        {
            return;
        }
        for ( String repoPath : repoPaths )
        {
            target.add( locate( repoPath, changeType, projectsByPrefix ) );
        }
    }

    private static List<GitFileChange> sorted( List<GitFileChange> changes )
    {
        if ( changes == null || changes.isEmpty() )
        {
            return List.of();
        }
        List<GitFileChange> copy = new ArrayList<>( changes );
        copy.sort( BY_REPO_PATH );
        return List.copyOf( copy );
    }

    private static String summarize( String branch, int staged, int unstaged, int untracked, int conflicting )
    {
        String on = "On branch " + branch + ": ";
        if ( staged + unstaged + untracked + conflicting == 0 )
        {
            return on + "working tree clean.";
        }
        List<String> parts = new ArrayList<>();
        if ( conflicting > 0 )
        {
            parts.add( conflicting + " conflicting" );
        }
        if ( staged > 0 )
        {
            parts.add( staged + " staged" );
        }
        if ( unstaged > 0 )
        {
            parts.add( unstaged + " unstaged" );
        }
        if ( untracked > 0 )
        {
            parts.add( untracked + " untracked" );
        }
        return on + String.join( ", ", parts ) + ".";
    }
}
