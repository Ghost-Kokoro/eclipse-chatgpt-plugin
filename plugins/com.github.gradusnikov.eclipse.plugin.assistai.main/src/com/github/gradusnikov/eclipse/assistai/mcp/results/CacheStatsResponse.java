package com.github.gradusnikov.eclipse.assistai.mcp.results;

/**
 * How full the conversation's resource cache is.
 * <p>
 * Previously this was the string {@code "Resources: 3/20, Tokens: ~4000/100000"},
 * which a caller had to split on slashes and commas to learn anything from. The
 * limits are reported alongside the counts because the useful question is not how
 * much is cached but how close the cache is to evicting the resource just read.
 */
public record CacheStatsResponse(
    int resourceCount,
    int maxResources,
    int totalEstimatedTokens,
    int maxTotalTokens,
    String summaryText
)
{
    public static CacheStatsResponse of( int resourceCount, int maxResources,
                                         int totalEstimatedTokens, int maxTotalTokens )
    {
        String summary = resourceCount == 0
                ? "No resources cached."
                : resourceCount + " of " + maxResources + " resources cached, ~"
                        + totalEstimatedTokens + " of " + maxTotalTokens + " tokens.";

        return new CacheStatsResponse( resourceCount, maxResources,
                totalEstimatedTokens, maxTotalTokens, summary );
    }

    /**
     * Whether the next resource read will evict something. Derived from the counts, so
     * a caller reading only the JSON can work it out for itself.
     */
    public boolean atCapacity()
    {
        return resourceCount >= maxResources || totalEstimatedTokens >= maxTotalTokens;
    }
}
