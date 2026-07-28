package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.revwalk.RevCommit;

/**
 * The stash entries of the repository a project is mapped into.
 * <p>
 * The listing used to be {@code stash@{0}: message} lines, or the sentence
 * "No stashes found." in place of a result - so an empty stash and a failed call read
 * alike. An empty stash is now {@code totalStashes: 0} with an empty list.
 */
public record GitStashListResponse(
    String projectName,
    int totalStashes,
    List<GitStash> stashes,
    String summaryText
)
{
    /**
     * One stash entry.
     *
     * @param index 0 is the most recent stash, the one a pop applies
     * @param ref the {@code stash@{n}} name that Git commands take
     * @param sha the commit the entry is stored as, which survives later stashes shifting
     *            every index
     * @param message the entry's first line
     */
    public record GitStash(
        int index,
        String ref,
        String sha,
        String message
    )
    {
    }

    public static GitStashListResponse from( String projectName, Collection<RevCommit> stashes )
    {
        List<GitStash> entries = new ArrayList<>();
        if ( stashes != null )
        {
            int index = 0;
            for ( RevCommit stash : stashes )
            {
                entries.add( new GitStash( index, "stash@{" + index + "}", stash.getName(), stash.getShortMessage() ) );
                index++;
            }
        }
        return of( projectName, entries );
    }

    public static GitStashListResponse of( String projectName, List<GitStash> stashes )
    {
        List<GitStash> entries = stashes == null ? List.of() : List.copyOf( stashes );
        String summary = entries.isEmpty()
                ? "No stash entries."
                : entries.size() + ( entries.size() == 1 ? " stash entry." : " stash entries." );
        return new GitStashListResponse( projectName, entries.size(), entries, summary );
    }
}
