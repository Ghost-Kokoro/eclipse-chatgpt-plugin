package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * A stretch of commit history.
 * <p>
 * The tool used to emit the {@code git log} layout - "commit &lt;sha&gt;", "Author: name
 * &lt;mail&gt;", an indented message - so a caller wanting the sha of the third commit
 * had to parse it back out of a rendering. Each commit now carries its identifiers,
 * timestamp and message as fields.
 *
 * @param commitCount how many commits this response carries
 * @param truncated whether the history goes further back than the requested maximum
 */
public record GitLogResponse(
    String projectName,
    String branch,
    int commitCount,
    List<GitCommit> commits,
    boolean truncated,
    String summaryText
)
{
    /**
     * One commit.
     *
     * @param shortSha the abbreviated sha, which is what a human-facing reference uses;
     *            {@link #sha()} is the one to pass back to a tool
     * @param authorTimeMillis epoch milliseconds, so a client can format or compare it
     *            in its own time zone instead of parsing a formatted date back
     * @param message the full commit message
     * @param shortMessage its first line, the subject
     */
    public record GitCommit(
        String sha,
        String shortSha,
        String author,
        String authorEmail,
        long authorTimeMillis,
        String message,
        String shortMessage
    )
    {
    }

    public static GitLogResponse from( String projectName, String branch, List<RevCommit> commits, boolean truncated )
    {
        List<GitCommit> converted = new ArrayList<>();
        if ( commits != null )
        {
            for ( RevCommit commit : commits )
            {
                converted.add( toCommit( commit ) );
            }
        }
        return of( projectName, branch, converted, truncated );
    }

    public static GitLogResponse of( String projectName, String branch, List<GitCommit> commits, boolean truncated )
    {
        List<GitCommit> entries = commits == null ? List.of() : List.copyOf( commits );
        return new GitLogResponse( projectName, branch, entries.size(), entries, truncated,
                summarize( branch, entries.size(), truncated ) );
    }

    /**
     * One commit, as this response describes them.
     * <p>
     * Public so that {@code gitCommit} can report the commit it just created in exactly
     * the shape {@code gitLog} reports the commits it lists - the alternative was a
     * second conversion that would drift.
     */
    public static GitCommit toCommit( RevCommit commit )
    {
        String sha = commit.getName();
        PersonIdent author = commit.getAuthorIdent();
        String message = commit.getFullMessage() == null ? "" : commit.getFullMessage().strip();

        return new GitCommit(
                sha,
                sha.substring( 0, Math.min( 7, sha.length() ) ),
                author == null ? null : author.getName(),
                author == null ? null : author.getEmailAddress(),
                author == null ? commit.getCommitTime() * 1000L : author.getWhenAsInstant().toEpochMilli(),
                message,
                commit.getShortMessage() );
    }

    private static String summarize( String branch, int count, boolean truncated )
    {
        if ( count == 0 )
        {
            return "No commits on " + branch + ".";
        }
        return count + ( count == 1 ? " commit on " : " commits on " ) + branch
                + ( truncated ? ", and the history goes further back." : "." );
    }
}
