package com.github.gradusnikov.eclipse.plugin.assistai.mcp.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.ILogListener;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.e4.core.contexts.ContextInjectionFactory;
import org.eclipse.e4.core.contexts.EclipseContextFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.ui.di.UISynchronize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;

import com.github.gradusnikov.eclipse.assistai.mcp.McpJson;
import com.github.gradusnikov.eclipse.assistai.mcp.McpOutputSchemas;
import com.github.gradusnikov.eclipse.assistai.mcp.StructuredToolResult;
import com.github.gradusnikov.eclipse.assistai.mcp.results.ActiveTargetResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.ActiveTargetResponse.TargetStatus;
import com.github.gradusnikov.eclipse.assistai.mcp.results.Diagnostic;
import com.github.gradusnikov.eclipse.assistai.mcp.results.DiagnosticCode;
import com.github.gradusnikov.eclipse.assistai.mcp.results.TestRunResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.TestRunResponse.RunStatus;
import com.github.gradusnikov.eclipse.assistai.mcp.servers.PDEMcpServer;

/**
 * Tests for PDEMcpServer - focuses on parameter handling and delegation to PDEService.
 * Tests that require a live PDE runtime are skipped via {@code assumeTrue}.
 */
public class McpServerPDETest
{
    private PDEMcpServer server;

    @BeforeEach
    public void setUp()
    {
        IEclipseContext context = EclipseContextFactory.create();

        context.set( ILog.class, new ILog()
        {
            @Override
            public void removeLogListener( ILogListener listener ) {}

            @Override
            public void log( IStatus status )
            {
                System.out.println( status.getMessage() );
                if ( status.getException() != null )
                {
                    status.getException().printStackTrace();
                }
            }

            @Override
            public Bundle getBundle() { return null; }

            @Override
            public void addLogListener( ILogListener listener ) {}
        } );

        context.set( UISynchronize.class, new UISynchronize()
        {
            @Override
            public void syncExec( Runnable runnable ) { runnable.run(); }

            @Override
            public void asyncExec( Runnable runnable ) { runnable.run(); }

            @Override
            protected boolean isUIThread( Thread thread ) { return true; }

            @Override
            protected void showBusyWhile( Runnable runnable ) { runnable.run(); }

            @Override
            protected boolean dispatchEvents() { return false; }
        } );

        server = ContextInjectionFactory.make( PDEMcpServer.class, context );
    }

    @Test
    public void testGetActiveTarget_describesTheTargetInForce()
    {
        ActiveTargetResponse response;
        try
        {
            response = server.getActiveTarget();
        }
        catch ( Exception e )
        {
            assumeTrue( false, "Skipping: PDE runtime not available (" + e.getMessage() + ")" );
            return;
        }

        assertNotNull( response, "getActiveTarget should not return null" );
        assertNotEquals( TargetStatus.FAILED, response.status(),
            () -> String.valueOf( response.diagnostics() ) );
        assertActiveTargetIsSelfConsistent( response );
    }

    @Test
    public void testReloadTarget_noExplicitTargetIsNotAFailure()
    {
        ActiveTargetResponse response;
        try
        {
            response = server.reloadTarget();
        }
        catch ( Exception e )
        {
            assumeTrue( false, "Skipping: PDE runtime not available (" + e.getMessage() + ")" );
            return;
        }

        assertNotNull( response, "reloadTarget should not return null" );
        // The point of the conversion: reload used to call "no target file is set" an
        // error while getActiveTarget called the identical state normal. Whatever this
        // workspace is, the two now agree on how to describe it.
        assertEquals( server.getActiveTarget().explicitTarget(), response.explicitTarget() );
        assertActiveTargetIsSelfConsistent( response );
    }

