package com.github.gradusnikov.eclipse.assistai.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.junit.jupiter.api.Test;

/**
 * Plain JUnit tests for the one-based range type and the occurrence selector. Both
 * rely only on the platform's document classes, which need no Eclipse runtime.
 */
public class ContentRangeTest
{
    private static final String LF_SOURCE = "alpha\nbravo\ncharlie\n";

    private static final String CRLF_SOURCE = "alpha\r\nbravo\r\ncharlie\r\n";

    // ---- offset to range ------------------------------------------------

    @Test
    public void describesARangeWithinOneLine()
    {
        IDocument document = new Document( LF_SOURCE );

        ContentRange range = assertDoesNotThrowRange( () -> ContentRange.of( document, LF_SOURCE.indexOf( "bravo" ), 5 ) );

        assertEquals( 2, range.startLine() );
        assertEquals( 1, range.startColumn() );
        assertEquals( 2, range.endLine() );
        assertEquals( 6, range.endColumn() );
    }

    @Test
    public void describesARangeSpanningLines()
    {
        IDocument document = new Document( LF_SOURCE );
        int start = LF_SOURCE.indexOf( "bravo" );
        int end = LF_SOURCE.indexOf( "charlie" ) + "charlie".length();

        ContentRange range = assertDoesNotThrowRange( () -> ContentRange.of( document, start, end - start ) );

        assertEquals( 2, range.startLine() );
        assertEquals( 3, range.endLine() );
        assertEquals( 8, range.endColumn() );
    }

    @Test
    public void reportsTheSameRangeForCrlfAsForLf()
    {
        IDocument lf = new Document( LF_SOURCE );
        IDocument crlf = new Document( CRLF_SOURCE );

        ContentRange lfRange = assertDoesNotThrowRange( () -> ContentRange.of( lf, LF_SOURCE.indexOf( "bravo" ), 5 ) );
        ContentRange crlfRange = assertDoesNotThrowRange( () -> ContentRange.of( crlf, CRLF_SOURCE.indexOf( "bravo" ), 5 ) );

        assertEquals( lfRange, crlfRange );
    }

    // ---- range back to offset -------------------------------------------

    @Test
    public void roundTripsThroughRegion() throws Exception
    {
        IDocument document = new Document( LF_SOURCE );
        int offset = LF_SOURCE.indexOf( "bravo" );

        ContentRange range = ContentRange.of( document, offset, 5 );
        IRegion region = range.toRegion( document );

        assertEquals( offset, region.getOffset() );
        assertEquals( 5, region.getLength() );
        assertEquals( "bravo", document.get( region.getOffset(), region.getLength() ) );
    }

    @Test
    public void roundTripsThroughRegionOnCrlf() throws Exception
    {
        IDocument document = new Document( CRLF_SOURCE );
        int offset = CRLF_SOURCE.indexOf( "charlie" );

        ContentRange range = ContentRange.of( document, offset, 7 );
        IRegion region = range.toRegion( document );

        assertEquals( "charlie", document.get( region.getOffset(), region.getLength() ) );
    }

    @Test
    public void coversTheWholeDocument() throws Exception
    {
        IDocument document = new Document( LF_SOURCE );

        ContentRange range = ContentRange.wholeDocument( document );
        IRegion region = range.toRegion( document );

        assertEquals( 0, region.getOffset() );
        assertEquals( LF_SOURCE.length(), region.getLength() );
    }

    @Test
    public void rejectsARangeOutsideTheDocument()
    {
        IDocument document = new Document( LF_SOURCE );
        ContentRange beyondEnd = new ContentRange( 99, 1, 99, 1 );

        assertThrows( Exception.class, () -> beyondEnd.toRegion( document ) );
    }

    @Test
    public void reportsAnEmptyRange()
    {
        assertTrue( new ContentRange( 3, 5, 3, 5 ).isEmpty() );
    }

    // ---- occurrence selection -------------------------------------------

    @Test
    public void defaultsToUniqueWhenOccurrenceIsAbsent()
    {
        assertEquals( Occurrence.UNIQUE, Occurrence.parse( null ) );
        assertEquals( Occurrence.UNIQUE, Occurrence.parse( "" ) );
        assertEquals( Occurrence.UNIQUE, Occurrence.parse( "   " ) );
    }

    @Test
    public void parsesOccurrenceCaseInsensitively()
    {
        assertEquals( Occurrence.ALL, Occurrence.parse( "all" ) );
        assertEquals( Occurrence.FIRST, Occurrence.parse( " First " ) );
        assertEquals( Occurrence.INDEX, Occurrence.parse( "INDEX" ) );
    }

    @Test
    public void rejectsAnUnknownOccurrence()
    {
        IllegalArgumentException error =
                assertThrows( IllegalArgumentException.class, () -> Occurrence.parse( "everything" ) );

        assertTrue( error.getMessage().contains( "UNIQUE" ), "the message should list the valid values" );
    }

    // ---- helper ---------------------------------------------------------

    private interface RangeSupplier
    {
        ContentRange get() throws Exception;
    }

    private static ContentRange assertDoesNotThrowRange( RangeSupplier supplier )
    {
        try
        {
            return supplier.get();
        }
        catch ( Exception e )
        {
            throw new AssertionError( "range computation failed", e );
        }
    }
}
