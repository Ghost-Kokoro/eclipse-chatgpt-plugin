package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * What a breakpoint write did.
 * <p>
 * One record for {@code toggleBreakpoint} and {@code setConditionalBreakpoint}, because
 * they have one shape: both end with a breakpoint that either exists or does not, and
 * the caller's next question is the same - where is it, and is it armed. The breakpoint
 * itself is reported as the {@link BreakpointsResponse.BreakpointInfo} the read tool
 * already returns, so a caller does not need two ways to read one thing.
 * <p>
 * Three defects this closes:
 * <ul>
 * <li>{@code toggleBreakpoint} is a two-way door whose only signal for which way it went
 * was the word "set" versus "removed" inside a sentence. That is now {@link #action()}.</li>
 * <li>{@code setConditionalBreakpoint} emitted {@code Type:line … condition: i > 100} -
 * the exact rendering {@link BreakpointsResponse} records as already fixed for the read
 * side, splitting on a ':' that also occurs inside the condition.</li>
 * <li>Neither checked that the type resolved or that the line existed, so a breakpoint
 * on a misspelled type was created happily, never bound, and reported "Breakpoint set".
 * That is now {@link Status#TYPE_NOT_FOUND} / {@link Status#INVALID_LINE}, and nothing
 * is created.</li>
 * </ul>
 *
 * @param projectName the project as asked for, echoed back so a request that created
 *            nothing still says what it was
 * @param typeName the fully qualified type as asked for
 * @param lineNumber the 1-based line as asked for
 * @param breakpoint the breakpoint that now exists, or - for {@link Action#REMOVED} -
 *            the one that was taken away, read before it was deleted. Null when nothing
 *            was created and nothing removed
 */
public record BreakpointResponse(
    Status status,
    Action action,
    String projectName,
    String typeName,
    int lineNumber,
    BreakpointsResponse.BreakpointInfo breakpoint,
    List<Diagnostic> diagnostics,
    String summaryText
)
{
    /** Which way the door went. */
    public enum Action
    {
        /** There was no breakpoint at the line and now there is one. */
        SET,
        /** There was one and now there is not. */
        REMOVED,
        /** There was one, it was replaced - what setting a condition over an existing breakpoint does. */
        REPLACED,
        /** Nothing changed, because the request did not validate. */
        NONE
    }

    /** Whether the workspace was changed, and if not, why not. */
    public enum Status
    {
        OK,
        /** No project of that name, or it is closed. */
        PROJECT_NOT_FOUND,
        /** The project resolves no type of that name, so a breakpoint there would never bind. */
        TYPE_NOT_FOUND,
        /** The type's source file has no such line. */
        INVALID_LINE,
        /** The breakpoint manager refused - see diagnostics. */
        FAILED
    }

    /** Whether a breakpoint is armed at the requested line after this call. Not serialized. */
    public boolean breakpointPresent()
    {
        return status == Status.OK && action != Action.REMOVED && action != Action.NONE;
    }

    private static String location( String typeName, int lineNumber )
    {
        return typeName + " line " + lineNumber;
    }

    public static BreakpointResponse projectNotFound( String projectName, String typeName, int lineNumber )
    {
        return new BreakpointResponse( Status.PROJECT_NOT_FOUND, Action.NONE, projectName, typeName, lineNumber, null,
                List.of( Diagnostic.fatal( DiagnosticCode.PROJECT_NOT_FOUND,
                        "No open project named '" + projectName + "' in the workspace." ) ),
                "No breakpoint was set: project '" + projectName + "' was not found." );
    }

    /**
     * The type does not resolve in the project. Reported rather than created: a
     * breakpoint on a type the project does not have never binds, and a caller told
     * "set" would wait at it forever.
     */
    public static BreakpointResponse typeNotFound( String projectName, String typeName, int lineNumber )
    {
        return new BreakpointResponse( Status.TYPE_NOT_FOUND, Action.NONE, projectName, typeName, lineNumber, null,
                List.of( Diagnostic.fatal( DiagnosticCode.RESOURCE_NOT_FOUND,
                        "Project '" + projectName + "' resolves no type named '" + typeName
                                + "'; a breakpoint there would never bind." ) ),
                "No breakpoint was set: '" + typeName + "' does not resolve in '" + projectName + "'." );
    }

    /**
     * @param totalLines the type's source line count, or -1 when there is no source to
     *            measure - a binary type with no attachment
     */
    public static BreakpointResponse invalidLine( String projectName, String typeName, int lineNumber, int totalLines )
    {
        String detail = "Line " + lineNumber + " does not exist in the source of '" + typeName + "'"
                + ( totalLines > 0 ? ", which has " + totalLines + " lines." : "." );

        return new BreakpointResponse( Status.INVALID_LINE, Action.NONE, projectName, typeName, lineNumber, null,
                List.of( Diagnostic.fatal( DiagnosticCode.INVALID_RANGE, detail ) ),
                "No breakpoint was set: " + detail );
    }

    public static BreakpointResponse failed( String projectName, String typeName, int lineNumber,
            Diagnostic diagnostic )
    {
        return new BreakpointResponse( Status.FAILED, Action.NONE, projectName, typeName, lineNumber, null,
                List.of( diagnostic ), diagnostic.message() );
    }

    public static BreakpointResponse set( String projectName, String typeName, int lineNumber,
            BreakpointsResponse.BreakpointInfo breakpoint )
    {
        return new BreakpointResponse( Status.OK, Action.SET, projectName, typeName, lineNumber, breakpoint,
                Diagnostic.none(), "Breakpoint set at " + location( typeName, lineNumber ) + "." );
    }

    public static BreakpointResponse replaced( String projectName, String typeName, int lineNumber,
            BreakpointsResponse.BreakpointInfo breakpoint )
    {
        return new BreakpointResponse( Status.OK, Action.REPLACED, projectName, typeName, lineNumber, breakpoint,
                Diagnostic.none(),
                "Breakpoint replaced at " + location( typeName, lineNumber ) + "." );
    }

    public static BreakpointResponse removed( String projectName, String typeName, int lineNumber,
            BreakpointsResponse.BreakpointInfo breakpoint )
    {
        return new BreakpointResponse( Status.OK, Action.REMOVED, projectName, typeName, lineNumber, breakpoint,
                Diagnostic.none(), "Breakpoint removed at " + location( typeName, lineNumber ) + "." );
    }
}
