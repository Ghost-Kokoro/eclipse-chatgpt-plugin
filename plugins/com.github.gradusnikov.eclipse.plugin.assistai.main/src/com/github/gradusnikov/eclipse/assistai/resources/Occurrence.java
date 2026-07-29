package com.github.gradusnikov.eclipse.assistai.resources;

/**
 * Which match a find-and-replace should act on when there is more than one.
 * <p>
 * {@link #UNIQUE} is the default because the alternative - silently editing every
 * match - is a change the caller did not ask for and cannot see in the result. A
 * caller that genuinely means all of them says so.
 */
public enum Occurrence
{
    /** Exactly one match must exist, otherwise the edit is rejected. */
    UNIQUE,

    /** Take the first match in the search range. */
    FIRST,

    /** Take the last match in the search range. */
    LAST,

    /** Replace every match. */
    ALL,

    /** Take the match at {@code occurrenceIndex}, counting from 1. */
    INDEX;

    /** Parses a tool parameter, defaulting to {@link #UNIQUE} when absent. */
    public static Occurrence parse( String value )
    {
        if ( value == null || value.isBlank() )
        {
            return UNIQUE;
        }
        try
        {
            return valueOf( value.trim().toUpperCase() );
        }
        catch ( IllegalArgumentException e )
        {
            throw new IllegalArgumentException( "Unknown occurrence '" + value + "'. Expected one of: "
                    + String.join( ", ", "UNIQUE", "FIRST", "LAST", "ALL", "INDEX" ) );
        }
    }
}
