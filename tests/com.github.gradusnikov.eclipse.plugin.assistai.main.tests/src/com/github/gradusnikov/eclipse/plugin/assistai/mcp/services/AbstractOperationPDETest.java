package com.github.gradusnikov.eclipse.plugin.assistai.mcp.services;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.Callable;
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
 * registry from the subclass setUp, and {@link #runWithOperation} to execute a
 * service call inside an {@link OperationContext} and fail with the captured
 * console output when the result signals an error.
 */
public abstract class AbstractOperationPDETest
{
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
     * Runs {@code call} inside an {@link OperationContext} bound to a freshly
     * registered {@link Operation}.
     * <p>
     * If the returned string starts with {@code "Error"} the test is failed
     * immediately and the full console output captured by the operation is
     * appended to the failure message, making it easy to diagnose why a
     * long-running tool returned an error.
     *
     * @param operationName used as both tool name and label for the operation
     * @param call          the service invocation to run
     * @return the result string returned by the service call
     * @throws Exception if the callable throws a checked exception
     */
    protected String runWithOperation( String operationName, Callable<String> call ) throws Exception
    {
        Operation op = operationRegistry.register( operationName, operationName );

        // OperationContext.callWith takes a Supplier, so we must wrap any
        // checked exception as an unchecked one and re-throw it afterwards.
        Throwable[] checkedException = { null };
        String result = OperationContext.callWith( op, () -> {
            try
            {
                return call.call();
            }
            catch ( RuntimeException | Error e )
            {
                throw e;
            }
            catch ( Exception e )
            {
                checkedException[0] = e;
                return "Error: " + e.getMessage();
            }
        } );

        if ( checkedException[0] instanceof Exception ce )
        {
            throw ce;
        }

        if ( result != null && result.startsWith( "Error" ) )
        {
            OperationOutputBuffer output = op.output();
            fail( "'" + operationName + "' returned error: " + result
                + "\n\nConsole output:\n" + output.page( 0, output.totalLines() ).lines().stream().collect(Collectors.joining("\n")) );
        }

        return result;
    }
}
