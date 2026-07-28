package com.github.gradusnikov.eclipse.assistai.mcp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.JavaRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.gradusnikov.eclipse.assistai.mcp.results.DiagnosticCode;
import com.github.gradusnikov.eclipse.assistai.mcp.results.JavaDocResponse;

/**
 * {@code getJavaDoc} against a real Java project.
 * <p>
 * The body is still one Markdown string; what is checked here is the miss.
 * {@code "JavaDoc is not available for X"} used to occupy the answer slot, so a type
 * with no documentation and a misspelled name produced the same result - and each
 * needs a different next move: read the source, or fix the name.
 */
public class JavaDocServicePDETest
{
    private static final String TEST_PROJECT = "JavaDocServiceTestProject";

    private final NullProgressMonitor monitor = new NullProgressMonitor();
    private final JavaDocService      service = new JavaDocService();
    private IProject                  project;

    @BeforeEach
    public void beforeEach() throws Exception
    {
        project = ResourcesPlugin.getWorkspace().getRoot().getProject( TEST_PROJECT );
        if ( project.exists() )
        {
            project.delete( true, true, monitor );
        }

        IProjectDescription description = project.getWorkspace().newProjectDescription( TEST_PROJECT );
        description.setNatureIds( new String[] { JavaCore.NATURE_ID } );
        project.create( description, monitor );
        project.open( monitor );

        IFolder sourceFolder = project.getFolder( "src" );
        sourceFolder.create( IResource.NONE, true, monitor );

        IJavaProject javaProject = JavaCore.create( project );
        javaProject.setRawClasspath( new IClasspathEntry[] {
                JavaCore.newSourceEntry( sourceFolder.getFullPath() ),
                JavaCore.newContainerEntry( new Path( JavaRuntime.JRE_CONTAINER ) ) },
                project.getFullPath().append( "bin" ), monitor );

        IFolder packageFolder = project.getFolder( "src/example" );
        packageFolder.create( IResource.NONE, true, monitor );

        createFile( "src/example/Documented.java",
                "package example;\n"
                + "\n"
                + "/** Explains what this type is for. */\n"
                + "public class Documented\n"
                + "{\n"
                + "}\n" );
        createFile( "src/example/Bare.java",
                "package example;\n"
                + "\n"
                + "public class Bare\n"
                + "{\n"
                + "    public void run()\n"
                + "    {\n"
                + "    }\n"
                + "}\n" );

        project.refreshLocal( IResource.DEPTH_INFINITE, monitor );
    }

    @AfterEach
    public void afterEach() throws Exception
    {
        if ( project != null && project.exists() )
        {
            project.delete( true, true, monitor );
        }
    }

    @Test
    public void returnsTheDocumentationOfADocumentedType()
    {
        JavaDocResponse response = service.getJavaDoc( "example.Documented" );

        assertEquals( JavaDocResponse.Status.OK, response.status() );
        assertEquals( TEST_PROJECT, response.projectName(), "which project answered is a field" );
        assertTrue( response.markdown().contains( "Explains" ), response.markdown() );
        assertTrue( response.diagnostics().isEmpty() );
    }

    @Test
    public void separatesAnUndocumentedTypeFromAMisspelledName()
    {
        JavaDocResponse undocumented = service.getJavaDoc( "example.Bare" );
        JavaDocResponse misspelled = service.getJavaDoc( "example.Bear" );

        // Both used to be the same sentence in the same field.
        assertEquals( JavaDocResponse.Status.NO_JAVADOC, undocumented.status() );
        assertEquals( JavaDocResponse.Status.TYPE_NOT_FOUND, misspelled.status() );
    }

    @Test
    public void anUndocumentedTypeIsNotAFault()
    {
        JavaDocResponse response = service.getJavaDoc( "example.Bare" );

        assertEquals( TEST_PROJECT, response.projectName(), "the type was found; it simply has no comment" );
        assertTrue( response.diagnostics().isEmpty() );
        assertFalse( response.markdown().isEmpty(), "the members are still listed" );
    }

    @Test
    public void aNameNoProjectResolvesCarriesACodeRatherThanASentence()
    {
        JavaDocResponse response = service.getJavaDoc( "example.Bear" );

        assertNull( response.projectName() );
        assertEquals( "", response.markdown(), "nothing was found, so the answer slot is empty" );
        assertEquals( DiagnosticCode.RESOURCE_NOT_FOUND, response.diagnostics().get( 0 ).code() );
        assertEquals( "example.Bear", response.typeName() );
    }

    private void createFile( String path, String content ) throws Exception
    {
        IFile file = project.getFile( new Path( path ) );
        file.create( new ByteArrayInputStream( content.getBytes( StandardCharsets.UTF_8 ) ), true, monitor );
    }
}
