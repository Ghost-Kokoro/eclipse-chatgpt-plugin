package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * Where a debugged program is stopped.
 * <p>
 * The old rendering was an indented transcript - {@code Thread: main [SUSPENDED]},
 * then {@code 0: Main.run() line: 42} straight from the debug model's own label - so
 * the frame's file was never named and the caller could not open it. Three different
 * outcomes also arrived as three sentences a caller had to read: no matching session,
 * a session whose threads are all running, and a real stack. They are now
 * {@link #sessionFound()}, {@link #anyThreadSuspended()} and the frames themselves.
 */
public record StackTraceResponse(
    String nameOrClass,
    boolean sessionFound,
    String launchName,
    String mainType,
    boolean anyThreadSuspended,
    int totalThreads,
    List<ThreadTrace> threads,
    String summaryText
)
{
    /**
     * @param suspended a running thread has no frames to report; that is a state, not a
     *            failure
     */
    public record ThreadTrace(
        String name,
        boolean suspended,
        int totalFrames,
        List<Frame> frames
    )
    {
    }

    /**
     * One stack frame.
     *
     * @param index 0 for the frame execution is stopped in
     * @param declaringType the fully qualified type declaring the method
     * @param projectName the Eclipse project holding the frame's source, or null when
     *            the source locator found none
     * @param filePath path relative to the project root, so the frame can be handed
     *            straight to the reading tools; null when the source is outside the
     *            workspace, as it is for JRE and library frames
     * @param lineNumber 1-based, or -1 when the class carries no line number table
     * @param nativeMethod a native frame has no source line to open
     * @param synthetic a compiler-generated frame - a bridge or lambda body - which is
     *            rarely where a caller wants to look
     * @param variables the locals visible in the frame. Reading them costs a round trip
     *            to the VM per frame, so this is filled in for the top frame only and
     *            is empty elsewhere
     */
    public record Frame(
        int index,
        String declaringType,
        String methodName,
        String projectName,
        String filePath,
        int lineNumber,
        boolean nativeMethod,
        boolean synthetic,
        List<Variable> variables
    )
    {
    }

    /**
     * @param value the debug model's string rendering of the value, or null when the VM
     *            refused to produce one
     */
    public record Variable(
        String name,
        String typeName,
        String value
    )
    {
    }

    /** No debug session matched - a state the caller acts on, not an error. */
    public static StackTraceResponse notFound( String nameOrClass )
    {
        return new StackTraceResponse( nameOrClass, false, null, null, false, 0, List.of(),
                "No active debug session matching '" + nameOrClass + "'." );
    }

    public static StackTraceResponse of( String nameOrClass, String launchName, String mainType,
            List<ThreadTrace> threads )
    {
        long suspended = threads.stream().filter( ThreadTrace::suspended ).count();

        String subject = mainType == null || mainType.isBlank() ? launchName : mainType;
        String summary = suspended == 0
                ? subject + " is running; no thread is suspended."
                : subject + ": " + suspended + ( suspended == 1 ? " suspended thread of " : " suspended threads of " )
                        + threads.size() + ".";

        return new StackTraceResponse( nameOrClass, true, launchName, mainType, suspended > 0,
                threads.size(), List.copyOf( threads ), summary );
    }
}
