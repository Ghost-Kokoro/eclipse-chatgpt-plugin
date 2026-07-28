package com.github.gradusnikov.eclipse.assistai.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * How a payload is rendered into the text content block.
 * <p>
 * The point of this rendering is narrow and worth stating: a source body, a diff or a
 * stack trace serialized into JSON becomes one line of escaped {@code \n}s, which is
 * readable by nothing. Lifting those into fenced blocks costs the text block its
 * byte-identity with the payload, so what these tests defend is that nothing is *lost*
 * in the trade - every field still arrives, and {@code structuredContent}, which is what
 * a client branches on, is not touched at all.
 * <p>
 * Runs under the PDE harness because it serializes through {@link McpJson}, which needs
 * Jackson from the bundle's own classpath.
 */
public class McpTextPDETest
{
    private static final String BODY = "public void greet()\n{\n    say( \"hello\" );\n}\n";

    record Read( String status, String language, String filePath, String content )
    {
    }

    record Method( String methodName, String source )
    {
    }

    record Methods( String className, String filePath, List<Method> methods )
    {
    }

    record Edit( String filePath, String unifiedDiff )
    {
    }

    record Listing( String status, int totalProjects )
    {
    }

    private static String render( Object payload )
    {
        return McpText.render( McpJson.toMap( payload ) );
    }

    private static Read aRead()
    {
        return new Read( "OK", "java", "src/Greeter.java", BODY );
    }

    // ---- the reason this exists ------------------------------------------

    @Test
    public void rendersAMultiLineStringAsAFencedBlock()
    {
        String text = render( aRead() );

        assertTrue( text.startsWith( "```java\n" ), text );
        assertTrue( text.contains( "    say( \"hello\" );" ),
                "the body has to arrive as lines, not as one escaped string" );
    }

    @Test
    public void doesNotAlsoLeaveTheEscapedCopyBehind()
    {
        String text = render( aRead() );

        assertFalse( text.contains( "\\n" ),
                "keeping both renderings would double the cost of every source body" );
    }

    @Test
    public void leavesAPointerWhereTheContentWas()
    {
        String text = render( aRead() );

        assertTrue( text.contains( "<rendered above as content>" ),
                "a reader of the JSON must be told where the value went" );
    }

    @Test
    public void stillCarriesEveryOtherField()
    {
        String text = render( aRead() );

        // The trade is byte-identity, not completeness: a text-only client must still
        // be able to see everything the payload held.
        assertTrue( text.contains( "\"status\"" ) );
        assertTrue( text.contains( "\"OK\"" ) );
        assertTrue( text.contains( "\"filePath\"" ) );
        assertTrue( text.contains( "src/Greeter.java" ) );
    }

    @Test
    public void doesNotDisturbTheMapBeingSentAsStructuredContent()
    {
        Map<String, Object> payload = McpJson.toMap( aRead() );

        McpText.render( payload );

        assertEquals( BODY, payload.get( "content" ),
                "structuredContent is the canonical form and must survive rendering intact" );
    }

    // ---- content that is not at the top level ----------------------------

    @Test
    public void liftsContentOutOfNestedRecords()
    {
        // getMethodSource keeps each body in methods[].source, so a top-level-only rule
        // would miss the case that motivated the whole change.
        String text = render( new Methods( "Greeter", "src/Greeter.java",
                List.of( new Method( "greet", BODY ) ) ) );

        assertTrue( text.startsWith( "```java\n" ), text );
        assertTrue( text.contains( "<rendered above as methods[0].source>" ), text );
    }

    @Test
    public void namesEachBlockWhenThereIsMoreThanOne()
    {
        String text = render( new Methods( "Greeter", "src/Greeter.java",
                List.of( new Method( "greet", BODY ), new Method( "farewell", BODY ) ) ) );

        assertTrue( text.startsWith( "methods[0].source:\n" ), text );
        assertTrue( text.contains( "methods[1].source:\n" ), text );
    }

    @Test
    public void doesNotNameTheBlockWhenThereIsOnlyOne()
    {
        // One block and one pointer cannot be confused, so the heading would be noise.
        assertFalse( render( aRead() ).contains( "content:\n" ) );
    }

    // ---- fence labels ----------------------------------------------------

    @Test
    public void takesTheFenceLabelFromThePayloadWhenItReportsOne()
    {
        assertTrue( render( aRead() ).startsWith( "```java\n" ) );
    }

    @Test
    public void fallsBackToTheFileExtension()
    {
        // Methods carries no language field, only a path - the same extension-as-tag
        // rule the responses themselves use.
        String text = render( new Methods( "Greeter", "src/Greeter.java",
                List.of( new Method( "greet", BODY ) ) ) );

        assertTrue( text.startsWith( "```java\n" ), text );
    }

    @Test
    public void labelsADiffAsADiffWhateverFileItCameFrom()
    {
        String text = render( new Edit( "src/Greeter.java",
                "@@ -1,2 +1,2 @@\n-was\n+now\n" ) );

        assertTrue( text.startsWith( "```diff\n" ), text );
    }

    // ---- content that fights the fence -----------------------------------

    @Test
    public void usesAFenceLongEnoughToSurviveMarkdownContent()
    {
        // A Javadoc body or a fetched web page can contain a fence of its own, which
        // would otherwise close the block early and spill the rest as prose.
        String text = render( new Read( "OK", "md", "README.md",
                "Example:\n```java\nint x = 1;\n```\nDone.\n" ) );

        assertTrue( text.startsWith( "````md\n" ), text );
        assertTrue( text.contains( "````\n" ), "the closing fence has to match the opening one" );
        assertTrue( text.contains( "int x = 1;" ) );
    }

    @Test
    public void doesNotAddASecondTrailingNewline()
    {
        String text = render( aRead() );

        assertFalse( text.contains( "}\n\n```" ),
                "content already ending in a newline must not gain a blank line" );
    }

    // ---- everything else is unchanged ------------------------------------

    @Test
    public void leavesAPayloadWithoutMultiLineStringsAsPlainJson()
    {
        // Most tools return no long text at all, and their text block should be exactly
        // what it was before this rendering existed.
        Listing listing = new Listing( "OK", 16 );

        assertEquals( McpJson.toJson( McpJson.toMap( listing ) ), render( listing ) );
    }

    @Test
    public void leavesSingleLineStringsAlone()
    {
        String text = render( new Listing( "OK", 16 ) );

        assertTrue( text.contains( "\"OK\"" ) );
        assertFalse( text.contains( "```" ), "a one-line value gains nothing from a fence" );
    }
}
