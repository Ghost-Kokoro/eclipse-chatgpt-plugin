package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * One thing that went wrong, reported as a field rather than in place of a result.
 * <p>
 * A response carrying diagnostics still carries everything else it managed to collect,
 * so "the run started and 3 of 40 tests had already passed when it timed out" is
 * expressible. Returning a {@code String} beginning with {@code "Error:"} instead of the
 * result made that case indistinguishable from a run that produced nothing.
 * <p>
 * Clients branch on {@link #code()}. The message stays readable, but nothing should
 * ever be decided by parsing it - ending that is the point of this type.
 *
 * @param retryable whether doing the same thing again could plausibly succeed - a
 *            locked workspace, yes; a misspelled class name, no. It is a hint to the
 *            caller's control flow, not a promise
 */
public record Diagnostic( DiagnosticCode code, String message, boolean retryable )
{
    /** A diagnostic that will not change on a retry of the same call. */
    public static Diagnostic fatal( DiagnosticCode code, String message )
    {
        return new Diagnostic( code, message, false );
    }

    /** A failure that may clear on its own - a lock, a race, a slow launch. */
    public static Diagnostic retryable( DiagnosticCode code, String message )
    {
        return new Diagnostic( code, message, true );
    }

    /** Nothing went wrong. Reads better than an empty list literal at a call site. */
    public static List<Diagnostic> none()
    {
        return List.of();
    }
}
