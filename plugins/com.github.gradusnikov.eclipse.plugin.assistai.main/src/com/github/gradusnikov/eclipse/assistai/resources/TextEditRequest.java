package com.github.gradusnikov.eclipse.assistai.resources;

/**
 * One requested replacement, before it becomes an {@link org.eclipse.text.edits.TextEdit}.
 * <p>
 * Named {@code ...Request} because {@code TextEdit} is the platform's own type, which
 * is what actually performs the change - this is only what a caller asked for.
 *
 * @param range the text to replace, in one-based lines and columns
 * @param expectedText what the caller believes is currently in {@code range}, or null
 *            to skip the check. A cheap guard against a range computed from content
 *            that has since moved, complementing the modification-stamp check that
 *            guards the resource as a whole
 * @param replacement the text to put there; empty string deletes
 */
public record TextEditRequest(
    ContentRange range,
    String expectedText,
    String replacement
)
{
    public TextEditRequest
    {
        if ( range == null )
        {
            throw new IllegalArgumentException( "range is required" );
        }
        if ( replacement == null )
        {
            replacement = "";
        }
    }
}
