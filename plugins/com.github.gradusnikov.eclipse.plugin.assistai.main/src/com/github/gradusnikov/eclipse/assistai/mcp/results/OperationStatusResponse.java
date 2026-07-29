package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

import com.github.gradusnikov.eclipse.assistai.mcp.operations.Operation;

/**
 * The state of a long-running operation, and its result once it has one.
 * <p>
 * Without this, a tool declaring {@code longExecution} could only return structure
 * when it happened to finish inside the inline wait. Past that point its result is
 * collected by polling, and everything the tool computed arrived as prose - which is
 * exactly the case where the caller has been waiting and most needs to branch on the
 * outcome.
 *
 * @param result the payload the tool produced, when it produced one. Typed as Object
 *            because an operation may be wrapping any tool; the schema advertised for
 *            it is correspondingly unconstrained
 */
public record OperationStatusResponse(
    String operationId,
    String toolName,
    String label,
    String state,
    boolean terminal,
    double elapsedSeconds,
    String progress,
    String resultText,
    Object result,
    String failure,
    List<String> availableResultTypes,
    String summaryText
)
{
    /** The reply when the id names no operation the registry still remembers. */
    public static OperationStatusResponse unknown( String operationId )
    {
        return new OperationStatusResponse(
                operationId, null, null, "UNKNOWN", true, 0d, null, "", null, null,
                List.of(), "No operation with id " + operationId + "." );
    }

    public static OperationStatusResponse from( Operation operation )
    {
        if ( operation == null )
        {
            return unknown( null );
        }

        String state = String.valueOf( operation.getState() );
        Throwable failure = operation.getFailure();

        return new OperationStatusResponse(
                operation.getId(),
                operation.getToolName(),
                operation.getLabel(),
                state,
                operation.isTerminal(),
                operation.elapsedSeconds(),
                operation.getProgress(),
                operation.getResultText(),
                operation.getStructuredResult(),
                failure == null ? null : rootMessage( failure ),
                List.copyOf( operation.getIntermediateResults().keySet() ),
                summarize( operation, state ) );
    }

    private static String summarize( Operation operation, String state )
    {
        return operation.getToolName() + " is " + state + " after "
                + String.format( "%.1fs", operation.elapsedSeconds() ) + ".";
    }

    /**
     * Reflection and CompletableFuture bury the real failure under wrappers, so the
     * outermost message is usually "InvocationTargetException" rather than what went
     * wrong.
     */
    private static String rootMessage( Throwable throwable )
    {
        Throwable root = throwable;
        while ( root.getCause() != null && root.getCause() != root )
        {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
