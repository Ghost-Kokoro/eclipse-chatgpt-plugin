package com.github.gradusnikov.eclipse.plugin.assistai.mcp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.e4.core.contexts.ContextInjectionFactory;
import org.eclipse.e4.core.contexts.EclipseContextFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.JavaRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.util.tracker.ServiceTracker;

import com.github.gradusnikov.eclipse.assistai.Activator;
import com.github.gradusnikov.eclipse.assistai.mcp.results.DiagnosticCode;
import com.github.gradusnikov.eclipse.assistai.mcp.results.OpenProjectResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.ProjectListResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.ProjectPropertiesResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.services.ProjectService;
import com.github.gradusnikov.eclipse.assistai.services.AiIgnoreService;

/**
 * {@code openProject} and {@code getProjectProperties} against a real workspace.
 * <p>
 * Assertions are on fields rather than on wording: the name Eclipse assigned, which is
 * the argument every tool called next takes, used to be recoverable only by matching
 * {@code Project '(.*)'} against one of three sentences.
 */
public class ProjectServicePDETest {

    private static final String[] TEST_PROJECTS =
            { "ExistingProjectTest", "NoProjectFileTest", "PropertiesTestProject", "BuildFilesTestProject",
              "ClosedTestProject" };

    private ProjectService service;
    private IWorkspaceRoot root;
    private NullProgressMonitor monitor = new NullProgressMonitor();
    private Path tempDir;

    @BeforeEach
    public void beforeEach() throws CoreException, IOException {
        BundleContext bundleContext = FrameworkUtil.getBundle(ProjectServicePDETest.class).getBundleContext();
        ServiceTracker<IWorkspace, IWorkspace> workspaceTracker = new ServiceTracker<>(bundleContext, IWorkspace.class, null);
        workspaceTracker.open();
        IWorkspace workspace = workspaceTracker.getService();
        root = workspace.getRoot();

        IEclipseContext context = EclipseContextFactory.create();
        context.set(ILog.class, Activator.getDefault().getLog());
        context.set(AiIgnoreService.class, ContextInjectionFactory.make(AiIgnoreService.class, context));
        service = ContextInjectionFactory.make(ProjectService.class, context);

        deleteTestProjects();
        tempDir = Files.createTempDirectory("projectServiceTest");
    }

    @AfterEach
    public void afterEach() throws CoreException, IOException {
        deleteTestProjects();
        deleteRecursively(tempDir.toFile());
    }

    /**
     * Deletes the content too. Keeping it leaves a .project file behind, and the next
     * test to create a project of the same name silently inherits its natures.
     */
    private void deleteTestProjects() throws CoreException {
        for (String name : TEST_PROJECTS) {
            IProject project = root.getProject(name);
            if (project.exists()) {
                project.delete(true, true, monitor);
            }
        }
    }

    // ---- openProject -----------------------------------------------------

    @Test
    public void testOpenProjectWithExistingProjectFile() throws IOException {
        Path projectDir = tempDir.resolve("ExistingProjectTest");
        Files.createDirectories(projectDir);

        String dotProject = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<projectDescription>\n"
                + "    <name>ExistingProjectTest</name>\n"
                + "    <comment></comment>\n"
                + "    <projects></projects>\n"
                + "    <buildSpec></buildSpec>\n"
                + "    <natures></natures>\n"
                + "</projectDescription>\n";
        Files.writeString(projectDir.resolve(".project"), dotProject);

        OpenProjectResponse result = service.openProject(projectDir.toAbsolutePath().toString());

        assertEquals(OpenProjectResponse.Status.IMPORTED, result.status());
        // The name comes from .project, and it is what every other tool takes next.
        assertEquals("ExistingProjectTest", result.projectName());
        assertEquals(projectDir.toAbsolutePath().toString(), result.directoryPath());
        assertNotNull(result.location());
        assertTrue(result.diagnostics().isEmpty());

        ProjectListResponse projects = service.listProjects();
        assertTrue(names(projects).contains(result.projectName()), "Project should appear in listProjects");
    }

