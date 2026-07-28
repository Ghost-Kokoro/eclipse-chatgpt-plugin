package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * The rendered documentation of a Java type.
 * <p>
 * The body stays a {@code String}: it is Markdown, one piece of text with nothing
 * smuggled alongside it - the same call {@code DiffResponse} makes for a unified diff.
 * Only the miss needed fixing. {@code "JavaDoc is not available for X"} occupied the
 * answer slot, so a type with no documentation, a misspelled type name and a type
 * whose real documentation happens to contain that sentence were three
 * indistinguishable results, and each needs a different next move: read the source,
 * fix the name, or use what came back.
 *
 * @param projectName the project the type resolved in, or null when none did. Several
 *            projects can resolve the same name; this is the one that answered
 * @param markdown the documentation with each member's declaration, converted from the
 *            HTML JDT produces. Empty only when the type resolved nowhere
 */
public record JavaDocResponse(
    Status status,
    String typeName,
    String projectName,
    String markdown,
    List<Diagnostic> diagnostics
)
{
    public enum Status
    {
        /** The type was found and something in it is documented. */
        OK,
        /**
         * The type was found and nothing in it carries a Javadoc comment. An ordinary
         * state, not a fault: the answer is to read the source instead.
         */
        NO_JAVADOC,
        /** No open Java project resolves the name - see diagnostics. */
        TYPE_NOT_FOUND
    }

    public static JavaDocResponse of( Status status, String typeName, String projectName, String markdown )
    {
        return new JavaDocResponse( status, typeName, projectName, markdown, Diagnostic.none() );
    }

    public static JavaDocResponse notFound( String typeName, Diagnostic diagnostic )
    {
        return new JavaDocResponse( Status.TYPE_NOT_FOUND, typeName, null, "", List.of( diagnostic ) );
    }
}
