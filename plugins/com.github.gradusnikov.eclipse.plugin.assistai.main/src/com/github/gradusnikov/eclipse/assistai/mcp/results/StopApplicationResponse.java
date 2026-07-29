package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * What a stop request terminated.
 * <p>
 * Two things a caller had to read apart from prose: "the filter matched nothing" and "it
 * went wrong" arrived as two sentences with nothing in common, and the terminated names
 * were comma-joined although launch configuration names routinely contain commas, so a
 * caller splitting the list got the wrong names. Both are fixed by making the outcome a
 * {@link Status} and the names a list.
 * <p>
 * {@link #totalMatched()} beside {@code terminated.size()} is what says a stop only
 * partly worked - three launches matched, one refused to die - which the old sentence
 * could not express at all.
 *
 * @param nameOrClass the filter the caller passed, echoed back
 * @param totalMatched how many running launches the filter matched, whether or not they
 *            could be terminated
 */
public record StopApplicationResponse(
    Status status,
    String nameOrClass,
    int totalMatched,
    List<TerminatedLaunch> terminated,
    List<Diagnostic> diagnostics,
    String summaryText
)
{
    public enum Status
    {
        /** At least one matching launch was terminated. */
        OK,
        /** Nothing was running that matched. A state, not an error. */
        NO_MATCH,
        /** Launches matched and none of them could be terminated - see diagnostics. */
        FAILED
    }

    /**
     * One launch that was stopped.
     *
     * @param launchName the launch configuration's name, or null when the configuration
     *            is gone - a launch outlives a deleted configuration
     * @param mainType the main class the configuration names, or null when it names none
     * @param mode {@code "run"} or {@code "debug"}
     */
    public record TerminatedLaunch(
        String launchName,
        String mainType,
        String mode
    )
    {
    }

    /** Whether every matching launch was stopped. Not serialized. */
    public boolean stoppedEverythingMatched()
    {
        return totalMatched == terminated.size();
    }

    /** Nothing matched. The caller's filter is wrong, or it already stopped. */
    public static StopApplicationResponse noMatch( String nameOrClass )
    {
        return new StopApplicationResponse( Status.NO_MATCH, nameOrClass, 0, List.of(), Diagnostic.none(),
                "No running application matching '" + nameOrClass + "'." );
    }

    public static StopApplicationResponse of( String nameOrClass, int totalMatched,
            List<TerminatedLaunch> terminated, List<Diagnostic> diagnostics )
    {
        if ( totalMatched == 0 )
        {
            return noMatch( nameOrClass );
        }

        Status status = terminated.isEmpty() ? Status.FAILED : Status.OK;
        String summary = terminated.isEmpty()
                ? "None of the " + totalMatched + " launches matching '" + nameOrClass + "' could be terminated."
                : "Terminated " + terminated.size() + " of " + totalMatched
                        + ( totalMatched == 1 ? " launch matching '" : " launches matching '" ) + nameOrClass + "'.";

        return new StopApplicationResponse( status, nameOrClass, totalMatched, List.copyOf( terminated ),
                List.copyOf( diagnostics ), summary );
    }
}
