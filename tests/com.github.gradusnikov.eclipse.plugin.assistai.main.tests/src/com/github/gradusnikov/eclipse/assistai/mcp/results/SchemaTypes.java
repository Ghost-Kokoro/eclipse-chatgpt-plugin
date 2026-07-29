package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.Collection;
import java.util.Map;

/**
 * The JSON type a schema field carries, with the null alternative set aside.
 * <p>
 * Most fields are advertised as {@code ["string","null"]} rather than {@code "string"},
 * because a Java reference can be absent and a strict client discards a whole payload
 * that contradicts the schema it was given. A test asking only <em>which</em> type a
 * field carries should not have to spell that out, and should not break the next time
 * the nullability rule is retuned - as it was when arrays went back to a bare type.
 * <p>
 * Where the null itself is the point - a modification stamp that is absent rather than
 * a sentinel, a Javadoc body that is missing rather than empty - the test asserts the
 * full declared type instead of calling this.
 */
final class SchemaTypes
{
    private SchemaTypes()
    {
    }

    /**
     * @param fieldSchema the schema of a single field
     * @return its type, ignoring the {@code "null"} alternative
     */
    static String carriedBy( Map<String, Object> fieldSchema )
    {
        Object declared = fieldSchema.get( "type" );

        if ( declared instanceof Collection<?> alternatives )
        {
            return alternatives.stream()
                    .filter( alternative -> !"null".equals( alternative ) )
                    .map( String::valueOf )
                    .findFirst()
                    .orElse( "null" );
        }
        return (String) declared;
    }
}
