package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * The outcome of a JUnit run.
 * <p>
 * What this replaces is a block of prose - {@code "Summary: Total: 7, Passed: 6, ..."}
 * followed by every test on its own line - which a caller had to parse to answer "did
 * it pass?", and which could not answer "where is the failure?" at all: the old
 * {@code TestResult} carried no stack trace and no source location, so a failing test
 * named a method and nothing else.
 * <p>
 * Three separations matter here:
 * <ul>
 * <li>{@link RunStatus} says whether the run happened, {@link #summary()} says how it
 * went. A run that never started is not a run in which nothing failed.</li>
 * <li>{@link #diagnostics()} is a field, not a substitute for the result. A run that
 * timed out still reports the tests that finished before it did.</li>
 * <li>Passing tests appear only as a count. Their names are the bulk of a large run's
 * payload and the least useful part of it; failures carry everything needed to act.</li>
 * </ul>
 *
 * @param requestedClasses what the caller asked to run, echoed back, because a launch
 *            can quietly run something other than what was named
 * @param summaryText a sentence for direct use in a user-facing answer. It is derived
 *            from the same counts, so it cannot disagree with them
 * @param durationMillis wall clock for the whole operation, launch included - not the
 *            sum of the tests' own times, which excludes JVM and workspace startup and
 *            is usually the smaller number by far
 */
public record TestRunResponse(
    RunStatus status,
    String projectName,
    List<String> requestedClasses,
    TestSummary summary,
    List<TestCaseResult> failedTests,
    List<SkippedTestResult> skippedTests,
    CoverageResult coverage,
    List<Diagnostic> diagnostics,
    String summaryText,
    long durationMillis
)
{
    /**
     * A failure trace longer than this is cut. A single stack overflow trace can run to
     * hundreds of kilobytes, and a run with twenty of them would produce a response no
     * client can use - while the first few frames are what locates the fault.
     */
    public static final int MAX_TRACE_CHARS = 4000;

    /**
     * How the run itself ended.
     * <p>
     * {@code RUNNING} is not decoration: results are published while the run is still
     * going, so {@code getOperationStatus} can show progress, and a vocabulary of
     * terminal states alone cannot describe that snapshot. Terminal status is derived
     * from the counts - see {@link #terminalStatus(TestSummary)} - never set by a caller.
     */
    public enum RunStatus
    {
        RUNNING,
        COMPLETED,
        COMPLETED_WITH_FAILURES,
        FAILED_TO_START,
        TIMED_OUT,
        CANCELLED
    }

    /**
     * How one test case ended.
     * <p>
     * JUnit's own distinction between a failed assertion and an error is kept, because
     * a caller acts differently on the two: one is a wrong answer, the other is code
     * that did not get far enough to give one.
     */
    public enum TestStatus
    {
        PASSED,
        FAILED,
        ERROR,
        SKIPPED,
        /** JDT reported a result this vocabulary does not name. */
        UNKNOWN
    }

    /** Counts of everything that ran, whether or not the listing below mentions it. */
    public record TestSummary(
        int total,
        int passed,
        int failed,
        int errors,
        int skipped
    )
    {
        public static final TestSummary EMPTY = new TestSummary( 0, 0, 0, 0, 0 );

        /** Failed assertions and errors together - what "did it pass?" really asks. */
        public int notPassing()
        {
            return failed + errors;
        }
    }

    /**
     * Where to open a failure.
     *
     * @param projectName the Eclipse project, and {@code filePath} relative to it,
     *            because that is the pair every reading and editing tool takes. A
     *            workspace path would have to be taken apart again by the caller
     * @param line 1-based, taken from the stack frame the JVM reported. Null when no
     *            frame in the trace named the test class - guessing a line is worse
     *            than admitting the trace did not say
     */
    public record SourceLocation(
        String projectName,
        String filePath,
        Integer line
    )
    {
    }

    /**
     * One test that did not pass.
     *
     * @param message the first line of the trace - the assertion message - so a caller
     *            can report the failure without holding the whole trace
     * @param failureTrace the full trace, cut at {@link #MAX_TRACE_CHARS}
     * @param traceTruncated whether the trace above is the whole one
     * @param source null when the trace named no frame in a workspace type
     */
    public record TestCaseResult(
        String className,
        String methodName,
        TestStatus status,
        String message,
        String failureTrace,
        boolean traceTruncated,
        SourceLocation source,
        double durationSeconds
    )
    {
    }

    /**
     * One test that did not run. Deliberately compact: a skipped test has no trace and
     * no location to open, and a run can skip hundreds at a time.
     *
     * @param reason what JUnit gave for skipping - a {@code @Disabled} value or a failed
     *            assumption - or null when it gave none
     */
    public record SkippedTestResult(
        String className,
        String methodName,
        String reason
    )
    {
    }

    /**
     * Coverage, when it was asked for.
     *
     * @param requested whether the caller asked for coverage at all. Without it,
     *            {@code available == false} would be reported for every ordinary run
     *            and read as a problem
     * @param available whether coverage tooling was installed to honour the request
     * @param execFilePath the JaCoCo execution data file, or null when none appeared
     * @param report the analyzer's own rendering. It is opaque text on purpose: it comes
     *            from the coverage tooling, not from this response, and re-deriving it
     *            into fields here would be a second rendering of someone else's data
     */
    public record CoverageResult(
        boolean requested,
        boolean available,
        String execFilePath,
        String report
    )
    {
        /** Coverage was asked for and the tooling to produce it is missing. */
        public static CoverageResult unavailable()
        {
            return new CoverageResult( true, false, null, null );
        }

        public static CoverageResult of( String execFilePath, String report )
        {
            return new CoverageResult( true, true, execFilePath, report );
        }
    }

    /** Whether any test failed or errored. */
    public boolean hasFailures()
    {
        return summary != null && summary.notPassing() > 0;
    }

    /** Whether anything stopped the run from doing what was asked. */
    public boolean hasDiagnostics()
    {
        return diagnostics != null && !diagnostics.isEmpty();
    }

    /**
     * The status a finished run deserves, from its counts alone. Derived rather than
     * passed in, so a caller cannot report a green run that contains failures.
     */
    public static RunStatus terminalStatus( TestSummary summary )
    {
        return summary.notPassing() > 0 ? RunStatus.COMPLETED_WITH_FAILURES : RunStatus.COMPLETED;
    }

    /**
     * A run that never got as far as executing tests: a missing project, a missing
     * class, an unavailable launcher.
     */
    public static TestRunResponse notStarted( String projectName, List<String> requestedClasses,
            Diagnostic diagnostic, long durationMillis )
    {
        return new TestRunResponse( RunStatus.FAILED_TO_START, projectName,
                List.copyOf( requestedClasses ), TestSummary.EMPTY, List.of(), List.of(), null,
                List.of( diagnostic ), diagnostic.message(), durationMillis );
    }

    /**
     * A run that was aborted before the session reported. Counts are empty rather than
     * zero-because-everything-passed, which is what the status is there to distinguish.
     */
    public static TestRunResponse aborted( RunStatus status, String projectName,
            List<String> requestedClasses, Diagnostic diagnostic, long durationMillis )
    {
        return new TestRunResponse( status, projectName, List.copyOf( requestedClasses ),
                TestSummary.EMPTY, List.of(), List.of(), null, List.of( diagnostic ),
                diagnostic.message(), durationMillis );
    }

    /**
     * The one sentence form of the counts. Categories that are zero are left out, so a
     * clean run reads as "12 tests executed: 12 passed." rather than trailing three
     * zeroes a reader has to check.
     */
    public static String describe( RunStatus status, TestSummary summary )
    {
        if ( summary == null || summary.total() == 0 )
        {
            return status == RunStatus.RUNNING
                    ? "Running; no test has finished yet."
                    : "No tests were executed.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append( summary.total() ).append( summary.total() == 1 ? " test " : " tests " )
          .append( status == RunStatus.RUNNING ? "finished so far: " : "executed: " )
          .append( summary.passed() ).append( " passed" );
        if ( summary.failed() > 0 )
        {
            sb.append( ", " ).append( summary.failed() ).append( " failed" );
        }
        if ( summary.errors() > 0 )
        {
            sb.append( ", " ).append( summary.errors() )
              .append( summary.errors() == 1 ? " error" : " errors" );
        }
        if ( summary.skipped() > 0 )
        {
            sb.append( ", " ).append( summary.skipped() ).append( " skipped" );
        }
        return sb.append( "." ).toString();
    }

    /**
     * Cuts a trace to {@link #MAX_TRACE_CHARS}, keeping the head - the exception and the
     * frames nearest the fault - because that is the part that locates it.
     *
     * @return the trace and whether it was cut, or {@code {null, false}} for no trace
     */
    public static String truncateTrace( String trace )
    {
        if ( trace == null || trace.isEmpty() )
        {
            return null;
        }
        return trace.length() <= MAX_TRACE_CHARS ? trace : trace.substring( 0, MAX_TRACE_CHARS );
    }

    /** Whether {@link #truncateTrace(String)} would cut this trace. */
    public static boolean isTraceTruncated( String trace )
    {
        return trace != null && trace.length() > MAX_TRACE_CHARS;
    }

    /**
     * The assertion message - the first non-blank line of the trace. A caller reporting
     * "1 failed: expected 201 but got 500" should not have to hold 4 kB to say it.
     */
    public static String firstTraceLine( String trace )
    {
        if ( trace == null || trace.isBlank() )
        {
            return null;
        }
        for ( String line : trace.split( "\\R" ) )
        {
            String trimmed = line.trim();
            if ( !trimmed.isEmpty() )
            {
                return trimmed;
            }
        }
        return null;
    }
}
