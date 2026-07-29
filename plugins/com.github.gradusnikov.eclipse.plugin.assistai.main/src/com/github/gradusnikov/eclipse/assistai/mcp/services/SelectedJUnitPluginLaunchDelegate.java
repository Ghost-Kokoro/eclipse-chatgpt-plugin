package com.github.gradusnikov.eclipse.assistai.mcp.services;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.pde.internal.launching.launcher.RequirementHelper;
import org.eclipse.pde.launching.JUnitLaunchConfigurationDelegate;

/**
 * PDE JUnit launch delegate that resolves an explicit list of test classes, or
 * a single test method within one class.
 * <p>
 * The standard PDE launch configuration supports a single class or a Java
 * container. This delegate extends that to:
 * <ul>
 *   <li>Multiple classes — set {@link #ATTR_TEST_CLASSES} to a list of FQNs.</li>
 *   <li>Single method — set {@link #ATTR_TEST_CLASSES} to a one-element list and
 *       {@link #ATTR_TEST_METHOD} to the method name. JDT's runner scopes execution
 *       to that method only, giving true isolation.</li>
 * </ul>
 * All plug-in resolution and launch behavior remains provided by PDE.
 */
public class SelectedJUnitPluginLaunchDelegate extends JUnitLaunchConfigurationDelegate
{
	
	static {
		// see super static block; without this it would not
		// resolve required bundle dependencies correctly when it's not set to use all dependencies
		RequirementHelper.registerSameRequirementsAsFor(SelectedJUnitPluginLaunchDelegate.LAUNCH_CONFIGURATION_TYPE, "org.eclipse.pde.ui.JunitLaunchConfig");
	}

	
    public static final String LAUNCH_CONFIGURATION_TYPE =
        "com.github.gradusnikov.eclipse.assistai.selectedJUnitPluginTests";

    public static final String ATTR_TEST_CLASSES =
        "com.github.gradusnikov.eclipse.assistai.selectedJUnitPluginTests.testClasses";

    /**
     * Optional single test method name. When set together with a single entry in
     * {@link #ATTR_TEST_CLASSES}, {@link #evaluateTests} returns the resolved
     * {@link IMethod} so JDT's runner executes only that method.
     */
    public static final String ATTR_TEST_METHOD =
        "com.github.gradusnikov.eclipse.assistai.selectedJUnitPluginTests.testMethod";

    @Override
    protected IMember[] evaluateTests( ILaunchConfiguration configuration, IProgressMonitor monitor )
        throws CoreException
    {
        List<String> classNames = configuration.getAttribute( ATTR_TEST_CLASSES, List.of() );
        if ( classNames.isEmpty() )
        {
            return super.evaluateTests( configuration, monitor );
        }

        IJavaProject javaProject = getJavaProject( configuration );
        if ( javaProject == null )
        {
            throw new CoreException( Status.error( "The configured Java project was not found." ) );
        }

        // Single class + method name → return the IMethod for isolated execution
        String methodName = configuration.getAttribute( ATTR_TEST_METHOD, "" );
        if ( classNames.size() == 1 && !methodName.isBlank() )
        {
            String className = classNames.get( 0 );
            IType type = javaProject.findType( className );
            if ( type == null )
            {
                throw new CoreException( Status.error(
                    "Test class '" + className + "' was not found in project '"
                    + javaProject.getElementName() + "'." ) );
            }
            IMethod method = findMethodByName( type, methodName );
            if ( method == null )
            {
                throw new CoreException( Status.error(
                    "Test method '" + methodName + "' was not found in class '" + className + "'." ) );
            }
            return new IMember[] { method };
        }

        // Multiple classes (or single class without a method) → return IType[]
        List<IMember> testClasses = new ArrayList<>( classNames.size() );
        for ( String className : classNames )
        {
            IType testClass = javaProject.findType( className );
            if ( testClass == null )
            {
                throw new CoreException( Status.error(
                    "Test class '" + className + "' was not found in project '"
                    + javaProject.getElementName() + "'." ) );
            }
            testClasses.add( testClass );
        }
        return testClasses.toArray( IMember[]::new );
    }

    /**
     * Finds a method by simple name. Returns the first match (by name) or
     * {@code null} when no method with that name exists on the type.
     */
    private IMethod findMethodByName( IType type, String methodName ) throws CoreException
    {
        for ( IMethod method : type.getMethods() )
        {
            if ( method.getElementName().equals( methodName ) )
            {
                return method;
            }
        }
        return null;
    }
}
