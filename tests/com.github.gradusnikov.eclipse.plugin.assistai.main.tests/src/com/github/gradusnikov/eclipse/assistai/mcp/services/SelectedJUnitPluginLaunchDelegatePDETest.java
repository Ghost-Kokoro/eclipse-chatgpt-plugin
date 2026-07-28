package com.github.gradusnikov.eclipse.assistai.mcp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.Launch;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SelectedJUnitPluginLaunchDelegatePDETest
{
    private static final String TEST_PROJECT = "SelectedJUnitPluginLaunchDelegate_TestProject";

    private IProject            project;

    @BeforeEach
    public void createJavaProject() throws Exception
    {
        NullProgressMonitor monitor = new NullProgressMonitor();
        project = ResourcesPlugin.getWorkspace().getRoot().getProject( TEST_PROJECT );
        if ( project.exists() )
        {
            project.delete( true, true, monitor );
        }
        project.create( monitor );
        project.open( monitor );

        IProjectDescription description = project.getDescription();
        description.setNatureIds( new String[] { JavaCore.NATURE_ID } );
        project.setDescription( description, monitor );

        IFolder sourceFolder = project.getFolder( "src" );
        sourceFolder.create( true, true, monitor );
        IFolder outputFolder = project.getFolder( "bin" );
        outputFolder.create( true, true, monitor );

        IJavaProject javaProject = JavaCore.create( project );
        javaProject.setOutputLocation( outputFolder.getFullPath(), monitor );
        IClasspathEntry sourceEntry = JavaCore.newSourceEntry( sourceFolder.getFullPath() );
        javaProject.setRawClasspath( new IClasspathEntry[] { sourceEntry }, monitor );

        IPackageFragmentRoot sourceRoot = javaProject.getPackageFragmentRoot( sourceFolder );
        IPackageFragment packageFragment = sourceRoot.createPackageFragment( "example.selected", true, monitor );
        packageFragment.createCompilationUnit( "FirstPDETest.java", "package example.selected; public class FirstPDETest {}", true, monitor );
        packageFragment.createCompilationUnit( "SecondPDETest.java", "package example.selected; public class SecondPDETest {}", true, monitor );
    }

    @AfterEach
    public void deleteJavaProject() throws Exception
    {
        if ( project != null && project.exists() )
        {
            project.delete( true, true, new NullProgressMonitor() );
        }
    }

    @Test
    public void testEvaluateTests_resolvesEverySelectedClass() throws Exception
    {
        ILaunchConfigurationType type = DebugPlugin.getDefault().getLaunchManager()
                .getLaunchConfigurationType( SelectedJUnitPluginLaunchDelegate.LAUNCH_CONFIGURATION_TYPE );
        assertNotNull( type );

        ILaunchConfigurationWorkingCopy configuration = type.newInstance( null, "SelectedJUnitPluginLaunchDelegatePDETest" );
        configuration.setAttribute( IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, TEST_PROJECT );
        configuration.setAttribute( SelectedJUnitPluginLaunchDelegate.ATTR_TEST_CLASSES,
                List.of( "example.selected.FirstPDETest", "example.selected.SecondPDETest" ) );

        IMember[] selected = new SelectedJUnitPluginLaunchDelegate().evaluateTests( configuration, new NullProgressMonitor() );

        assertEquals( 2, selected.length );
        assertEquals( "example.selected.FirstPDETest", ( (IType) selected[0] ).getFullyQualifiedName() );
        assertEquals( "example.selected.SecondPDETest", ( (IType) selected[1] ).getFullyQualifiedName() );
    }

    /**
     * The test above passes whether or not a multi-class run actually works, because JDT
     * resolves a test target from the configuration itself <em>before</em> it ever calls
     * {@code evaluateTests} - and for a JUnit 5 or 6 test kind that resolution used to
     * abort with "The input type of the launch configuration does not exist", because
     * {@code PDEService} left both of the attributes it reads blank. So this one goes in
     * through {@code launch}, the way the tool does, and stops at the first step past the
     * resolution rather than starting a JVM.
     * <p>
     * The configuration is built by {@code PDEService.applyTestTargeting} rather than by
     * this test, so a change to the targeting rules is caught here instead of being
     * mirrored here.
     */
    @Test
    public void testLaunchPath_resolvesEverySelectedClass() throws Exception
    {
        IJavaProject javaProject = JavaCore.create( project );
        ILaunchConfigurationType type = DebugPlugin.getDefault().getLaunchManager()
                .getLaunchConfigurationType( SelectedJUnitPluginLaunchDelegate.LAUNCH_CONFIGURATION_TYPE );
        assertNotNull( type );

        ILaunchConfigurationWorkingCopy configuration = type.newInstance( null,
                "SelectedJUnitPluginLaunchDelegatePDETest-launch" );
        PDEService.applyTestTargeting( configuration, javaProject, null,
                List.of( javaProject.findType( "example.selected.FirstPDETest" ),
                         javaProject.findType( "example.selected.SecondPDETest" ) ) );
        // The kind that takes the failing branch: for JUnit 3 and 4 JDT calls
        // evaluateTests directly and never resolves a target of its own.
        configuration.setAttribute( "org.eclipse.jdt.junit.TEST_KIND", "org.eclipse.jdt.junit.loader.junit5" );

        StopAtVerification delegate = new StopAtVerification();
        ILaunch launch = new Launch( configuration, ILaunchManager.RUN_MODE, null );

        assertThrows( StopAtVerification.Reached.class,
                () -> delegate.launch( configuration, ILaunchManager.RUN_MODE, launch, new NullProgressMonitor() ),
                "the launch did not get as far as verifying the main type" );

        assertEquals( List.of( "example.selected.FirstPDETest", "example.selected.SecondPDETest" ),
                delegate.resolved,
                "the launch must run every selected class, not just the one named as the input type" );
    }

    /**
     * Runs the real launch up to the point where the test selection is settled, then
     * stops. Everything past {@code verifyMainTypeName} is PDE assembling bundles for a
     * second Eclipse - which needs a plug-in project and several seconds, and none of
     * which is what this is testing.
     */
    private static final class StopAtVerification extends SelectedJUnitPluginLaunchDelegate
    {
        private List<String> resolved;

        static final class Reached extends RuntimeException
        {
            private static final long serialVersionUID = 1L;
        }

        @Override
        protected IMember[] evaluateTests( ILaunchConfiguration configuration, IProgressMonitor monitor )
                throws CoreException
        {
            IMember[] members = super.evaluateTests( configuration, monitor );
            resolved = Arrays.stream( members ).map( m -> ( (IType) m ).getFullyQualifiedName() ).toList();
            return members;
        }

        @Override
        protected void preLaunchCheck( ILaunchConfiguration configuration, ILaunch launch, IProgressMonitor monitor )
        {
            // PDE's version merges the bundle map for a runtime workbench. Not needed to
            // decide what the launch would run, and slow.
        }

        @Override
        public String verifyMainTypeName( ILaunchConfiguration configuration )
        {
            throw new Reached();
        }
    }
}
