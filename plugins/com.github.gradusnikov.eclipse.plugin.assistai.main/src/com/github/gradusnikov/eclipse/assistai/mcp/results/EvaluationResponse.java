package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * The result of evaluating an expression in a suspended debug frame.
 * <p>
 * What this replaces put a successful evaluation and a compile error in the expression
 * into the same field of the same sentence - {@code "Expression: x\nResult: …"} - so a
 * caller pretty-printing the answer presented a compile failure as the value. Value and
 * type were joined as {@code value + " (" + type + ")"}, which is not invertible for any
 * object whose {@code toString()} contains a parenthesis, which is most of them.
 * <p>
 * Three separations matter:
 * <ul>
 * <li>{@link Status} says whether there is a value at all. {@code OK} and
 * {@code COMPILE_ERROR} are different answers to different questions, not two
 * renderings of one.</li>
 * <li>{@link #value()} and {@link #declaredType()} are separate fields, so neither has
 * to be recovered by splitting a string on a delimiter that occurs in both.</li>
 * <li>{@link #threadName()} and {@link #frame()} name the context the expression was
 * evaluated in. The old tool silently used {@code frames[0]} of the first suspended
 * thread it found and never said which, so the same expression could legitimately give
 * two answers with nothing to tell them apart.</li>
 * </ul>
 *
 * @param nameOrClass the filter the caller passed, echoed back
 * @param expression the expression as submitted, echoed back, because a caller batching
 *            evaluations has to match answers to questions
 * @param launchName the debug session the expression ran in, null when none matched
 * @param threadName the suspended thread whose frame was used, null when none was
 * @param frame the frame the expression was evaluated in - the same shape
 *            {@code getStackTrace} returns, so the caller can open it. Null when no
 *            frame was reached
 * @param value the debug model's rendering of the result, null when the evaluation
 *            produced no value - a failure, or an expression of type {@code void}
 * @param declaredType the runtime type of the result, for example
 *            {@code java.util.ArrayList} or {@code int}. Null when there is no value
 * @param nullResult whether the expression evaluated to the Java {@code null} reference.
 *            It is a separate field because {@code value} renders that as the four
 *            characters {@code null}, which is also how a {@code String} holding those
 *            characters renders
 * @param errorMessages the compiler's own messages when the expression did not compile.
 *            They are the actionable content of a {@code COMPILE_ERROR} and belong to
 *            the caller's expression, not to the tool, which is why they are not
 *            diagnostics
 */
public record EvaluationResponse(
    Status status,
    String nameOrClass,
    String expression,
    String launchName,
    String threadName,
    StackTraceResponse.Frame frame,
    String value,
    String declaredType,
    boolean nullResult,
    List<String> errorMessages,
    List<Diagnostic> diagnostics,
    String summaryText
)
{
    /** How the evaluation ended. Every member is a different thing for a caller to do. */
    public enum Status
    {
        /** The expression compiled, ran, and produced a value. */
        OK,
        /** The expression did not compile - see {@link #errorMessages()}. Fix the expression. */
        COMPILE_ERROR,
        /** The expression compiled but threw, or the VM refused to run it. */
        EVALUATION_FAILED,
        /** The VM did not answer in time. The session is still there; try again. */
        TIMED_OUT,
        /** The session matched but nothing is stopped, so there is no frame to evaluate in. */
        NO_SUSPENDED_THREAD,
        /** A thread name was given and no suspended thread of the session has it. */
        THREAD_NOT_FOUND,
        /** No debug session matched the filter. A state, not an error. */
        SESSION_NOT_FOUND
    }

    /** Whether there is a value to read. Derived, and not serialized. */
    public boolean hasValue()
    {
        return status == Status.OK;
    }

    /** No debug session matched - the caller has the wrong filter, or nothing is running. */
    public static EvaluationResponse sessionNotFound( String nameOrClass, String expression )
    {
        return new EvaluationResponse( Status.SESSION_NOT_FOUND, nameOrClass, expression, null, null, null,
                null, null, false, List.of(), Diagnostic.none(),
                "No active debug session matching '" + nameOrClass + "'." );
    }

    /** The session is there but running, so there is no frame to evaluate in. */
    public static EvaluationResponse noSuspendedThread( String nameOrClass, String expression, String launchName )
    {
        return new EvaluationResponse( Status.NO_SUSPENDED_THREAD, nameOrClass, expression, launchName, null, null,
                null, null, false, List.of(), Diagnostic.none(),
                "No thread of '" + launchName + "' is suspended; an expression needs a stopped frame." );
    }

    /** A thread was named and the session has no suspended thread by that name. */
    public static EvaluationResponse threadNotFound( String nameOrClass, String expression, String launchName,
            String threadName )
    {
        return new EvaluationResponse( Status.THREAD_NOT_FOUND, nameOrClass, expression, launchName, threadName, null,
                null, null, false, List.of(), Diagnostic.none(),
                "No suspended thread named '" + threadName + "' in '" + launchName + "'." );
    }

    /** The expression did not compile. The messages say what is wrong with it. */
    public static EvaluationResponse compileError( String nameOrClass, String expression, String launchName,
            String threadName, StackTraceResponse.Frame frame, List<String> errorMessages )
    {
        return new EvaluationResponse( Status.COMPILE_ERROR, nameOrClass, expression, launchName, threadName, frame,
                null, null, false, List.copyOf( errorMessages ), Diagnostic.none(),
                "The expression did not compile: " + String.join( "; ", errorMessages ) );
    }

    /** The expression compiled but running it failed. */
    public static EvaluationResponse failed( String nameOrClass, String expression, String launchName,
            String threadName, StackTraceResponse.Frame frame, Diagnostic diagnostic )
    {
        return new EvaluationResponse( Status.EVALUATION_FAILED, nameOrClass, expression, launchName, threadName, frame,
                null, null, false, List.of(), List.of( diagnostic ), diagnostic.message() );
    }

    /** The VM did not answer within the wait. Retryable: the session is still there. */
    public static EvaluationResponse timedOut( String nameOrClass, String expression, String launchName,
            String threadName, StackTraceResponse.Frame frame, long waitedMillis, Diagnostic diagnostic )
    {
        return new EvaluationResponse( Status.TIMED_OUT, nameOrClass, expression, launchName, threadName, frame,
                null, null, false, List.of(), List.of( diagnostic ),
                "The evaluation did not finish within " + waitedMillis + " ms." );
    }

    /** A value came back. */
    public static EvaluationResponse of( String nameOrClass, String expression, String launchName, String threadName,
            StackTraceResponse.Frame frame, String value, String declaredType, boolean nullResult )
    {
        String rendered = nullResult ? "null" : value;
        String summary = declaredType == null
                ? expression + " = " + rendered
                : expression + " = " + rendered + ", of type " + declaredType;

        return new EvaluationResponse( Status.OK, nameOrClass, expression, launchName, threadName, frame,
                value, declaredType, nullResult, List.of(), Diagnostic.none(), summary );
    }
}
