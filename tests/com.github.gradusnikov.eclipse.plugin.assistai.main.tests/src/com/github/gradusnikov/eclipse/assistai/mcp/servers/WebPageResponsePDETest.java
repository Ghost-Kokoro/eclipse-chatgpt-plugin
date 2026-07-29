package com.github.gradusnikov.eclipse.assistai.mcp.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.gradusnikov.eclipse.assistai.mcp.McpJson;
import com.github.gradusnikov.eclipse.assistai.mcp.McpOutputSchemas;
import com.github.gradusnikov.eclipse.assistai.mcp.annotations.Tool;
import com.github.gradusnikov.eclipse.assistai.mcp.results.WebPageResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.servers.ReadWebPageMcpServer;

/**
 * {@code readWebPage}, once it stopped returning only the markdown.
 * <p>
 * The conversion is exercised against fixed pages rather than the network, so these
 * run offline and deterministically.
 */
public class WebPageResponsePDETest
{
    private static final String PAGE = """
            <html>
              <head>
                <title>Release notes</title>
                <style>body { color: red }</style>
                <script>console.log('tracking')</script>
              </head>
              <body>
                <h1>Version 2</h1>
                <p>Fixes a bug.</p>
              </body>
            </html>
            """;

    // ---- the facts the markdown cannot carry -------------------------------

    @Test
    public void reportsTheStatusSoAnErrorPageIsNotMistakenForContent()
    {
        // A 404 body converts to perfectly readable prose. Without the status there is
        // nothing in the result to tell it apart from the page that was asked for.
        WebPageResponse notFound = ReadWebPageMcpServer.toMarkdown(
                "https://example.com/gone", "https://example.com/gone", 404, "text/html",
                "<html><head><title>Not Found</title></head><body><h1>Not Found</h1></body></html>" );

        assertEquals( 404, notFound.statusCode() );
        assertFalse( notFound.content().isBlank(), "the body is still returned; it is just not the page" );
    }

    @Test
    public void reportsWhereTheRequestEndedAfterRedirects()
    {
        WebPageResponse redirected = ReadWebPageMcpServer.toMarkdown(
                "http://example.com/old", "https://example.com/new", 200, "text/html", PAGE );

        assertEquals( "http://example.com/old", redirected.requestedUrl() );
        assertEquals( "https://example.com/new", redirected.finalUrl(),
                "relative links in the content resolve against this, not against the request" );
    }

    @Test
    public void carriesTheTitleAndTheContentType()
    {
        WebPageResponse page = ReadWebPageMcpServer.toMarkdown(
                "https://example.com/", "https://example.com/", 200, "text/html; charset=utf-8", PAGE );

        assertEquals( "Release notes", page.title() );
        assertEquals( "text/html; charset=utf-8", page.contentType() );
    }

    @Test
    public void aMissingTitleIsAbsentRatherThanEmpty()
    {
        WebPageResponse page = ReadWebPageMcpServer.toMarkdown(
                "https://example.com/", "https://example.com/", 200, "text/html",
                "<html><body><p>No title here.</p></body></html>" );

        assertNull( page.title(), "an empty string would read as a page titled with nothing" );
    }

    // ---- the conversion ----------------------------------------------------

    @Test
    public void convertsTheBodyAndLeavesTheHeadBehind()
    {
        WebPageResponse page = ReadWebPageMcpServer.toMarkdown(
                "https://example.com/", "https://example.com/", 200, "text/html", PAGE );

        assertTrue( page.content().contains( "Version 2" ) );
        assertTrue( page.content().contains( "Fixes a bug." ) );
        assertFalse( page.content().contains( "tracking" ), "scripts must not reach the markdown" );
        assertFalse( page.content().contains( "color: red" ), "styles must not reach the markdown" );
    }

    @Test
    public void aPayloadThatIsNotHtmlComesBackAsItArrived()
    {
        // A gateway answering with JSON has no body element. Reporting empty content
        // would be indistinguishable from a page that genuinely has none.
        String json = "{\"error\":\"upstream timeout\"}";
        WebPageResponse page = ReadWebPageMcpServer.toMarkdown(
                "https://api.example.com/", "https://api.example.com/", 502, "application/json", json );

        assertEquals( 502, page.statusCode() );
        assertTrue( page.content().contains( "upstream timeout" ) );
    }

    // ---- the contract with the client --------------------------------------

    @Test
    public void theToolAdvertisesTheTypeItReturns() throws Exception
    {
        Method readWebPage = ReadWebPageMcpServer.class.getMethod( "readWebPage", String.class );
        Tool tool = readWebPage.getAnnotation( Tool.class );

        assertNotEquals( Void.class, tool.outputType() );
        assertEquals( readWebPage.getReturnType(), tool.outputType() );
    }

    @Test
    public void serializesExactlyTheFieldsItAdvertises()
    {
        WebPageResponse page = ReadWebPageMcpServer.toMarkdown(
                "https://example.com/", "https://example.com/", 200, "text/html", PAGE );

        @SuppressWarnings( "unchecked" )
        Map<String, Object> advertised = (Map<String, Object>) McpOutputSchemas
                .forType( WebPageResponse.class ).get( "properties" );

        assertEquals( advertised.keySet(), McpJson.toMap( page ).keySet() );
    }
}
