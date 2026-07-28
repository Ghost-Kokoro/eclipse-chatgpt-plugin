package com.github.gradusnikov.eclipse.assistai.tools;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;

/**
 * Maps character offsets in a text resource onto line numbers.
 * <p>
 * The work is done by {@link IDocument}, whose line tracker already understands
 * {@code \n}, {@code \r} and {@code \r\n} and mixtures of them. Counting terminators
 * by hand is what put every search match in a CRLF file on the wrong line: the
 * arithmetic added one character per line, so the error grew with the line number.
 */
public final class LineOffsets
{
    /** A 1-based line number and that line's text, excluding its delimiter. */
    public record LineInfo( int lineNumber, String lineContent )
    {
    }

    /** Returned when an offset falls outside the content. */
    public static final LineInfo UNKNOWN = new LineInfo( -1, "" );

    private LineOffsets()
    {
    }

    /**
     * Resolves a character offset to a 1-based line number and that line's text.
     *
     * @param content the whole resource, terminators included, exactly as the offset
     *            was computed against
     * @param offset 0-based character offset into {@code content}
     * @return the line holding {@code offset}, or {@link #UNKNOWN} if it falls outside
     */
    public static LineInfo lineInfoAt( String content, int offset )
    {
        if ( content == null || offset < 0 || offset > content.length() )
        {
            return UNKNOWN;
        }
        return lineInfoAt( new Document( content ), offset );
    }

    /**
     * Counts the lines of a text, by the same line tracker that resolves offsets.
     * <p>
     * Splitting on {@code "\n"} would report one line too many for a CRLF file whose
     * content ends without a terminator, and would not see a lone {@code \r} as a
     * break at all.
     */
    public static int countLines( String content )
    {
        if ( content == null || content.isEmpty() )
        {
            return 0;
        }
        return new Document( content ).getNumberOfLines();
    }

    /**
     * Resolves an offset within a document that is already open, so a caller holding
     * one does not pay to build another.
     */
    public static LineInfo lineInfoAt( IDocument document, int offset )
    {
        if ( document == null || offset < 0 || offset > document.getLength() )
        {
            return UNKNOWN;
        }
        try
        {
            int line = document.getLineOfOffset( offset );
            IRegion region = document.getLineInformation( line );
            return new LineInfo( line + 1, document.get( region.getOffset(), region.getLength() ) );
        }
        catch ( BadLocationException e )
        {
            return UNKNOWN;
        }
    }
}
