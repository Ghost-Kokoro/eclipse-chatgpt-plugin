package com.github.gradusnikov.eclipse.assistai.mcp.servers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.eclipse.core.runtime.ILog;
import org.eclipse.e4.core.di.annotations.Creatable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.github.gradusnikov.eclipse.assistai.mcp.annotations.McpServer;
import com.github.gradusnikov.eclipse.assistai.mcp.annotations.Tool;
import com.github.gradusnikov.eclipse.assistai.mcp.annotations.ToolParam;
import com.github.gradusnikov.eclipse.assistai.mcp.results.WebPageResponse;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;

import jakarta.inject.Inject;

@Creatable
@McpServer(name = "webpage-reader")
public class ReadWebPageMcpServer
{
    @Inject
    private ILog logger;

    @Tool(name = "readWebPage",
          description = "Reads the content of the given web page and returns it as markdown, "
                      + "together with the HTTP status, the URL the request ended at after "
                      + "redirects, the content type and the page title. Check statusCode: an "
                      + "error page converts to plausible-looking prose just as a real one does.",
          type = "object", outputType = WebPageResponse.class)
    public WebPageResponse readWebPage(
            @ToolParam(name = "url", description = "A web site URL", required = true) String url )
    {
        try
        {
            logger.info( "Fetching web page: " + url );

            HttpClient client = HttpClient.newBuilder()
                    .followRedirects( HttpClient.Redirect.NORMAL )
                    .connectTimeout( Duration.ofSeconds( 15 ) )
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri( URI.create( url ) )
                    .timeout( Duration.ofSeconds( 30 ) )
                    .header( "User-Agent", "Mozilla/5.0 (compatible; AssistAI/1.0)" )
                    .GET()
                    .build();

            HttpResponse<String> response = client.send( request, HttpResponse.BodyHandlers.ofString() );

            WebPageResponse page = toMarkdown( url, response.uri().toString(), response.statusCode(),
                    response.headers().firstValue( "content-type" ).orElse( null ), response.body() );

            // The content is the return value; logging it as well duplicated whole pages
            // into the error log.
            logger.info( "Fetched " + page.finalUrl() + " (" + page.statusCode() + ", "
                    + page.content().length() + " characters)" );

            return page;
        }
        catch ( InterruptedException e )
        {
            Thread.currentThread().interrupt();
            throw new RuntimeException( "Interrupted while fetching " + url, e );
        }
        catch ( Exception e )
        {
            throw new RuntimeException( "Could not fetch " + url + ": " + e.getMessage(), e );
        }
    }

    /**
     * Turns a fetched response into the tool's result.
     * <p>
     * Separate from the request so it can be exercised against fixed pages: everything
     * that can be wrong about this tool - a title that is missing, a body that is not
     * there, a payload that was never HTML - is in here rather than in the HTTP call.
     */
    static WebPageResponse toMarkdown( String requestedUrl, String finalUrl, int statusCode,
                                       String contentType, String body )
    {
        Document document = Jsoup.parse( body == null ? "" : body );

        // The body element is converted rather than the whole document, so scripts and
        // styles in the head stay out of the markdown.
        StringBuilder content = new StringBuilder();
        var converter = FlexmarkHtmlConverter.builder().build();
        for ( Element element : document.getElementsByTag( "body" ) )
        {
            content.append( converter.convert( element.toString() ) );
        }

        // A payload that is not HTML - JSON, plain text, a stack trace from a gateway -
        // has nothing Jsoup recognises as a body. Returning what actually arrived beats
        // reporting the page as empty, which is indistinguishable from a page that is.
        String markdown = content.isEmpty() ? ( body == null ? "" : body ) : content.toString();

        String title = document.title();
        return new WebPageResponse(
                requestedUrl,
                finalUrl,
                statusCode,
                contentType,
                title == null || title.isBlank() ? null : title,
                markdown );
    }
}
