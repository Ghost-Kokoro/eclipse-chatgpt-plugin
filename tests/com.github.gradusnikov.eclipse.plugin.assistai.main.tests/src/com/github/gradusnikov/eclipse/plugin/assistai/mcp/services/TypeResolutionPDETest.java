package com.github.gradusnikov.eclipse.plugin.assistai.mcp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.github.gradusnikov.eclipse.assistai.mcp.results.TypeResolutionResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.services.JavaDocService;
import com.github.gradusnikov.eclipse.assistai.resources.SourceOrigin;

/**
 * {@code explainTypeResolution} against a real Java project.
 * <p>
 * Assertions are on fields rather than on the lines the report used to print. Two of
 * those lines mattered: {@code Workspace resource:} was a workspace-absolute path no
 * reading or editing tool accepts, and {@code Kind:}/{@code Source strategy:} were two
 * prose renderings of the {@link SourceOrigin} every read already reports.
 */
public class TypeResolutionPDETest
{
    private static final String TEST_PROJECT = "TypeResolutionTestProject";

    private final NullProgressMonitor monitor = new NullProgressMonitor();
    private final JavaDocService service = new JavaDocService();
    private IProject project;

    @BeforeEach
    public void beforeEach() throws Exception
    {
        project = ResourcesPlugin.getWorkspace().getRoot().getProject(TEST_PROJECT);
        if (project.exists())
        {
            project.delete(true, true, monitor);
        }

        IProjectDescription description = project.getWorkspace().newProjectDescription(TEST_PROJECT);
        description.setNatureIds(new String[] { JavaCore.NATURE_ID });
        project.create(description, monitor);
        project.open(monitor);

        IFolder sourceFolder = project.getFolder("src");
        sourceFolder.create(IResource.NONE, true, monitor);

        IJavaProject javaProject = JavaCore.create(project);
        IClasspathEntry[] classpath = {
                JavaCore.newSourceEntry(sourceFolder.getFullPath()),
                JavaCore.newContainerEntry(new Path(JavaRuntime.JRE_CONTAINER))
        };
        javaProject.setRawClasspath(classpath, project.getFullPath().append("bin"), monitor);

        IFile sample = project.getFile("src/example/Sample.java");
        sample.getParent().getAdapter(IFolder.class).create(IResource.NONE, true, monitor);
        sample.create(new ByteArrayInputStream(
                "package example; public class Sample {}".getBytes(StandardCharsets.UTF_8)), true, monitor);
        project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
    }

    @AfterEach
    public void afterEach() throws Exception
    {
        if (project != null && project.exists())
        {
            project.delete(true, true, monitor);
        }
    }

    @Test
    public void testExplainsWorkspaceSourceResolution()
    {
        TypeResolutionResponse result = service.explainTypeResolution(TEST_PROJECT, "example.Sample");

        assertEquals(TypeResolutionResponse.Status.OK, result.status());
        assertEquals(SourceOrigin.WORKSPACE_SOURCE, result.sourceOrigin(),
                "workspace source is the only origin getSource can hand back for editing");

        // The location a caller feeds to readProjectResource or the editing tools.
        assertEquals(TEST_PROJECT, result.projectName());
        assertEquals("src/example/Sample.java", result.filePath());
        assertEquals(TEST_PROJECT, result.searchedProjectName());
        assertEquals("example.Sample", result.resolvedTypeName());

        assertEquals(TypeResolutionResponse.ClasspathEntryKind.SOURCE, result.classpathEntryKind());
        assertEquals(TypeResolutionResponse.RootKind.WORKSPACE_FOLDER, result.rootKind());
        assertNull(result.classFilePath());
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    public void testExplainsBinaryResolution()
    {
        TypeResolutionResponse result = service.explainTypeResolution(TEST_PROJECT, "java.lang.String");

        assertEquals(TypeResolutionResponse.Status.OK, result.status());
        assertNotNull(result.packageFragmentRoot());
        assertNotNull(result.classpathEntryKind());
        assertNotNull(result.classFilePath());
        assertNotNull(result.sourceOrigin());
        // A JRE type is not workspace source, whether or not source happens to be attached.
        assertTrue(result.sourceOrigin() != SourceOrigin.WORKSPACE_SOURCE, String.valueOf(result.sourceOrigin()));
    }

    @Test
    public void testReportsUnresolvedType()
    {
        TypeResolutionResponse result = service.explainTypeResolution(TEST_PROJECT, "does.not.exist.Missing");

        assertEquals(TypeResolutionResponse.Status.TYPE_NOT_RESOLVED, result.status());
        assertEquals(DiagnosticCode.RESOURCE_NOT_FOUND, result.diagnostics().get(0).code());
        assertNull(result.sourceOrigin());
        assertNull(result.filePath());
        assertEquals("does.not.exist.Missing", result.requestedTypeName());
    }

    @Test
    public void testReportsAnUnknownProjectSeparatelyFromAnUnknownType()
    {
        TypeResolutionResponse result = service.explainTypeResolution("NoSuchProjectAnywhere", "example.Sample");

        assertEquals(TypeResolutionResponse.Status.PROJECT_NOT_FOUND, result.status());
        assertEquals(DiagnosticCode.PROJECT_NOT_FOUND, result.diagnostics().get(0).code());
        assertEquals("NoSuchProjectAnywhere", result.searchedProjectName());
    }
}
