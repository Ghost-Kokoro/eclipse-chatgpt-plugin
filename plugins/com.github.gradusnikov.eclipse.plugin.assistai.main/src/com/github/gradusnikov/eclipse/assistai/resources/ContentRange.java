package com.github.gradusnikov.eclipse.assistai.resources;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;

/**
 * A range of text, in one-based lines and columns.
 * <p>
 * One-based to match every existing Eclipse tool in this plugin and what a user sees
 * in the editor's status bar. Conversion to and from the zero-based offsets that
 * {@link IDocument} works in happens here, once, rather than at each call site.
 */
public record ContentRange(
    int startLine,
    int startColumn,
    int endLine,
    int endColumn
)
{
    /**
     * The range covering whole lines, from the first column of {@code startLine} to
     * the end of {@code endLine}.
     */
    public static ContentRange ofLines( IDocument document, int startLine, int endLine ) throws BadLocationException
    {
        int lastLine = Math.min( endLine, document.getNumberOfLines() );
        int endColumn = document.getLineLength( lastLine - 1 ) + 1;
        return new ContentRange( startLine, 1, lastLine, Math.max( 1, endColumn ) );
    }

    /** The range covering an offset region of a document. */
    public static ContentRange of( IDocument document, int offset, int length ) throws BadLocationException
    {
        int startLine = document.getLineOfOffset( offset );
        int endLine = document.getLineOfOffset( offset + length );
        return new ContentRange(
                startLine + 1,
                offset - document.getLineOffset( startLine ) + 1,
                endLine + 1,
                offset + length - document.getLineOffset( endLine ) + 1 );
    }

    /** The whole of a document. */
    public static ContentRange wholeDocument( IDocument document ) throws BadLocationException
    {
        return of( document, 0, document.getLength() );
    }

    /** Converts back to the offset region {@link IDocument} operations take. */
    public IRegion toRegion( IDocument document ) throws BadLocationException
    {
        int startOffset = document.getLineOffset( startLine - 1 ) + ( startColumn - 1 );
        int endOffset = document.getLineOffset( endLine - 1 ) + ( endColumn - 1 );
        return new Region( startOffset, Math.max( 0, endOffset - startOffset ) );
    }

    /** Whether this range starts after it ends. */
    public boolean isEmpty()
    {
        return startLine == endLine && startColumn == endColumn;
    }

    @Override
    public String toString()
    {
        return startLine + ":" + startColumn + "-" + endLine + ":" + endColumn;
    }
}
