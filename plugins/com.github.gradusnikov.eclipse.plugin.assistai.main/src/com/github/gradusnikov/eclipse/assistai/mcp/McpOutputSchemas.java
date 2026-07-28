package com.github.gradusnikov.eclipse.assistai.mcp;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds a JSON Schema for what a tool returns, by reflecting over the Java type.
 * <p>
 * A tool that advertises an {@code outputSchema} tells a client what fields to expect
 * in {@code structuredContent}, so the client can branch on them without parsing prose
 * - which is the whole reason structured results exist. Writing those schemas by hand
 * beside the records they describe would guarantee they drift apart.
 * <p>
 * Only the shapes this plugin actually returns are handled: records, enums, the
 * primitive wrappers, strings, collections and maps. Anything else degrades to an
 * unconstrained object rather than failing, because an imperfect schema is still
 * better than a tool that will not start.
 */
public final class McpOutputSchemas
{
    /** Guards against a record that reaches itself through a field. */
    private static final int MAX_DEPTH = 8;

    private McpOutputSchemas()
    {
    }

    /**
     * The schema for a tool's payload type.
     *
     * @return an object schema, or null when {@code type} declares no structure worth
     *         advertising ({@code void}, {@code Object} or a bare String)
     */
    public static Map<String, Object> forType( Class<?> type )
    {
        if ( type == null || type == void.class || type == Void.class || type == Object.class || type == String.class )
        {
            return null;
        }

        Map<String, Object> schema = describe( type, new ArrayDeque<>(), 0 );

        // MCP structuredContent is a JSON object, so a payload that is not one has to
        // be wrapped rather than advertised as a bare array or scalar.
        if ( !"object".equals( schema.get( "type" ) ) )
        {
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put( "type", "object" );
            wrapper.put( "properties", Map.of( "value", schema ) );
            return wrapper;
        }
        return schema;
    }

    private static Map<String, Object> describe( Type type, Deque<Class<?>> inProgress, int depth )
    {
        if ( depth > MAX_DEPTH )
        {
            return objectSchema();
        }

        if ( type instanceof ParameterizedType parameterized )
        {
            Class<?> raw = (Class<?>) parameterized.getRawType();
            Type[] arguments = parameterized.getActualTypeArguments();

            if ( Collection.class.isAssignableFrom( raw ) && arguments.length == 1 )
            {
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put( "type", "array" );
                schema.put( "items", describe( arguments[0], inProgress, depth + 1 ) );
                return schema;
            }
            if ( Map.class.isAssignableFrom( raw ) )
            {
                return objectSchema();
            }
            if ( Optional.class.isAssignableFrom( raw ) && arguments.length == 1 )
            {
                return describe( arguments[0], inProgress, depth + 1 );
            }
            return describe( raw, inProgress, depth );
        }

        if ( !( type instanceof Class<?> raw ) )
        {
            return objectSchema();
        }

        if ( raw == String.class || CharSequence.class.isAssignableFrom( raw ) )
        {
            return typeSchema( "string" );
        }
        if ( raw == boolean.class || raw == Boolean.class )
        {
            return typeSchema( "boolean" );
        }
        if ( raw == byte.class || raw == Byte.class || raw == short.class || raw == Short.class
                || raw == int.class || raw == Integer.class || raw == long.class || raw == Long.class )
        {
            return typeSchema( "integer" );
        }
        if ( raw == float.class || raw == Float.class || raw == double.class || raw == Double.class )
        {
            return typeSchema( "number" );
        }
        if ( raw.isEnum() )
        {
            Map<String, Object> schema = typeSchema( "string" );
            List<String> constants = new ArrayList<>();
            for ( Object constant : raw.getEnumConstants() )
            {
                constants.add( ( (Enum<?>) constant ).name() );
            }
            schema.put( "enum", constants );
            return schema;
        }
        if ( raw.isArray() )
        {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put( "type", "array" );
            schema.put( "items", describe( raw.getComponentType(), inProgress, depth + 1 ) );
            return schema;
        }
        if ( Collection.class.isAssignableFrom( raw ) )
        {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put( "type", "array" );
            schema.put( "items", objectSchema() );
            return schema;
        }
        if ( raw.isRecord() )
        {
            if ( inProgress.contains( raw ) )
            {
                // Self-referencing record: stop here rather than recurse forever.
                return objectSchema();
            }
            inProgress.push( raw );
            try
            {
                Map<String, Object> properties = new LinkedHashMap<>();
                for ( RecordComponent component : raw.getRecordComponents() )
                {
                    properties.put( component.getName(),
                            describe( component.getGenericType(), inProgress, depth + 1 ) );
                }
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put( "type", "object" );
                schema.put( "properties", properties );
                return schema;
            }
            finally
            {
                inProgress.pop();
            }
        }

        return objectSchema();
    }

    /**
     * An object of unspecified shape. Deliberately without {@code "properties"}: saying
     * nothing is honest, whereas an empty property set would claim the object is empty.
     */
    private static Map<String, Object> objectSchema()
    {
        return typeSchema( "object" );
    }

    private static Map<String, Object> typeSchema( String jsonType )
    {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put( "type", jsonType );
        return schema;
    }
}
