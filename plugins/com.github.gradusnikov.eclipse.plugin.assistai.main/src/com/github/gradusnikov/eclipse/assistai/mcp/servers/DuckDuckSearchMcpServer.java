package com.github.gradusnikov.eclipse.assistai.mcp.servers;

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.ILog;
import org.eclipse.e4.core.di.annotations.Creatable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.github.gradusnikov.eclipse.assistai.mcp.annotations.McpServer;
import com.github.gradusnikov.eclipse.assistai.mcp.annotations.Tool;
import com.github.gradusnikov.eclipse.assistai.mcp.annotations.ToolParam;
import com.github.gradusnikov.eclipse.assistai.mcp.results.WebSearchResponse;

import jakarta.inject.Inject;

@Creatable
@McpServer(name="duck-duck-search")
public class DuckDuckSearchMcpServer
{
    @Inject
    ILog logger;

    /** Jsoup treats 0 as an infinite timeout, so this must be a positive value. */
    private static final int SEARCH_TIMEOUT_MILLIS = 15_000;

    @SuppressWarnings("deprecation")
    @Tool(name="webSearch",
          description="Searches the web with DuckDuckGo. Returns totalResults and, for each hit, its title, "
                  + "absolute url and snippet, ranked as the engine ranked them. totalResults of 0 means the "
                  + "search matched nothing. The url of a hit is what webpage-reader takes to fetch the page.",
          type="object", outputType = WebSearchResponse.class, longExecution=true, inlineWait=20)
    public WebSearchResponse webSearch(
            @ToolParam(name="query", description="A search query", required=true) String query)
    {
        String url = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode( query, StandardCharsets.UTF_8 );

        logger.info( "Performing web search: " + url );

        try
        {
            // Jsoup reads a timeout of 0 as "wait forever": a black-holed connection used to
            // hang this tool, and the MCP thread behind it, with no way out.
            WebSearchResponse response = extractResults( query, Jsoup.parse( new URL( url ), SEARCH_TIMEOUT_MILLIS ) );
            logger.info( "Search results for query \"" + query + "\": " + response.summaryText() );
            return response;
        }
        catch ( IOException e )
        {
            throw new RuntimeException( e );
        }
    }

    /**
     * Reads the hits out of a DuckDuckGo HTML result page.
     * <p>
     * Separate from {@link #webSearch(String)} so that the extraction can be exercised
     * against a saved page. The tool itself needs the network, and a test that needed it
     * too would be asserting on whatever DuckDuckGo happened to rank that day.
     *
     * @param query the query the page is a response to; carried into the result
     * @param html the result page's markup
     */
    public static WebSearchResponse parseSearchResults( String query, String html )
    {
        return extractResults( query, Jsoup.parse( html ) );
    }

    private static WebSearchResponse extractResults( String query, Document document )
    {
        List<WebSearchResponse.Result> results = new ArrayList<>();

        for ( Element entry : document.select( ".results_links" ) )
        {
            Element titleElement = entry.select( ".result__title" ).select( "a" ).first();
            Element snippetElement = entry.select( ".result__snippet" ).first();
            if ( titleElement == null || snippetElement == null )
            {
                // An advertisement or a "no results" placeholder carries one but not the
                // other; a hit missing its link would be unusable anyway.
                continue;
            }

            String resultUrl = titleElement.attr( "href" );
            if ( resultUrl.startsWith( "//" ) )
            {
                // DuckDuckGo emits protocol-relative links. The caller's next act is to
                // fetch this, so it is made absolute here rather than by every caller.
                resultUrl = "https:" + resultUrl;
            }

            results.add( new WebSearchResponse.Result( titleElement.text(), resultUrl, snippetElement.text() ) );
        }

        return WebSearchResponse.of( query, results );
    }
}
