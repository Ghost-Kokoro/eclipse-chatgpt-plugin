package com.github.gradusnikov.eclipse.assistai.mcp.results;

/**
 * A fetched web page.
 * <p>
 * The four fields around the content are the ones a caller cannot recover from the
 * markdown itself, and each answers a question the previous string return left open:
 * a 404 page converts to perfectly plausible prose, so {@code statusCode} says whether
 * this is the page that was asked for; a redirect chain ends somewhere, so
 * {@code finalUrl} says where; and a server that answered with JSON or a PDF is not
 * something to read as an article, so {@code contentType} says what arrived.
 * <p>
 * The content stays markdown rather than becoming a tree of blocks. It is meant to be
 * read, and every consumer of this tool wants it whole.
 *
 * @param finalUrl where the request ended after redirects - the URL to cite, and the
 *            one to use as the base of any relative link in the content
 * @param title the document title, or null when the page has none
 */
public record WebPageResponse(
    String requestedUrl,
    String finalUrl,
    int statusCode,
    String contentType,
    String title,
    String content
)
{
}
