package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.FileHeader.PatchType;

import com.github.gradusnikov.eclipse.assistai.mcp.results.GitStatusResponse.ChangeType;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitStatusResponse.GitFileChange;

/**
 * The difference between two versions of a whole repository.
 * <p>
 * Deliberately not {@link DiffResponse}, which describes two versions of <em>one</em>
 * file and addresses it as a project plus a project-relative path. A repository diff is
 * multi-file by nature and Git names every path from the repository root, which is not
 * a form any reading or editing tool accepts - so the per-file list resolves each path
 * to the project that owns it as well, exactly as {@code gitStatus} does.
 * <p>
 * The diff body stays a string for the reason {@code DiffResponse} gives: a unified
 * diff is already a machine format with a parser everywhere. What is lifted out is what
 * a caller would otherwise have to parse it to learn. Three outcomes used to share the
 * one String slot - hunks, an empty string, and the sentence "No commits yet." - so an
 * unchanged tree, a failure and a fresh repository were told apart by reading prose.
 * {@link #identical()} answers the first, and a repository with no commits is a null
 * {@link #baseRevision()} whose staged diff is honestly reported against the empty
 * tree, which is what {@code git diff --cached} does there.
 * <p>
 * Line counts come from the {@link EditList} JGit built to render the diff, so they
 * agree with the body by construction; counting {@code +}/{@code -} prefixes in the
 * rendered text would miscount the {@code ---}/{@code +++} headers and every context
 * line that happens to start with a minus.
 *
 * @param staged whether the index was compared against HEAD rather than the working
 *            tree against the index
 * @param fromLabel the older side of the comparison, named
 * @param toLabel the newer side
 * @param baseRevision the sha HEAD resolved to, or null in a repository with no commits
 * @param identical whether the two sides are the same; a field, because "no
 *            differences" and "the diff could not be produced" both render as no hunks
 * @param unifiedDiff the hunks, with the repository-relative paths Git writes
 */
public record GitDiffResponse(
    String projectName,
    boolean staged,
    String fromLabel,
    String toLabel,
    String baseRevision,
    boolean identical,
    int totalFiles,
    int addedLines,
    int removedLines,
    List<GitFileDiff> files,
    String unifiedDiff,
    String summaryText
)
{
    /**
     * How a file changed between the two sides.
     * <p>
     * Rename and copy are their own kinds rather than a delete plus an add, because
     * rename detection is on and collapsing them would tell a caller a file it still
     * has was removed.
     */
    public enum FileChangeType
    {
        ADDED,
        MODIFIED,
        DELETED,
        RENAMED,
        COPIED
    }

    /**
     * One file in the diff.
     *
     * @param projectName the Eclipse project the file belongs to, or null when no
     *            project mapped into this repository covers it
     * @param filePath path relative to that project - what the reading and editing
     *            tools take - or null when projectName is
     * @param repoPath path relative to the repository root, which is what the diff body
     *            and the Git pathspecs use
     * @param oldRepoPath where the file came from, for a rename or a copy; null
     *            otherwise
     * @param binary whether the content is binary, in which case the line counts are
     *            zero because there are no lines to count
     */
    public record GitFileDiff(
        String projectName,
        String filePath,
        String repoPath,
        String oldRepoPath,
        FileChangeType changeType,
        int addedLines,
        int removedLines,
        boolean binary
    )
    {
    }

    /**
     * Describes one file of a diff JGit has already computed.
     * <p>
     * The project and project-relative path come from
     * {@link GitStatusResponse#locate(String, ChangeType, Map)}, so a repository path
     * resolves to a project here by the same longest-prefix rule as in a status - one
     * repository routinely holds several projects, and a file none of them covers gets
     * nulls rather than an invented path. Only the location is taken from it; the kind
     * of change is this record's own, because a diff distinguishes renames and copies
     * and a status does not.
     */
    public static GitFileDiff file( FileHeader header, Map<String, String> projectsByPrefix )
    {
        FileChangeType changeType = switch ( header.getChangeType() )
        {
            case ADD -> FileChangeType.ADDED;
            case DELETE -> FileChangeType.DELETED;
            case RENAME -> FileChangeType.RENAMED;
            case COPY -> FileChangeType.COPIED;
            default -> FileChangeType.MODIFIED;
        };

        String repoPath = changeType == FileChangeType.DELETED ? header.getOldPath() : header.getNewPath();
        String oldRepoPath = changeType == FileChangeType.RENAMED || changeType == FileChangeType.COPIED
                ? header.getOldPath()
                : null;

        boolean binary = header.getPatchType() != PatchType.UNIFIED;
        int added = 0;
        int removed = 0;
        if ( !binary )
        {
            for ( Edit edit : header.toEditList() )
            {
                added += edit.getEndB() - edit.getBeginB();
                removed += edit.getEndA() - edit.getBeginA();
            }
        }

        GitFileChange located = GitStatusResponse.locate( repoPath, ChangeType.MODIFIED, projectsByPrefix );
        return new GitFileDiff( located.projectName(), located.filePath(), located.repoPath(), oldRepoPath,
                changeType, added, removed, binary );
    }

    public static GitDiffResponse of( String projectName, boolean staged, String fromLabel, String toLabel,
            String baseRevision, List<GitFileDiff> files, String unifiedDiff )
    {
        List<GitFileDiff> entries = files == null ? List.of() : List.copyOf( files );

        int added = 0;
        int removed = 0;
        for ( GitFileDiff file : entries )
        {
            added += file.addedLines();
            removed += file.removedLines();
        }

        String body = unifiedDiff == null ? "" : unifiedDiff;
        return new GitDiffResponse( projectName, staged, fromLabel, toLabel, baseRevision, entries.isEmpty(),
                entries.size(), added, removed, entries, body, summarize( entries.size(), added, removed ) );
    }

    private static String summarize( int files, int added, int removed )
    {
        if ( files == 0 )
        {
            return "No differences.";
        }
        List<String> parts = new ArrayList<>();
        parts.add( files + ( files == 1 ? " file changed" : " files changed" ) );
        parts.add( added + ( added == 1 ? " insertion" : " insertions" ) );
        parts.add( removed + ( removed == 1 ? " deletion" : " deletions" ) );
        return String.join( ", ", parts ) + ".";
    }
}