    /**
     * The point of reporting the assigned name: it is taken from {@code .project} and
     * need not be the directory's own name.
     */
    @Test
    public void testOpenProjectReportsTheNameEclipseAssignedNotTheDirectoryName() throws IOException {
        Path projectDir = tempDir.resolve("a-directory-named-something-else");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve(".project"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<projectDescription>\n"
                + "    <name>ExistingProjectTest</name>\n"
                + "    <comment></comment>\n"
                + "    <projects></projects>\n"
                + "    <buildSpec></buildSpec>\n"
                + "    <natures></natures>\n"
                + "</projectDescription>\n");

        OpenProjectResponse result = service.openProject(projectDir.toAbsolutePath().toString());

        assertEquals(OpenProjectResponse.Status.IMPORTED, result.status());
        assertEquals("ExistingProjectTest", result.projectName());
        assertFalse(result.directoryPath().endsWith(result.projectName()),
                "the assigned name differs from the last segment of the path, which is why it is a field");
    }

    @Test
    public void testOpenProjectWithoutProjectFile() throws IOException {
        Path projectDir = tempDir.resolve("NoProjectFileTest");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("hello.txt"), "hello world");

        assertFalse(Files.exists(projectDir.resolve(".project")), ".project should not exist before openProject");

        OpenProjectResponse result = service.openProject(projectDir.toAbsolutePath().toString());

        assertEquals(OpenProjectResponse.Status.IMPORTED, result.status());
        assertEquals("NoProjectFileTest", result.projectName(), "with no .project, the directory name is used");

