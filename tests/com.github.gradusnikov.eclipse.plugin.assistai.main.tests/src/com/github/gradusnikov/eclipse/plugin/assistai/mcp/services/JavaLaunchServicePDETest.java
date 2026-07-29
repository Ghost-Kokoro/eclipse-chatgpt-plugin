package com.github.gradusnikov.eclipse.plugin.assistai.mcp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.e4.core.contexts.ContextInjectionFactory;
import org.eclipse.e4.core.contexts.EclipseContextFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.util.tracker.ServiceTracker;

import java.util.List;

import com.github.gradusnikov.eclipse.assistai.mcp.McpJson;
import com.github.gradusnikov.eclipse.assistai.mcp.results.LaunchConfigurationsResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.services.JavaLaunchService;

/**
 * Plug-in tests for {@link JavaLaunchService#listLaunchConfigurations(String)}.
 *
 * <p>A temporary JUnit and a temporary Java Application launch configuration
 * are created in {@code @BeforeAll} and removed in {@code @AfterAll}, so the
 * tests run in a predictable environment regardless of what is already saved
 * in the workspace.
 */
@TestInstance( TestInstance.Lifecycle.PER_CLASS )
@TestMethodOrder( MethodOrderer.OrderAnnotation.class )
public class JavaLaunchServicePDETest
{
    private static final String JUNIT_CONFIG_NAME  = "AssistAITest_JUnitConfig";
    private static final String JAVA_CONFIG_NAME   = "AssistAITest_JavaAppConfig";
    private static final String PDE_CONFIG_NAME    = "AssistAITest_PDEJUnitConfig";

    private static final String JUNIT_TYPE_ID      = "org.eclipse.jdt.junit.launchconfig";
    private static final String JAVA_TYPE_ID       = "org.eclipse.jdt.launching.localJavaApplication";
    private static final String PDE_JUNIT_TYPE_ID  = "org.eclipse.pde.ui.JunitLaunchConfig";

    private JavaLaunchService service;
    private ILaunchConfiguration junitConfig;
    private ILaunchConfiguration javaConfig;
    private ILaunchConfiguration pdeConfig;     // may be null if PDE not available

    @BeforeAll
    public void setUp() throws Exception
    {
        BundleContext bundleContext = FrameworkUtil.getBundle( JavaLaunchServicePDETest.class ).getBundleContext();

        ServiceTracker<ILog, ILog> logTracker = new ServiceTracker<>( bundleContext, ILog.class, null );
        logTracker.open();
        ILog log = logTracker.getService();

        IEclipseContext context = EclipseContextFactory.getServiceContext( bundleContext );
        context.set( UISynchronize.class, new UISynchronize()
        {
            @Override public void syncExec( Runnable r ) { r.run(); }
            @Override public void asyncExec( Runnable r ) { r.run(); }
            @Override protected boolean isUIThread( Thread t ) { return false; }
            @Override protected void showBusyWhile( Runnable r ) { r.run(); }
            @Override protected boolean dispatchEvents() { return false; }
        } );
        if ( log != null ) context.set( ILog.class, log );

        service = ContextInjectionFactory.make( JavaLaunchService.class, context );

        ILaunchManager lm = DebugPlugin.getDefault().getLaunchManager();

        // Create a plain JUnit launch config
        ILaunchConfigurationType junitType = lm.getLaunchConfigurationType( JUNIT_TYPE_ID );
        if ( junitType != null )
        {
            ILaunchConfigurationWorkingCopy wc = junitType.newInstance( null, JUNIT_CONFIG_NAME );
            wc.setAttribute( IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, "SomeTestProject" );
            wc.setAttribute( IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, "com.example.MyTest" );
            junitConfig = wc.doSave();
        }

        // Create a Java Application launch config
        ILaunchConfigurationType javaType = lm.getLaunchConfigurationType( JAVA_TYPE_ID );
        if ( javaType != null )
        {
            ILaunchConfigurationWorkingCopy wc = javaType.newInstance( null, JAVA_CONFIG_NAME );
            wc.setAttribute( IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, "SomeProject" );
            wc.setAttribute( IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, "com.example.Main" );
            javaConfig = wc.doSave();
        }

        // Create a PDE JUnit launch config (may not be available in all environments)
        ILaunchConfigurationType pdeType = lm.getLaunchConfigurationType( PDE_JUNIT_TYPE_ID );
        if ( pdeType != null )
        {
            ILaunchConfigurationWorkingCopy wc = pdeType.newInstance( null, PDE_CONFIG_NAME );
            wc.setAttribute( IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, "SomePluginProject" );
            wc.setAttribute( IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, "com.example.MyPluginTest" );
            pdeConfig = wc.doSave();
        }
    }

    @AfterAll
    public void tearDown()
    {
        deleteQuietly( junitConfig );
        deleteQuietly( javaConfig );
        deleteQuietly( pdeConfig );
    }

