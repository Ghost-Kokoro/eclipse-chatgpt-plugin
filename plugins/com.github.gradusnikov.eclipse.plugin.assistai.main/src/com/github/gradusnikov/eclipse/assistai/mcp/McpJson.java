package com.github.gradusnikov.eclipse.assistai.mcp;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Serializes tool payloads for the wire.
 * <p>
 * One mapper, one shape: the object a client reads from {@code structuredContent} and
 * the text a client reads from the content block are the same serialization, so they
 * cannot describe different outcomes. Hand-written renderings of the same data were
 * the thing structured output existed to remove.
 */
public final class McpJson
{
    /**
     * Unknown properties are tolerated on the way back in. A payload may legitimately
     * come from a slightly older or newer build of the same record, and refusing it
     * would turn a cosmetic difference into a failure to recognise the result at all.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable( SerializationFeature.INDENT_OUTPUT )
            .disable( DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES )
            // Serialize a record by its components only. Response records carry
            // derived accessors - isUnused(), isCacheable(), isEditable() - so that a
            // caller can express the question it actually has. Auto-detected as
            // getters, those become extra fields that the advertised outputSchema,
            // generated from the components, does not mention: the tool would promise
            // one shape and send another. Suppressing them centrally is more reliable
            // than @JsonIgnore, which needs the annotation package to be resolvable in
            // the OSGi bundle at runtime and is silently dropped when it is not.
            .setVisibility( PropertyAccessor.GETTER, Visibility.NONE )
            .setVisibility( PropertyAccessor.IS_GETTER, Visibility.NONE )
            .setVisibility( PropertyAccessor.FIELD, Visibility.ANY );

    private McpJson()
    {
    }

    /**
     * Reads a payload back out of the map form MCP delivers it in.
     * <p>
     * Needed by the in-IDE chat, which receives a {@code CallToolResult} whose
     * structuredContent has already been flattened to a map, and has to recover the
     * typed record to act on it.
     */
    public static <T> T convert( Object payload, Class<T> type )
    {
        return MAPPER.convertValue( payload, type );
    }

    /** The payload as the map MCP sends in {@code structuredContent}. */
    @SuppressWarnings( "unchecked" )
    public static Map<String, Object> toMap( Object payload )
    {
        return MAPPER.convertValue( payload, Map.class );
    }

    /**
     * The payload as indented JSON.
     * <p>
     * This is what goes in the text content block. The MCP specification asks a tool
     * returning structured content to also return its serialized JSON as text, so that
     * a client which only reads text still receives the same data - not a prose
     * approximation of it.
     */
    public static String toJson( Object payload )
    {
        try
        {
            return MAPPER.writeValueAsString( payload );
        }
        catch ( JsonProcessingException e )
        {
            // A payload that cannot be serialized is a programming error, but a tool
            // result is not worth losing over it.
            return String.valueOf( payload );
        }
    }
}
