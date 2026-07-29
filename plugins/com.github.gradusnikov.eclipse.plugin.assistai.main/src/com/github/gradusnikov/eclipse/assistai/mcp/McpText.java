package com.github.gradusnikov.eclipse.assistai.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders a payload as the text content block that travels beside
 * {@code structuredContent}.
 * <p>
 * MCP asks a tool returning structured content to also return that data as text, so a
 * client reading only text is not left with a prose approximation of the result.
 * Serializing the payload answers that, but it escapes every source body, diff and stack
 * trace into one long line of {@code \n}s - the single shape a reader cannot skim.
 * <p>
 * So a string spanning more than one line is lifted out and rendered as a fenced block,
 * and the value it came from becomes a pointer naming the block that replaced it. The
 * rest is the same JSON as before. Between them the two carry every field, and
 * {@code structuredContent} - the form a client branches on - is untouched and remains
 * canonical. That is the one place the text block stops being byte-identical to the
 * serialized payload; it is a rendering of it, which is what a text block is for.
 * <p>
 * One rule, one implementation, applied to whatever a record happens to hold. Nothing is
 * registered per tool, so a new tool returning a source body is rendered properly
 * without being told to, and there is no second hand-written formatter to drift.
 */
public final class McpText
{
    /** Left where a lifted string was, naming the block that now carries it. */
    private static final String POINTER = "<rendered above as %s>";

    private McpText()
    {
    }

    /**
     * @param payload the same map that goes out as {@code structuredContent}, so the
     *            text block is demonstrably a rendering of what the client receives
     *            rather than a second computation of it
     * @return the fenced blocks, then the JSON of everything else
     */
    public static String render( Map<String, Object> payload )
    {
        List<Block> blocks = new ArrayList<>();
        Object remaining = lift( payload, "", null, blocks );

        if ( blocks.isEmpty() )
        {
            return McpJson.toJson( remaining );
        }

        StringBuilder text = new StringBuilder();
        for ( Block block : blocks )
        {
            // With one block the pointer is unambiguous on its own. With several, each
            // needs the name the pointer refers to.
            if ( blocks.size() > 1 )
            {
                text.append( block.path() ).append( ":\n" );
            }
            block.appendTo( text );
            text.append( '\n' );
        }
        return text.append( McpJson.toJson( remaining ) ).toString();
    }

    /**
     * Rebuilds the tree with every multi-line string replaced by a pointer, collecting
     * what was removed. Rebuilt rather than edited in place: the caller's map is the one
     * being sent as {@code structuredContent}, and must not be altered.
     */
    private static Object lift( Object node, String path, String language, List<Block> blocks )
    {
        if ( node instanceof Map<?, ?> map )
        {
            String here = languageOf( map, language );
            Map<String, Object> lifted = new LinkedHashMap<>();
            for ( Map.Entry<?, ?> entry : map.entrySet() )
            {
                String name = String.valueOf( entry.getKey() );
                lifted.put( name, lift( entry.getValue(), child( path, name ), here, blocks ) );
            }
            return lifted;
        }
        if ( node instanceof List<?> list )
        {
            List<Object> lifted = new ArrayList<>( list.size() );
            for ( int index = 0; index < list.size(); index++ )
            {
                lifted.add( lift( list.get( index ), path + "[" + index + "]", language, blocks ) );
            }
            return lifted;
        }
        if ( node instanceof String text && text.indexOf( '\n' ) >= 0 )
        {
            blocks.add( new Block( path, languageFor( path, language ), text ) );
            return POINTER.formatted( path );
        }
        return node;
    }

    private static String child( String path, String name )
    {
        return path.isEmpty() ? name : path + "." + name;
    }

    /**
     * The fence label for a level of the tree, inherited by everything below it.
     * <p>
     * A payload that reports its own language is believed. One that does not usually
     * still names a file, and the extension is the same tag the responses themselves
     * carry. This is only a fence label: a wrong guess still leaves a readable block, so
     * guessing beats a lookup that can fail on a decompiled class or a console.
     */
    private static String languageOf( Map<?, ?> map, String inherited )
    {
        if ( map.get( "language" ) instanceof String declared && !declared.isBlank() )
        {
            return declared;
        }
        if ( map.get( "filePath" ) instanceof String file )
        {
            int dot = file.lastIndexOf( '.' );
            if ( dot > 0 && dot < file.length() - 1 )
            {
                return file.substring( dot + 1 );
            }
        }
        return inherited;
    }

    /** A unified diff reads as a diff whatever file it was taken from. */
    private static String languageFor( String path, String language )
    {
        return path.toLowerCase( Locale.ROOT ).endsWith( "diff" ) ? "diff" : language;
    }

    private record Block( String path, String language, String content )
    {
        void appendTo( StringBuilder text )
        {
            String fence = fence();

            text.append( fence );
            if ( language != null )
            {
                text.append( language );
            }
            text.append( '\n' ).append( content );
            if ( !content.endsWith( "\n" ) )
            {
                text.append( '\n' );
            }
            text.append( fence ).append( '\n' );
        }

        /**
         * Longer than any run of backticks inside the content, so a block that is itself
         * Markdown - a Javadoc body, a web page - cannot close its own fence early.
         */
        private String fence()
        {
            int longest = 0;
            int run = 0;
            for ( int index = 0; index < content.length(); index++ )
            {
                run = content.charAt( index ) == '`' ? run + 1 : 0;
                longest = Math.max( longest, run );
            }
            return "`".repeat( Math.max( 3, longest + 1 ) );
        }
    }
}
