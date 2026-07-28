package com.github.gradusnikov.eclipse.assistai.mcp.results;

import com.github.gradusnikov.eclipse.assistai.mcp.results.GitLogResponse.GitCommit;

/**
 * The commit a {@code gitCommit} produced.
 * <p>
 * The tool used to answer {@code "Committed: " + sha + " " + shortMessage}. The sha is
 * the handle for everything a caller does next - tag it, reset to it, cite it, read a
 * file at it - and it was glued to free text by a single space, while commit subjects
 * contain spaces themselves. Splitting on the first space recovers the sha and mangles
 * the message; splitting on the last recovers neither.
 * <p>
 * {@link GitCommit} is reused rather than redeclared, so the commit a caller gets back
 * from committing has exactly the shape of the commits it gets back from
 * {@code gitLog} - same field names, same {@code authorTimeMillis}, same abbreviated
 * sha - and code that renders one renders the other.
 *
 * @param branch the branch the commit landed on
 * @param commit the new commit, whose {@code sha} is what every later tool takes
 */
public record GitCommitResponse(
    String projectName,
    String branch,
    GitCommit commit,
    String summaryText
)
{
    public static GitCommitResponse of( String projectName, String branch, GitCommit commit )
    {
        return new GitCommitResponse( projectName, branch, commit, summarize( branch, commit ) );
    }

    private static String summarize( String branch, GitCommit commit )
    {
        if ( commit == null )
        {
            return "Nothing was committed.";
        }
        return "Committed " + commit.shortSha() + " on " + branch + ".";
    }
}
