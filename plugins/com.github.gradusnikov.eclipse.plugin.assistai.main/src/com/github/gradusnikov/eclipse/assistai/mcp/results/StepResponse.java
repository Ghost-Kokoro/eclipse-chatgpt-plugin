package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * Where a debugged program is after a step or a resume.
 * <p>
 * A step is not a command with a confirmation; it is a query whose answer is a program
 * counter. What this replaces held {@code frames[0]} and kept only {@code getName()} -
 * for a JDT frame the bare method name - so {@code "Step into completed. Now at:
 * compute"} was the whole answer and every useful next action cost another
 * {@code getStackTrace}. The frame is now reported in full, in the same
 * {@link StackTraceResponse.Frame} shape that tool returns.
 * <p>
 * Resume folds in as a {@link Kind#RESUME}: "run to the next breakpoint" answers the
 * same question a step does, and answering it with {@code "Debug session resumed."} said
 * nothing about where the program ended up. It also fixes the defect that made a missed
 * resume unreadable - the old {@code "No active debug session found matching '…'"} was
 * the one failure sentence here not prefixed {@code "Error:"}, so a caller checking
 * {@code startsWith("Error:")} treated a completely missed resume as done and then
 * waited at a breakpoint that would never be reached. That is now
 * {@link Status#SESSION_NOT_FOUND}.
 *
 * @param nameOrClass the filter the caller passed, echoed back
 * @param launchName the debug session that was stepped, null when none matched
 * @param threadName the thread that was stepped or that suspended. Stepping "the first
 *            suspended thread found" without saying which is wrong in any multithreaded
 *            target, so the answer names it
 * @param frame where execution is now - null unless {@link #status()} is
 *            {@link Status#SUSPENDED}
 * @param waitedMillis how long this call actually waited for the program to stop again.
 *            The old implementation slept a flat 500 ms and then reported whatever it
 *            found, so a step taking longer read as {@code "Thread is running."} and the
 *            caller concluded the program had resumed
 */
public record StepResponse(
    Status status,
    Kind kind,
    String nameOrClass,
    String launchName,
    String threadName,
    StackTraceResponse.Frame frame,
    long waitedMillis,
    List<Diagnostic> diagnostics,
    String summaryText
)
{
    /** What was asked for. Four tools, one shape, because they have one answer. */
    public enum Kind
    {
        STEP_OVER,
        STEP_INTO,
        STEP_RETURN,
        /** Run until the next breakpoint, or until the program ends. */
        RESUME
    }

    /** How the program ended up. */
    public enum Status
    {
        /** Stopped again; {@link #frame()} says where. */
        SUSPENDED,
        /** The caller asked not to wait. The program is running; nothing was observed. */
        RUNNING,
        /** The program ended while we were waiting for it to stop. */
        TERMINATED,
        /** We waited and it never stopped. Distinct from {@link #RUNNING}: we looked. */
        TIMED_OUT,
        /** The session matched but no thread of it is suspended, so nothing can step. */
        NO_SUSPENDED_THREAD,
        /** A thread name was given and no suspended thread of the session has it. */
        THREAD_NOT_FOUND,
        /** No debug session matched the filter. A state, not an error. */
        SESSION_NOT_FOUND,
        /** The debug model refused the request - see diagnostics. */
        FAILED
    }

    /** Whether the program is stopped and {@link #frame()} can be read. Not serialized. */
    public boolean suspended()
    {
        return status == Status.SUSPENDED;
    }

    private static String verb( Kind kind )
    {
        return switch ( kind )
        {
            case STEP_OVER   -> "Step over";
            case STEP_INTO   -> "Step into";
            case STEP_RETURN -> "Step return";
            case RESUME      -> "Resume";
        };
    }

    /** No debug session matched - what a caller must never read as "done". */
    public static StepResponse sessionNotFound( Kind kind, String nameOrClass )
    {
        return new StepResponse( Status.SESSION_NOT_FOUND, kind, nameOrClass, null, null, null, 0,
                Diagnostic.none(),
                verb( kind ) + " did nothing: no active debug session matching '" + nameOrClass + "'." );
    }

    /** Nothing is stopped, so there is nothing to step. */
    public static StepResponse noSuspendedThread( Kind kind, String nameOrClass, String launchName )
    {
        return new StepResponse( Status.NO_SUSPENDED_THREAD, kind, nameOrClass, launchName, null, null, 0,
                Diagnostic.none(),
                verb( kind ) + " did nothing: no thread of '" + launchName + "' is suspended." );
    }

    /** A thread was named and the session has no suspended thread by that name. */
    public static StepResponse threadNotFound( Kind kind, String nameOrClass, String launchName, String threadName )
    {
        return new StepResponse( Status.THREAD_NOT_FOUND, kind, nameOrClass, launchName, threadName, null, 0,
                Diagnostic.none(),
                verb( kind ) + " did nothing: no suspended thread named '" + threadName + "' in '"
                        + launchName + "'." );
    }

    /** The program stopped again, and the frame says where. */
    public static StepResponse suspendedAt( Kind kind, String nameOrClass, String launchName, String threadName,
            StackTraceResponse.Frame frame, long waitedMillis )
    {
        String where = frame == null
                ? "an unreadable frame"
                : frame.declaringType() + "." + frame.methodName()
                        + ( frame.lineNumber() > 0 ? " line " + frame.lineNumber() : "" );

        return new StepResponse( Status.SUSPENDED, kind, nameOrClass, launchName, threadName, frame, waitedMillis,
                Diagnostic.none(),
                verb( kind ) + " stopped '" + threadName + "' at " + where + "." );
    }

    /** The caller asked not to wait; the program was left running. */
    public static StepResponse running( Kind kind, String nameOrClass, String launchName, String threadName )
    {
        return new StepResponse( Status.RUNNING, kind, nameOrClass, launchName, threadName, null, 0,
                Diagnostic.none(),
                verb( kind ) + " was issued and not waited for; '" + launchName + "' is running." );
    }

    /** The program ended rather than stopping again - not a failure, an outcome. */
    public static StepResponse terminated( Kind kind, String nameOrClass, String launchName, String threadName,
            long waitedMillis )
    {
        return new StepResponse( Status.TERMINATED, kind, nameOrClass, launchName, threadName, null, waitedMillis,
                Diagnostic.none(), "'" + launchName + "' terminated during the " + verb( kind ).toLowerCase() + "." );
    }

    /** We waited and it never stopped. Retryable: it may still stop later. */
    public static StepResponse timedOut( Kind kind, String nameOrClass, String launchName, String threadName,
            long waitedMillis, Diagnostic diagnostic )
    {
        return new StepResponse( Status.TIMED_OUT, kind, nameOrClass, launchName, threadName, null, waitedMillis,
                List.of( diagnostic ),
                "'" + launchName + "' had not stopped again after " + waitedMillis + " ms; it is still running." );
    }

    /** The debug model refused - the thread cannot step, or the request threw. */
    public static StepResponse failed( Kind kind, String nameOrClass, String launchName, String threadName,
            Diagnostic diagnostic )
    {
        return new StepResponse( Status.FAILED, kind, nameOrClass, launchName, threadName, null, 0,
                List.of( diagnostic ), diagnostic.message() );
    }
}
