package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * The outcome of launching a Java application or a saved launch configuration.
 * <p>
 * The exit code - the one fact that says whether the program worked - used to sit inside
 * a text blob that also contained arbitrary program stdout, which may itself contain the
 * line {@code Exit code: 0}. A caller reading the exit code out of that blob could not
 * tell the tool's own line from the program's. It is now a field, and the program's two
 * streams are separate fields beside it.
 * <p>
 * {@code exitCode} is null when there is none to report - the process is still running,
 * or the debug plug-in never recorded one. It is never {@code -1}: a sentinel exit code
 * is indistinguishable from a program that really exited {@code -1}.
 *
 * @param launchName the configuration that was launched. For {@code runJavaApplication}
 *            and {@code debugJavaApplication} this is the throwaway configuration the
 *            tool built, which is a fact worth having when reading the Eclipse console
 * @param mode {@code "run"} or {@code "debug"}
 * @param projectName the project the launch names, or null when it names none
 * @param mainClass the main type the launch names, or null when it names none
 * @param pid the operating system process id, or null when the debug plug-in recorded
 *            none. A background launch is stopped by name, but a pid is what survives
 *            Eclipse forgetting about the process
 * @param exitCode the first process's exit value, or null when there is none to report
 * @param timedOut whether the wait ran out with the process still running. Distinct from
 *            an exit code that happens to be missing
 * @param durationMillis wall clock spent in this call, launch included
 * @param stdout what the program wrote to standard output while this call watched, cut
 *            at {@link #MAX_STDOUT_CHARS}
 * @param stderr the same for standard error, cut at {@link #MAX_STDERR_CHARS}
 */
public record LaunchResponse(
    Status status,
    String launchName,
    String mode,
    String projectName,
    String mainClass,
    Long pid,
    Integer exitCode,
    boolean timedOut,
    long durationMillis,
    ProcessOutput stdout,
    ProcessOutput stderr,
    List<Diagnostic> diagnostics,
    String summaryText
)
{
    /** Standard output is where a program says what it did; it gets the larger budget. */
    public static final int MAX_STDOUT_CHARS = 5000;

    /** Standard error is usually one stack trace, and the head of it locates the fault. */
    public static final int MAX_STDERR_CHARS = 2000;

    /** Whether the launch got as far as running, and if not, whether it is still going. */
    public enum Status
    {
        /** The process exists and had not exited when this call stopped watching. */
        RUNNING,
        /** The process exited. {@link #exitCode()} says how, when the VM reported it. */
        COMPLETED,
        /** Nothing was launched - see diagnostics. */
        FAILED_TO_START
    }

    /**
     * One captured stream.
     *
     * @param text the stream as captured, cut to the budget above
     * @param truncated whether {@code text} is the whole of it
     * @param totalChars how much the program wrote in total, before the cut - so a
     *            caller knows the size of what it is not seeing
     */
    public record ProcessOutput(
        String text,
        boolean truncated,
        int totalChars
    )
    {
        public static final ProcessOutput EMPTY = new ProcessOutput( "", false, 0 );

        /** Cuts a stream to {@code maxChars}, keeping the head. */
        public static ProcessOutput of( String text, int maxChars )
        {
            if ( text == null || text.isEmpty() )
            {
                return EMPTY;
            }
            return text.length() <= maxChars
                    ? new ProcessOutput( text, false, text.length() )
                    : new ProcessOutput( text.substring( 0, maxChars ), true, text.length() );
        }
    }

    /** Whether the program ran to completion and said it succeeded. Not serialized. */
    public boolean succeeded()
    {
        return status == Status.COMPLETED && exitCode != null && exitCode == 0;
    }

    /** Nothing was launched: a missing project, a missing main class, a missing configuration. */
    public static LaunchResponse failedToStart( String launchName, String mode, String projectName, String mainClass,
            Diagnostic diagnostic )
    {
        return new LaunchResponse( Status.FAILED_TO_START, launchName, mode, projectName, mainClass, null, null,
                false, 0, ProcessOutput.EMPTY, ProcessOutput.EMPTY, List.of( diagnostic ), diagnostic.message() );
    }

    /** The caller asked not to wait, or the wait ran out. */
    public static LaunchResponse running( String launchName, String mode, String projectName, String mainClass,
            Long pid, boolean timedOut, long durationMillis, ProcessOutput stdout, ProcessOutput stderr )
    {
        String summary = timedOut
                ? "'" + launchName + "' was still running after " + ( durationMillis / 1000 )
                        + "s; use stopApplication to end it or getConsoleOutput to follow it."
                : "'" + launchName + "' launched in " + mode
                        + " mode and left running; use stopApplication to end it or getConsoleOutput to follow it.";

        return new LaunchResponse( Status.RUNNING, launchName, mode, projectName, mainClass, pid, null,
                timedOut, durationMillis, stdout, stderr, Diagnostic.none(), summary );
    }

    /** The process exited. */
    public static LaunchResponse completed( String launchName, String mode, String projectName, String mainClass,
            Long pid, Integer exitCode, long durationMillis, ProcessOutput stdout, ProcessOutput stderr )
    {
        String summary = exitCode == null
                ? "'" + launchName + "' exited; the VM reported no exit code."
                : "'" + launchName + "' exited with code " + exitCode + ".";

        return new LaunchResponse( Status.COMPLETED, launchName, mode, projectName, mainClass, pid, exitCode,
                false, durationMillis, stdout, stderr, Diagnostic.none(), summary );
    }
}
