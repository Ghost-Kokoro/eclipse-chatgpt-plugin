package com.github.gradusnikov.eclipse.plugin.assistai.mcp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.gradusnikov.eclipse.assistai.mcp.results.TestClassesResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.TestClassesResponse.TestClass;
import com.github.gradusnikov.eclipse.assistai.mcp.services.UnitTestService;

public class UnitTestDiscoveryPDETest
{
    private static final String TEST_PROJECT = "UnitTestDiscoveryTestProject";

    private static final String EMPTY_PROJECT = "UnitTestDiscoveryEmptyProject";

    private final NullProgressMonitor monitor = new NullProgressMonitor();
    private final UnitTestService service = new UnitTestService();
    private IProject project;
    private IProject emptyProject;
    private IFolder packageFolder;

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
        packageFolder = sourceFolder.getFolder("sample");
        packageFolder.create(IResource.NONE, true, monitor);

        IJavaProject javaProject = JavaCore.create(project);
        javaProject.setRawClasspath(
                new org.eclipse.jdt.core.IClasspathEntry[] { JavaCore.newSourceEntry(sourceFolder.getFullPath()) },
                project.getFullPath().append("bin"), monitor);

        emptyProject = ResourcesPlugin.getWorkspace().getRoot().getProject(EMPTY_PROJECT);
        if (emptyProject.exists())
        {
            emptyProject.delete(true, true, monitor);
        }
        IProjectDescription emptyDescription = emptyProject.getWorkspace().newProjectDescription(EMPTY_PROJECT);
        emptyDescription.setNatureIds(new String[] { JavaCore.NATURE_ID });
        emptyProject.create(emptyDescription, monitor);
        emptyProject.open(monitor);
        IFolder emptySource = emptyProject.getFolder("src");
        emptySource.create(IResource.NONE, true, monitor);
        JavaCore.create(emptyProject).setRawClasspath(
                new org.eclipse.jdt.core.IClasspathEntry[] { JavaCore.newSourceEntry(emptySource.getFullPath()) },
                emptyProject.getFullPath().append("bin"), monitor);

        createSource("PlainTest.java", "package sample; public class PlainTest {}");
        createSource("WorkspacePDETest.java", "package sample; public class WorkspacePDETest {}");
        createSource("MisnamedIntegrationTest.java",
                "package sample; import org.eclipse.core.resources.ResourcesPlugin; public class MisnamedIntegrationTest { Object workspace = ResourcesPlugin.getWorkspace(); }");
        createSource("Odd.java", "package sample; public class Odd { void test() {} }");
        project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
    }

    @AfterEach
    public void afterEach() throws Exception
    {
        if (project != null && project.exists())
        {
            project.delete(true, true, monitor);
        }
        if (emptyProject != null && emptyProject.exists())
        {
            emptyProject.delete(true, true, monitor);
        }
    }

    @Test
    public void testClassifiesPdeTestsAndReportsNamingWarnings()
    {
        TestClassesResponse result = service.findTestClasses(TEST_PROJECT);

        assertEquals(TEST_PROJECT, result.projectName());
        assertEquals(3, result.totalClasses());

        List<String> plain = result.plainTests().stream().map(TestClass::className).toList();
        assertEquals(List.of("sample.MisnamedIntegrationTest", "sample.PlainTest"), plain);

        List<String> pde = result.pdeTests().stream().map(TestClass::className).toList();
        assertEquals(List.of("sample.WorkspacePDETest"), pde);

        // The warning is the actionable part: this class uses PDE runtime types but is
        // launched by the plain runner because it does not carry the suffix.
        assertEquals(List.of("sample.MisnamedIntegrationTest"), result.namingWarnings());
        assertTrue(result.hasNamingWarnings());

        // A class with a method merely named test() is not a test class.
        assertFalse(plain.contains("sample.Odd"));
        assertFalse(pde.contains("sample.Odd"));
    }

    @Test
    public void testReportsWhereEachTestClassLives()
    {
        TestClassesResponse result = service.findTestClasses(TEST_PROJECT);

        TestClass pdeTest = result.pdeTests().get(0);
        // Project-relative, so the class can go straight to the reading tools.
        assertEquals("src/sample/WorkspacePDETest.java", pdeTest.filePath());
        assertTrue(pdeTest.likelyRequiresPdeHarness());
    }

    @Test
    public void testEmptyProjectIsNotAnError()
    {
        TestClassesResponse result = service.findTestClasses(EMPTY_PROJECT);

        assertEquals(0, result.totalClasses());
        assertTrue(result.plainTests().isEmpty());
        assertTrue(result.pdeTests().isEmpty());
        assertFalse(result.hasNamingWarnings());
    }

    private void createSource(String fileName, String source) throws Exception
    {
        IFile file = packageFolder.getFile(fileName);
        file.create(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)), true, monitor);
    }
}
