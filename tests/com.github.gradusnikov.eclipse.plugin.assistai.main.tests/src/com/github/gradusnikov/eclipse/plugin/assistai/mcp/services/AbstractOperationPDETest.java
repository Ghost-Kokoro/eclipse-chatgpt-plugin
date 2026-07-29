package com.github.gradusnikov.eclipse.plugin.assistai.mcp.services;

import java.util.stream.Collectors;

import org.eclipse.e4.core.contexts.ContextInjectionFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;

import com.github.gradusnikov.eclipse.assistai.mcp.operations.Operation;
import com.github.gradusnikov.eclipse.assistai.mcp.operations.OperationContext;
import com.github.gradusnikov.eclipse.assistai.mcp.operations.OperationOutputBuffer;
import com.github.gradusnikov.eclipse.assistai.mcp.operations.OperationRegistry;

/**
 * Base class for PDE tests that exercise Operation-backed service methods.
 * <p>
 * Provides {@link #initOperationRegistry(IEclipseContext)} to wire up the
 * registry from the subclass setUp, and
 * {@link #runWithOperationConsoleOutputOnProblems} to execute a service call
 * (and its assertions) inside an {@link OperationContext}. If any exception or
 * assertion failure escapes, the full console output captured by the operation
 * is printed to {@code System.out} before the throwable is re-thrown.
 */
public abstract class AbstractOperationPDETest
{
    /**
     * A {@link Runnable}-like that is allowed to throw checked exceptions.
     * Used as the lambda parameter for
     * {@link #runWithOperationConsoleOutputOnProblems}.
     */
    @FunctionalInterface
    protected interface ThrowingRunnable
    {
        void run() throws Exception;
    }

    protected OperationRegistry operationRegistry;

    /**
     * Creates and stores the {@link OperationRegistry} from the given context.
     * Call this once from your {@code @BeforeAll} / {@code @BeforeEach} after
     * building the {@link IEclipseContext}.
     */
    protected void initOperationRegistry( IEclipseContext context )
    {
        operationRegistry = ContextInjectionFactory.make( OperationRegistry.class, context );
    }

    /**
     * Runs {@code body} inside an {@link OperationContext} bound to a freshly
     * registered {@link Operation}. The body should contain both the service
     * call and all assertions.
     * <p>
     * If any exception or assertion failure escapes from {@code body}, the full
     * console output captured by the operation is printed to
     * {@code System.out} before the throwable is re-thrown. This makes it easy
     * to see what a long-running launch (PDE test JVM, Maven build) actually
     * printed when a test fails.
     * <p>
     * Normal test skips ({@code assumeTrue} etc.) still skip — the console is
     * printed before re-throwing, but since the skip is routine the output is
     * usually empty.
     *
     * @param operationName used as both tool name and label for the operation
     * @param body          the service invocation(s) and assertions to run
     */
    protected void runWithOperationAndPrintConsoleOnProblems( String operationName, ThrowingRunnable body )
    {
        Operation op = operationRegistry.register( operationName, operationName );

        // OperationContext.callWith takes a Supplier<T>. Checked exceptions
        // cannot cross a Supplier boundary, so we carry them out manually.
        Exception[] checkedException = { null };
        try
        {
            OperationContext.callWith( op, () -> {
                try
                {
                    body.run();
                }
                catch ( RuntimeException | Error t )
                {
                    throw t;  // propagates directly
                }
                catch ( Exception e )
                {
                    checkedException[0] = e;
                }
                return null;
            } );
        }
        catch ( RuntimeException | Error t )
        {
            printConsoleOutput( op, operationName );
            throw t;
        }

        if ( checkedException[0] != null )
        {
            printConsoleOutput( op, operationName );
            throw new RuntimeException( checkedException[0] );
        }
    }

    private static void printConsoleOutput( Operation op, String operationName )
    {
        OperationOutputBuffer output = op.output();
        int total = output.totalLines();
        if ( total > 0 )
        {
            System.out.println( "=== Console output for '" + operationName + "' ===" );
            System.out.println(
                output.page( 0, total ).lines().stream().collect( Collectors.joining( "\n" ) ) );
            System.out.println( "=== End console output ===" );
        }
    }
}
