package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

import com.github.gradusnikov.eclipse.assistai.resources.ContentRange;

/**
 * The heading structure of a Markdown file.
 * <p>
 * An outline is never the answer on its own: a caller reads it to decide which section
 * to fetch next with {@code getMarkdownSection}. That tool accepts either a 1-based
 * index or a text match, so both {@link Heading#index()} and {@link Heading#text()}
 * are carried - the index because two sections in a long document routinely have the
 * same title, and matching by text would then fetch the wrong one.
 * <p>
 * The previous rendering indented each heading by two spaces per level and appended
 * {@code [line 12, 40 lines]}. Both facts are now fields, so nothing has to be
 * recovered by counting leading spaces.
 *
 * @param totalLines the whole file, so a caller can weigh one section against it
 * @param headings in document order, empty when the file has none - which is a fact
 *            about the file, not a failure
 */
public record MarkdownOutlineResponse(
    Status status,
    String projectName,
    String filePath,
    int totalLines,
    List<Heading> headings,
    List<Diagnostic> diagnostics
)
{
    public enum Status
    {
        /** The file was parsed; {@code headings} may still be empty. */
        OK,
        /** The file could not be read - see diagnostics. */
        FAILED
    }

    /**
     * One heading and the section it opens.
     *
     * @param index 1-based position in {@link #headings()}, which is what
     *            {@code getMarkdownSection} takes as its {@code heading} argument
     * @param level 1 for {@code #}, 6 for {@code ######}; a Setext {@code ===}
     *            underline is reported as level 1 and {@code ---} as level 2
     * @param range from the heading line to the line before the next heading of any
     *            level, or the end of the file
     */
    public record Heading(
        int index,
        int level,
        String text,
        ContentRange range
    )
    {
        /** How many lines this section costs, which is what a caller budgets against. */
        public int lineCount()
        {
            return range.endLine() - range.startLine() + 1;
        }
    }

    public static MarkdownOutlineResponse failed( String projectName, String filePath, Diagnostic diagnostic )
    {
        return new MarkdownOutlineResponse( Status.FAILED, projectName, filePath, 0,
                List.of(), List.of( diagnostic ) );
    }

    public static MarkdownOutlineResponse of( String projectName, String filePath, int totalLines,
                                              List<Heading> headings )
    {
        return new MarkdownOutlineResponse( Status.OK, projectName, filePath, totalLines,
                List.copyOf( headings ), Diagnostic.none() );
    }
}
