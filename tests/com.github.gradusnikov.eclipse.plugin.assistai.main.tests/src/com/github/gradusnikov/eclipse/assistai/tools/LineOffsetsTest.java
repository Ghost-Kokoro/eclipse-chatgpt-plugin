package com.github.gradusnikov.eclipse.assistai.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Plain JUnit tests for {@link LineOffsets}. It delegates to the platform's line
 * tracker, which needs no Eclipse runtime, so these run outside the PDE harness.
 * <p>
 * The regression these cover: the line number of a search match used to be derived by
 * adding one character per line for the terminator, which is right for LF and one
 * short for CRLF. Every assertion here that uses \r\n failed before the fix.
 */
public class LineOffsetsTest
{
    private static LineOffsets.LineInfo resolve( String content, String needle )
    {
        return LineOffsets.lineInfoAt( content, content.indexOf( needle ) );
    }

    // ---- line resolution -----------------------------------------------

    @Test
    public void resolvesMatchInLfFile()
    {
        LineOffsets.LineInfo info = resolve( "alpha\nbravo\ncharlie\n", "bravo" );

        assertEquals( 2, info.lineNumber() );
        assertEquals( "bravo", info.lineContent() );
    }

    @Test
    public void resolvesMatchInCrlfFile()
    {
        LineOffsets.LineInfo info = resolve( "alpha\r\nbravo\r\ncharlie\r\n", "bravo" );

        assertEquals( 2, info.lineNumber() );
        assertEquals( "bravo", info.lineContent() );
    }

    @Test
    public void doesNotDriftOnLongCrlfFile()
    {
        // The old arithmetic lost one character per line, so the drift grew with the
        // line count until matches landed dozens of lines below where they were.
        StringBuilder content = new StringBuilder();
        for ( int i = 1; i <= 1000; i++ )
        {
            content.append( "    // filler line " ).append( i ).append( " of the file\r\n" );
        }
        content.append( "    NEEDLE;\r\n" );

        LineOffsets.LineInfo info = LineOffsets.lineInfoAt( content.toString(), content.indexOf( "NEEDLE" ) );

        assertEquals( 1001, info.lineNumber() );
        assertEquals( "    NEEDLE;", info.lineContent() );
    }

    @Test
    public void reportsSameLineRegardlessOfTerminator()
    {
        LineOffsets.LineInfo lf = resolve( "alpha\nbravo\n", "bravo" );
        LineOffsets.LineInfo crlf = resolve( "alpha\r\nbravo\r\n", "bravo" );

        assertEquals( lf.lineNumber(), crlf.lineNumber() );
        assertEquals( lf.lineContent(), crlf.lineContent() );
    }

    @Test
    public void resolvesMatchOnFirstLine()
    {
        LineOffsets.LineInfo info = resolve( "alpha\r\nbravo\r\n", "alpha" );

        assertEquals( 1, info.lineNumber() );
        assertEquals( "alpha", info.lineContent() );
    }

    @Test
    public void resolvesMatchOnUnterminatedLastLine()
    {
        LineOffsets.LineInfo info = resolve( "alpha\r\nbravo", "bravo" );

        assertEquals( 2, info.lineNumber() );
        assertEquals( "bravo", info.lineContent() );
    }

    @Test
    public void countsBlankLine()
    {
        LineOffsets.LineInfo info = resolve( "alpha\r\n\r\nbravo\r\n", "bravo" );

        assertEquals( 3, info.lineNumber() );
        assertEquals( "bravo", info.lineContent() );
    }

    @Test
    public void handlesMixedTerminators()
    {
        LineOffsets.LineInfo info = resolve( "alpha\nbravo\r\ncharlie\n", "charlie" );

        assertEquals( 3, info.lineNumber() );
        assertEquals( "charlie", info.lineContent() );
    }

    @Test
    public void handlesLoneCarriageReturnTerminator()
    {
        // The platform line tracker treats a bare \r as a delimiter; hand-rolled
        // arithmetic keyed on \n did not.
        LineOffsets.LineInfo info = resolve( "alpha\rbravo\r", "bravo" );

        assertEquals( 2, info.lineNumber() );
        assertEquals( "bravo", info.lineContent() );
    }

    // ---- out of range --------------------------------------------------

    @Test
    public void returnsUnknownLineForOffsetPastEnd()
    {
        LineOffsets.LineInfo info = LineOffsets.lineInfoAt( "alpha\r\n", 999 );

        assertEquals( -1, info.lineNumber() );
        assertEquals( "", info.lineContent() );
    }

    @Test
    public void returnsUnknownLineForNegativeOffset()
    {
        assertEquals( -1, LineOffsets.lineInfoAt( "alpha\r\n", -1 ).lineNumber() );
    }

    @Test
    public void returnsUnknownLineForNullContent()
    {
        assertEquals( -1, LineOffsets.lineInfoAt( (String) null, 0 ).lineNumber() );
    }
}