    private void deleteQuietly( ILaunchConfiguration config )
    {
        if ( config == null ) return;
        try { config.delete(); } catch ( CoreException ignored ) {}
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<String> configurationNames( String typeFilter )
    {
        return service.listLaunchConfigurations( typeFilter ).configurations().stream()
                .map( LaunchConfigurationsResponse.LaunchConfigurationInfo::name )
                .toList();
    }

    private List<String> typeIds( String typeFilter )
    {
        return service.listLaunchConfigurations( typeFilter ).configurations().stream()
                .map( LaunchConfigurationsResponse.LaunchConfigurationInfo::typeId )
                .toList();
    }

    // -------------------------------------------------------------------------
    // Shape of the result
    // -------------------------------------------------------------------------

    @Test
    @Order( 1 )
    public void testListAll_reportsCountMatchingTheListing()
    {
        LaunchConfigurationsResponse result = service.listLaunchConfigurations( null );

        assertNotNull( result );
        assertEquals( result.configurations().size(), result.totalConfigurations() );
    }

    @Test
    @Order( 2 )
    public void testListAll_emptyFilter_sameAsNull()
    {
        List<String> withNull = configurationNames( null );

        assertEquals( withNull, configurationNames( "" ), "null and empty filter should agree" );
        assertEquals( withNull, configurationNames( "all" ), "null and 'all' filter should agree" );
    }

    @Test
    @Order( 3 )
    public void testListAll_populatesTheFieldsACallerActsOn()
    {
        if ( junitConfig == null ) return;   // type not available in this environment

        var found = service.listLaunchConfigurations( null ).configurations().stream()
                .filter( config -> JUNIT_CONFIG_NAME.equals( config.name() ) )
                .findFirst()
                .orElseThrow( () -> new AssertionError( "our JUnit config was not listed" ) );

        assertNotNull( found.typeId() );
        assertNotNull( found.typeName() );
        assertNotNull( found.projectName() );
        assertNotNull( found.mainClass() );
    }

    // -------------------------------------------------------------------------
    // Filtering - junit
    // -------------------------------------------------------------------------

    @Test
    @Order( 10 )
    public void testFilterJunit_containsOurJUnitConfig()
    {
        if ( junitConfig == null ) return;

        assertTrue( configurationNames( "junit" ).contains( JUNIT_CONFIG_NAME ) );
    }

    @Test
    @Order( 11 )
    public void testFilterJunit_doesNotContainJavaAppConfig()
    {
        if ( javaConfig == null ) return;

        assertFalse( configurationNames( "junit" ).contains( JAVA_CONFIG_NAME ) );
    }

    @Test
    @Order( 12 )
    public void testFilterJunit_excludesThePdeType()
    {
        if ( junitConfig == null ) return;

        // Every listed configuration must be of the plain JUnit type, which the
        // string form could only approximate by searching for an absent substring.
        assertFalse( typeIds( "junit" ).contains( PDE_JUNIT_TYPE_ID ) );
    }

    // -------------------------------------------------------------------------
    // Filtering - junit-plugin
    // -------------------------------------------------------------------------

    @Test
    @Order( 20 )
    public void testFilterJunitPlugin_containsOurPDEConfig()
    {
        if ( pdeConfig == null ) return;

        assertTrue( configurationNames( "junit-plugin" ).contains( PDE_CONFIG_NAME ) );
    }

    @Test
    @Order( 21 )
    public void testFilterJunitPlugin_doesNotContainPlainJUnitConfig()
    {
        if ( junitConfig == null || pdeConfig == null ) return;

        assertFalse( configurationNames( "junit-plugin" ).contains( JUNIT_CONFIG_NAME ) );
    }

    // -------------------------------------------------------------------------
    // Filtering - all
    // -------------------------------------------------------------------------

    @Test
    @Order( 30 )
    public void testFilterAll_containsJUnitAndJavaApp()
    {
        if ( junitConfig == null || javaConfig == null ) return;

        List<String> names = configurationNames( "all" );
        assertTrue( names.contains( JUNIT_CONFIG_NAME ) );
        assertTrue( names.contains( JAVA_CONFIG_NAME ) );
    }

    // -------------------------------------------------------------------------
    // Filtering - unknown type
    // -------------------------------------------------------------------------

    @Test
    @Order( 40 )
    public void testFilterUnknown_returnsNoConfigurations()
    {
        LaunchConfigurationsResponse result =
                service.listLaunchConfigurations( "completely-unknown-type-xyz-12345" );

        assertEquals( 0, result.totalConfigurations() );
        assertTrue( result.configurations().isEmpty() );
        // The filter is echoed, so an empty result from a filter stays distinguishable
        // from an empty workspace.
        assertEquals( "completely-unknown-type-xyz-12345", result.typeFilter() );
    }

    // -------------------------------------------------------------------------
    // Filtering - substring of type ID
    // -------------------------------------------------------------------------

    @Test
    @Order( 50 )
    public void testFilterSubstring_localJava_matchesJavaApp()
    {
        if ( javaConfig == null ) return;

        // "localJava" is a substring of "org.eclipse.jdt.launching.localJavaApplication"
        assertTrue( configurationNames( "localJava" ).contains( JAVA_CONFIG_NAME ) );
    }

    @Test
    @Order( 51 )
    public void testFilterSubstring_localJava_doesNotContainJUnit()
    {
        if ( junitConfig == null || javaConfig == null ) return;

        assertFalse( configurationNames( "localJava" ).contains( JUNIT_CONFIG_NAME ) );
    }

    // -------------------------------------------------------------------------
    // Serialization
    // -------------------------------------------------------------------------

    @Test
    @Order( 60 )
    public void testResponseSerializesIntoStructuredContent()
    {
        // Replaces the old "output is valid JSON" test. The tool no longer builds
        // JSON itself; what matters is that the record survives the mapper the
        // framework puts it through.
        var content = McpJson.toMap( service.listLaunchConfigurations( null ) );

        assertTrue( content.containsKey( "configurations" ) );
        assertTrue( content.containsKey( "totalConfigurations" ) );
    }
}
