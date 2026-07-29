package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * What a web search found.
 * <p>
 * A search result is never the answer on its own: the caller reads the list to pick one
 * hit and then fetches its page. That is why {@code url} is a field of every result
 * rather than something to be recovered from a rendered listing - it is the argument
 * {@code webpage-reader} takes next.
 * <p>
 * A search that matched nothing is {@code totalResults} of {@code 0} and an empty
 * {@link #results()} list, never a sentence saying so. "No results" and "the search
 * failed" must not have to be told apart by reading prose.
 *
 * @param query the query as it was searched for, so a result set can be identified
 *            after it has been passed around
 * @param totalResults how many results the engine's page carried; the same as
 *            {@code results().size()}, because nothing here truncates
 * @param results the hits, in the order the engine ranked them
 * @param summaryText a one-line human summary; the machine-readable answer is
 *            {@code totalResults}
 */
public record WebSearchResponse(
    String query,
    int totalResults,
    List<Result> results,
    String summaryText
)
{
    /**
     * One hit.
     *
     * @param title the link text the engine showed
     * @param url an absolute {@code http}/{@code https} URL, ready to be fetched;
     *            DuckDuckGo emits protocol-relative links, which are made absolute here
     *            rather than by every caller
     * @param snippet the engine's extract of the page, with whitespace normalized
     */
    public record Result(
        String title,
        String url,
        String snippet
    )
    {
    }

    /** Whether the search found nothing - the question, asked of a field rather than of text. */
    public boolean isEmpty()
    {
        return totalResults == 0;
    }

    public static WebSearchResponse of( String query, List<Result> results )
    {
        List<Result> hits = List.copyOf( results );
        return new WebSearchResponse( query, hits.size(), hits, summarize( query, hits.size() ) );
    }

    private static String summarize( String query, int totalResults )
    {
        if ( totalResults == 0 )
        {
            return "No results for \"" + query + "\".";
        }
        return totalResults + ( totalResults == 1 ? " result for \"" : " results for \"" ) + query + "\".";
    }
}