        ProjectListResponse projects = service.listProjects();
        assertTrue(names(projects).contains("NoProjectFileTest"), "Project should appear in listProjects");
    }

    @Test
    public void testOpenProjectNonExistentDirectory() {
        OpenProjectResponse result = service.openProject("/non/existent/path/xyz123");

        assertEquals(OpenProjectResponse.Status.FAILED, result.status());
        assertEquals(DiagnosticCode.RESOURCE_NOT_FOUND, result.diagnostics().get(0).code());
        assertNull(result.projectName(), "nothing was opened, so there is no name to address");
    }

    @Test
    public void testOpenProjectOnAFileRatherThanADirectory() throws IOException {
        Path file = tempDir.resolve("not-a-directory.txt");
        Files.writeString(file, "text");

        OpenProjectResponse result = service.openProject(file.toAbsolutePath().toString());

        assertEquals(OpenProjectResponse.Status.FAILED, result.status());
        assertEquals(DiagnosticCode.RESOURCE_NOT_ACCESSIBLE, result.diagnostics().get(0).code(),
                "a path that exists but is not a directory is a different fault from one that does not exist");
    }

    @Test
    public void testOpenProjectAlreadyOpen() throws IOException {
        Path projectDir = tempDir.resolve("NoProjectFileTest");
        Files.createDirectories(projectDir);

        service.openProject(projectDir.toAbsolutePath().toString());
        OpenProjectResponse result = service.openProject(projectDir.toAbsolutePath().toString());

        assertEquals(OpenProjectResponse.Status.ALREADY_OPEN, result.status(),
                "nothing changed, which is an answer rather than a failure");
        assertEquals("NoProjectFileTest", result.projectName());
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    public void testOpenProjectReopensAClosedProject() throws CoreException, IOException {
        Path projectDir = tempDir.resolve("NoProjectFileTest");
        Files.createDirectories(projectDir);

        OpenProjectResponse imported = service.openProject(projectDir.toAbsolutePath().toString());
        root.getProject(imported.projectName()).close(monitor);

        OpenProjectResponse result = service.openProject(projectDir.toAbsolutePath().toString());

        assertEquals(OpenProjectResponse.Status.OPENED, result.status(),
                "re-opening a closed project is not the same as importing a new one");
        assertTrue(root.getProject(result.projectName()).isOpen());
    }

    // ---- getProjectProperties --------------------------------------------

    @Test
    public void testProjectPropertiesReportsAMissingProjectAsAStatus() {
        ProjectPropertiesResponse result = service.getProjectProperties("NoSuchProjectAnywhere");

        assertEquals(ProjectPropertiesResponse.Status.PROJECT_NOT_FOUND, result.status());
        assertEquals(DiagnosticCode.PROJECT_NOT_FOUND, result.diagnostics().get(0).code());
        assertNull(result.java());
    }

    @Test
    public void testProjectPropertiesSeparatesAClosedProjectFromAMissingOne() throws CoreException {
        IProject project = root.getProject("ClosedTestProject");
        project.create(monitor);
        project.open(monitor);
        project.close(monitor);

        ProjectPropertiesResponse result = service.getProjectProperties("ClosedTestProject");

        // Different next move from PROJECT_NOT_FOUND: call openProject, do not retype the name.
        assertEquals(ProjectPropertiesResponse.Status.PROJECT_CLOSED, result.status());
        assertEquals(DiagnosticCode.RESOURCE_NOT_ACCESSIBLE, result.diagnostics().get(0).code());
    }

    @Test
    public void testProjectPropertiesReportsSourceFoldersTheEditingToolsAccept() throws CoreException {
        IJavaProject javaProject = createJavaProject("PropertiesTestProject");

        ProjectPropertiesResponse result = service.getProjectProperties("PropertiesTestProject");

        assertEquals(ProjectPropertiesResponse.Status.OK, result.status());
        assertTrue(result.natures().contains(JavaCore.NATURE_ID), result.natures().toString());
        assertNotNull(result.location());
        assertNotNull(result.java(), "a project with the Java nature reports its Java configuration");

        assertEquals(List.of("src"), result.java().sourceFolders(),
                "a workspace path like /PropertiesTestProject/src is accepted by no reading or editing tool");
        assertEquals("bin", result.java().outputLocation());
        assertNotNull(result.java().complianceLevel());
        assertTrue(result.java().referencedProjects().isEmpty());
        assertNotNull(javaProject);
    }

    @Test
    public void testProjectPropertiesListsBuildDescriptorsInTheRoot() throws CoreException {
        IProject project = root.getProject("BuildFilesTestProject");
        project.create(monitor);
        project.open(monitor);
        project.getFile("pom.xml").create(
                new java.io.ByteArrayInputStream("<project/>".getBytes()), true, monitor);

        ProjectPropertiesResponse result = service.getProjectProperties("BuildFilesTestProject");

        assertEquals(ProjectPropertiesResponse.Status.OK, result.status());
        assertEquals(List.of("pom.xml"), result.buildFiles());
        assertNull(result.java(), "no Java nature is not a failure, it is an absent block");
    }

    // ---- fixture ---------------------------------------------------------

    private IJavaProject createJavaProject(String name) throws CoreException {
        IProject project = root.getProject(name);
        IProjectDescription description = project.getWorkspace().newProjectDescription(name);
        description.setNatureIds(new String[] { JavaCore.NATURE_ID });
        project.create(description, monitor);
        project.open(monitor);

        IFolder source = createFolder(project, "src");
        IFolder output = createFolder(project, "bin");

        IJavaProject javaProject = JavaCore.create(project);
        javaProject.setRawClasspath(new IClasspathEntry[] {
                JavaCore.newSourceEntry(source.getFullPath()),
                JavaRuntime.getDefaultJREContainerEntry() }, output.getFullPath(), monitor);
        return javaProject;
    }

    /** Tolerates a folder left on disk by an earlier run, which project.create() adopts. */
    private IFolder createFolder(IProject project, String name) throws CoreException {
        IFolder folder = project.getFolder(name);
        if (!folder.exists()) {
            folder.create(IResource.NONE, true, monitor);
        }
        return folder;
    }

    /** The project names, which is the field the other tools address a project by. */
    private static List<String> names(ProjectListResponse response) {
        return response.projects().stream()
                .map(ProjectListResponse.WorkspaceProject::projectName)
                .toList();
    }

    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