    /**
     * A count of zero would say the target resolved to nothing, which is a far worse
     * situation than one that has not been resolved yet - so an unresolved target has no
     * count at all.
     */
    private static void assertActiveTargetIsSelfConsistent( ActiveTargetResponse response )
    {
        if ( !response.resolved() )
        {
            assertNull( response.bundleCount(),
                "bundleCount must be absent rather than 0 when the target is not resolved" );
        }
        if ( !response.explicitTarget() )
        {
            assertNull( response.name(), "the running platform is not a named target" );
            assertNull( response.memento() );
        }
    }

    @Test
    public void anActiveTargetSendsExactlyTheFieldsItAdvertises()
    {
        // The schema comes from the record components and the payload from Jackson; a
        // derived accessor - isUsable() here - would otherwise be serialized as a field
        // the schema never mentioned.
        @SuppressWarnings( "unchecked" )
        Map<String, Object> advertised = (Map<String, Object>) McpOutputSchemas
            .forType( ActiveTargetResponse.class ).get( "properties" );

        assertEquals( advertised.keySet(),
            McpJson.toMap( ActiveTargetResponse.active( "a-target", "memento", true, true, 412 ) ).keySet() );
        assertEquals( advertised.keySet(),
            McpJson.toMap( ActiveTargetResponse.runningPlatform() ).keySet(),
            "every state of the response has to have the same shape" );
    }

    @Test
    public void aFailedLoadStillSaysWhatIsInForce()
    {
        ActiveTargetResponse loaded = ActiveTargetResponse.active( "a-target", "memento", true, true, 412 );
        ActiveTargetResponse failed = loaded.withFailure(
            Diagnostic.retryable( DiagnosticCode.OPERATION_TIMED_OUT, "took too long" ) );

        assertEquals( TargetStatus.FAILED, failed.status() );
        assertEquals( List.of( DiagnosticCode.OPERATION_TIMED_OUT ),
            failed.diagnostics().stream().map( Diagnostic::code ).toList() );
        assertEquals( "a-target", failed.name(), "the target that is still active is the caller's next question" );
        assertEquals( Integer.valueOf( 412 ), failed.bundleCount() );
    }

    @Test
    public void anUnresolvedTargetHasNoBundleCount()
    {
        assertNull( ActiveTargetResponse.active( "a-target", "memento", true, false, 0 ).bundleCount() );
        assertNull( ActiveTargetResponse.runningPlatform().bundleCount() );
    }

    // -----------------------------------------------------------------------
    // runJUnitPluginTests — unified entry point
    // -----------------------------------------------------------------------

    @Test
    public void testStartJUnitPluginTestRun_allTests_nullTimeout_usesDefault()
    {
        try
        {
            // no className/packageName → runs all tests in project
            assertProjectNotFound( server.runJUnitPluginTests(
                "NonExistentProject_XYZ", null, null, null, null, null, null, null ) );
        }
        catch ( IllegalStateException e )
        {
            assumeTrue( false, "Skipping: workspace not available (" + e.getMessage() + ")" );
        }
    }

    @Test
    public void testStartJUnitPluginTestRun_allTests_explicitTimeout_parsed()
    {
        try
        {
            assertProjectNotFound( server.runJUnitPluginTests(
                "NonExistentProject_XYZ", null, null, "30", null, null, null, null ) );
        }
        catch ( IllegalStateException e )
        {
            assumeTrue( false, "Skipping: workspace not available (" + e.getMessage() + ")" );
        }
    }

    @Test
    public void testStartJUnitPluginTestRun_includeAllPluginsTrue()
    {
        try
        {
            assertProjectNotFound( server.runJUnitPluginTests(
                "NonExistentProject_XYZ", null, null, "10", null, "true", null, null ) );
        }
        catch ( IllegalStateException e )
        {
            assumeTrue( false, "Skipping: workspace not available (" + e.getMessage() + ")" );
        }
    }

