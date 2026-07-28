package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.gradusnikov.eclipse.assistai.resources.ContentRange;

/**
 * What one or more Eclipse consoles currently hold.
 * <p>
 * Deliberately not a
 * {@link com.github.gradusnikov.eclipse.assistai.resources.ResourceReadResult}. A
 * console is not a workspace file: it has no project, no path, no modification stamp
 * and nothing to pass as {@code expectedModificationStamp}, and it cannot be written
 * back. Forcing it into that record would have meant a read result whose every
 * location and version field was null, which reads as "we could not find out" rather
 * than "there is nothing to find out".
 * <p>
 * {@link #totalConsoles()} is what the selection was drawn from, so a caller can tell
 * "this is the only console there is" from "this is one of nine" - the difference
 * between a complete picture of a build and an arbitrary slice of one.
 *
 * @param totalConsoles how many consoles the workbench holds, whatever was returned
 */
public record ConsoleOutputResponse(
    Status status,
    int totalConsoles,
    List<ConsoleOutput> consoles,
    List<Diagnostic> diagnostics
)
{
    public enum Status
    {
        /** At least one console was read; it may still have been empty. */
        OK,
        /** No console could be read - see diagnostics. */
        FAILED
    }

    /**
     * The tail of one console's document.
     *
     * @param returnedRange the 1-based lines of the console document that
     *            {@link #text()} covers. A console is read from the end, so this
     *            normally ends at {@code totalLines}
     * @param totalLines how many lines the console holds, before {@code maxLines} was
     *            applied
     * @param truncated whether earlier lines exist that {@code maxLines} left out.
     *            Raise {@code maxLines} to see them - unlike a file, a console has no
     *            other way to reach them
     * @param text exact console text, with no fence and no line-number prefixes
     */
    public record ConsoleOutput(
        String consoleName,
        ContentRange returnedRange,
        int totalLines,
        boolean truncated,
        String text
    )
    {
        /** Whether this console had produced anything at all. */
        public boolean isEmpty()
        {
            return text == null || text.isBlank();
        }
    }

    /**
     * Whether the workbench has exactly one console, so what was returned is
     * everything there is. Derived, and not serialized: the two counts it compares are
     * both already fields.
     */
    @JsonIgnore
    public boolean onlyConsole()
    {
        return totalConsoles == 1;
    }

    /** No console could be read. Being unable to find one is a state, not an exception. */
    public static ConsoleOutputResponse failed( int totalConsoles, Diagnostic diagnostic )
    {
        return new ConsoleOutputResponse( Status.FAILED, totalConsoles, List.of(), List.of( diagnostic ) );
    }

    public static ConsoleOutputResponse of( int totalConsoles, List<ConsoleOutput> consoles )
    {
        return new ConsoleOutputResponse( Status.OK, totalConsoles, List.copyOf( consoles ), Diagnostic.none() );
    }
}
