package com.github.gradusnikov.eclipse.assistai.mcp.results;

import com.github.gradusnikov.eclipse.assistai.mcp.results.GitStashListResponse.GitStash;

/**
 * The stash entry a {@code gitStash} created.
 * <p>
 * Two things were prose here. The stash commit - the only durable handle on the work
 * that was just taken off the working tree - was returned inside the sentence
 * "Stashed working directory: &lt;sha&gt;", and having nothing to stash was returned as
 * the sentence "No local changes to stash." in place of a result. The sibling tool
 * {@code gitStashList} already reports an empty stash as {@code totalStashes: 0}, so
 * the two tools in the same server disagreed about whether a stash is data.
 * <p>
 * {@link GitStash} is reused, so the entry this tool creates is the same shape as the
 * entries {@code gitStashList} enumerates.
 *
 * @param stashed whether anything was stashed; false means the working tree was already
 *            clean, which is an outcome and not a failure
 * @param stash the new entry - always {@code stash@{0}}, since a push goes on top -
 *            or null when nothing was stashed
 * @param totalStashes how many entries the stash holds afterwards, so a caller can see
 *            what a later pop would find
 */
public record GitStashResponse(
    String projectName,
    boolean stashed,
    GitStash stash,
    int totalStashes,
    String summaryText
)
{
    public static GitStashResponse stashed( String projectName, GitStash stash, int totalStashes )
    {
        return new GitStashResponse( projectName, true, stash, totalStashes,
                "Stashed the working tree as " + ( stash == null ? "a new entry" : stash.ref() ) + "." );
    }

    public static GitStashResponse nothingToStash( String projectName, int totalStashes )
    {
        return new GitStashResponse( projectName, false, null, totalStashes,
                "No local changes to stash." );
    }
}