    @Test
    public void testStartJUnitPluginTestRun_includeAllPluginsFalse()
    {
        try
        {
            assertProjectNotFound( server.runJUnitPluginTests(
                "NonExistentProject_XYZ", null, null, "10", null, "false", null, null ) );
        }
        catch ( IllegalStateException e )
        {
            assumeTrue( false, "Skipping: workspace not available (" + e.getMessage() + ")" );
        }
    }

    @Test
    public void testStartJUnitPluginTestRun_withAdditionalBundles()
    {
        try
        {
            assertProjectNotFound( server.runJUnitPluginTests(
                "NonExistentProject_XYZ", null, null, "10", null, "false",
                "org.eclipse.core.runtime,org.eclipse.ui", null ) );
        }
        catch ( IllegalStateException e )
        {
            assumeTrue( false, "Skipping: workspace not available (" + e.getMessage() + ")" );
        }
    }

    @Test
    public void testStartJUnitPluginTestRun_singleClass_nullTimeout()
    {
        try
        {
            assertProjectNotFound( server.runJUnitPluginTests(
                "NonExistentProject_XYZ", "com.example.MyTest", null, null, null, null, null, null ) );
        }
        catch ( IllegalStateException e )
        {
            assumeTrue( false, "Skipping: workspace not available (" + e.getMessage() + ")" );
        }
    }

    @Test
    public void testStartJUnitPluginTestRun_singleClass_includeAllPluginsTrue()
    {
        try
        {
            assertProjectNotFound( server.runJUnitPluginTests(
                "NonExistentProject_XYZ", "com.example.MyTest", null, "10", null, "true", null, null ) );
        }
        catch ( IllegalStateException e )
        {
            assumeTrue( false, "Skipping: workspace not available (" + e.getMessage() + ")" );
        }
    }

    @Test
    public void testStartJUnitPluginTestRun_singleClass_withAdditionalBundles()
    {
        try
        {
            assertProjectNotFound( server.runJUnitPluginTests(
                "NonExistentProject_XYZ", "com.example.MyTest", null, "10", null, "false",
                "org.eclipse.core.runtime, org.eclipse.ui", null ) );
        }
        catch ( IllegalStateException e )
        {
            assumeTrue( false, "Skipping: workspace not available (" + e.getMessage() + ")" );
        }
    }

    @Test
    public void testStartJUnitPluginTestRun_multipleClasses_emptySelection_isRejected()
    {
        // comma-only className → parseCommaSeparated returns empty list → rejected by PDEService
        assertThrows( IllegalArgumentException.class,
            () -> server.runJUnitPluginTests(
                "SomeProject", " , ", null, null, null, null, null, null ) );
    }

    @Test
    public void testStartJUnitPluginTestRun_multipleClasses_areAccepted()
    {
        assertProjectNotFound( server.runJUnitPluginTests(
            "NonExistentProject_XYZ",
            " com.example.FirstPDETest, com.example.SecondPDETest ",
            null, "10", null, "false", "org.eclipse.ui, org.eclipse.core.runtime", null ) );
    }

    @Test
    public void testStartJUnitPluginTestRun_packageScope()
    {
        try
        {
            assertProjectNotFound( server.runJUnitPluginTests(
                "NonExistentProject_XYZ", null, "com.example.tests", "10", null, null, null, null ) );
        }
        catch ( IllegalStateException e )
        {
            assumeTrue( false, "Skipping: workspace not available (" + e.getMessage() + ")" );
        }
    }

    /**
     * Whatever scope was asked for, naming a project that is not in the workspace is
     * reported as a diagnostic code on a structured result - not as a sentence starting
     * with "Error", which a caller had to match on to notice.
     */
    private static void assertProjectNotFound( TestRunResponse response )
    {
        assertNotNull( response );
        assertEquals( RunStatus.FAILED_TO_START, response.status(), response.summaryText() );
        assertEquals( List.of( DiagnosticCode.PROJECT_NOT_FOUND ),
            response.diagnostics().stream().map( Diagnostic::code ).toList() );
    }
}
