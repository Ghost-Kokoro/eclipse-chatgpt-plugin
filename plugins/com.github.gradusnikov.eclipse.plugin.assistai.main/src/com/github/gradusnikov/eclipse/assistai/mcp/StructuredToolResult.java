package com.github.gradusnikov.eclipse.assistai.mcp;

import java.util.Objects;

/**
 * A tool result that carries both the data a client should branch on and the prose a
 * person should read.
 * <p>
 * MCP sends both on the same response: {@code structuredContent} for the caller's
 * logic, a text block for callers that only render text. Returning them together keeps
 * the two derived from one computation, so they cannot describe different outcomes.
 *
 * @param data the payload, serialized into {@code structuredContent}. Its type should
 *            match the {@code outputType} declared on the tool, which is what the
 *            advertised {@code outputSchema} is generated from
 * @param text the human-readable rendering, produced by the domain's formatter - never
 *            by {@code data.toString()}
 */
public record StructuredToolResult( Object data, String text )
{
    public StructuredToolResult
    {
        Objects.requireNonNull( data, "data" );
        Objects.requireNonNull( text, "text" );
    }

    public static StructuredToolResult of( Object data, String text )
    {
        return new StructuredToolResult( data, text );
    }
}
