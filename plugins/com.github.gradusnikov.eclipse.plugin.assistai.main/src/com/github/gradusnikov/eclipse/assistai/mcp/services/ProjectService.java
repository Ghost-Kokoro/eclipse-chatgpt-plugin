
package com.github.gradusnikov.eclipse.assistai.mcp.services;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.e4.core.di.annotations.Creatable;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.core.resources.IProjectDescription;

import com.github.gradusnikov.eclipse.assistai.mcp.results.Diagnostic;
import com.github.gradusnikov.eclipse.assistai.mcp.results.DiagnosticCode;
import com.github.gradusnikov.eclipse.assistai.mcp.results.OpenProjectResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.ProjectListResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.ProjectLayoutResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.ProjectPropertiesResponse;
import com.github.gradusnikov.eclipse.assistai.services.AiIgnoreService;

import jakarta.inject.Inject;

/**
 * Service for project-related operations including structure analysis, 
 * properties, and layout.
 */
@Creatable
public class ProjectService {
    
    @Inject
    ILog logger;

    @Inject
    AiIgnoreService aiIgnoreService;
    
    /**
     * Lists every project in the workspace.
     * <p>
     * Natures are reported as ids rather than as friendly labels: an id is what
     * identifies a project as Java or Maven, and it survives rewording.
     *
     * @return the projects, each with the name the other tools address it by
     */
    public ProjectListResponse listProjects()
    {
        List<ProjectListResponse.WorkspaceProject> projects = new ArrayList<>();

        for ( IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects() )
        {
            IPath location = project.getLocation();
            projects.add( new ProjectListResponse.WorkspaceProject(
                    project.getName(),
                    project.isOpen(),
                    naturesOf( project ),
                    location == null ? null : location.toOSString() ) );
        }

        return ProjectListResponse.of( projects );
    }

    /**
     * The nature ids of an open project. A closed project has no readable description,
     * so it reports none rather than an error - being closed is already in the response.
     */
    private List<String> naturesOf( IProject project )
    {
        if ( !project.isOpen() )
        {
            return List.of();
        }
        try
        {
            return List.of( project.getDescription().getNatureIds() );
        }
        catch ( CoreException e )
        {
            logger.error( e.getMessage(), e );
            return List.of();
        }
    }


    /**
     * Opens a directory as a workspace project, importing it if the workspace does not
     * already know it.
     * <p>
     * The returned {@code projectName} is the name Eclipse assigned - from the
     * directory's {@code .project} file, or from the directory name when there is
     * none - and it is what every tool called next takes as its {@code projectName}
     * argument. It is not necessarily the last segment of {@code directoryPath}.
     *
     * @param directoryPath an absolute filesystem path to the directory
     * @return which of the three ways this succeeded, or a failure carrying the reason
     *         as a code
     */
    public OpenProjectResponse openProject( String directoryPath )
    {
        File directory = new File( directoryPath );
        if ( !directory.exists() )
        {
            return OpenProjectResponse.failed( directoryPath, Diagnostic.fatal( DiagnosticCode.RESOURCE_NOT_FOUND,
                    "Directory does not exist: " + directoryPath ) );
        }
        if ( !directory.isDirectory() )
        {
            return OpenProjectResponse.failed( directoryPath,
                    Diagnostic.fatal( DiagnosticCode.RESOURCE_NOT_ACCESSIBLE,
                            "Path is not a directory: " + directoryPath ) );
        }

        try
        {
            IProgressMonitor monitor = new NullProgressMonitor();
            File projectFile = new File( directory, ".project" );

            IProjectDescription description;
            if ( projectFile.exists() )
            {
                description = ResourcesPlugin.getWorkspace()
                        .loadProjectDescription( Path.fromOSString( projectFile.getAbsolutePath() ) );
            }
            else
            {
                description = ResourcesPlugin.getWorkspace().newProjectDescription( directory.getName() );
                description.setLocation( Path.fromOSString( directory.getAbsolutePath() ) );
            }

            String projectName = description.getName();
            IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject( projectName );

            if ( project.exists() )
            {
                if ( project.isOpen() )
                {
                    return OpenProjectResponse.of( OpenProjectResponse.Status.ALREADY_OPEN, projectName,
                            directoryPath, locationOf( project ) );
                }
                project.open( monitor );
                return OpenProjectResponse.of( OpenProjectResponse.Status.OPENED, projectName, directoryPath,
                        locationOf( project ) );
            }

            project.create( description, monitor );
            project.open( monitor );

            return OpenProjectResponse.of( OpenProjectResponse.Status.IMPORTED, projectName, directoryPath,
                    locationOf( project ) );
        }
        catch ( CoreException e )
        {
            logger.error( e.getMessage(), e );
            return OpenProjectResponse.failed( directoryPath, Diagnostic.fatal( DiagnosticCode.INTERNAL_ERROR,
                    "Error opening project: " + e.getMessage() ) );
        }
    }

    /** Where a project's content is on disk, or null for one with no local content. */
    private static String locationOf( IProject project )
    {
        IPath location = project.getLocation();
        return location == null ? null : location.toOSString();
    }

    
    /**
     * The file and folder tree of a project, or of one directory inside it.
     * <p>
     * The nesting is the record's nesting rather than leading spaces in a bullet list,
     * and every node carries the project-relative path the reading and editing tools
     * take - so the next call after this one needs no second lookup.
     *
     * @param scopePath a directory relative to the project root, or null for the whole
     *            project. Reported as a field, not as truncation: the caller chose it
     * @param maxDepth how many levels to walk, or null/&lt;=0 for all of them. A folder
     *            the walk stops at still reports how many children it has, and the
     *            response as a whole reports that it was cut short
     */
    public ProjectLayoutResponse getProjectLayout( String projectName, String scopePath, Integer maxDepth )
    {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject( projectName );
        if ( project == null || !project.exists() )
        {
            return ProjectLayoutResponse.failed( projectName, scopePath, Diagnostic.fatal(
                    DiagnosticCode.PROJECT_NOT_FOUND, "Project '" + projectName + "' not found." ) );
        }
        if ( !project.isOpen() )
        {
            return ProjectLayoutResponse.failed( projectName, scopePath, Diagnostic.fatal(
                    DiagnosticCode.RESOURCE_NOT_ACCESSIBLE, "Project '" + projectName + "' is closed." ) );
        }

        IResource start = project;
        if ( scopePath != null && !scopePath.isBlank() )
        {
            start = project.findMember( scopePath );
            if ( start == null || !start.exists() )
            {
                return ProjectLayoutResponse.failed( projectName, scopePath, Diagnostic.fatal(
                        DiagnosticCode.RESOURCE_NOT_FOUND,
                        "Path '" + scopePath + "' not found in project '" + projectName + "'." ) );
            }
        }

        int depthLimit = ( maxDepth == null || maxDepth <= 0 ) ? Integer.MAX_VALUE : maxDepth;
        LayoutWalk walk = new LayoutWalk( depthLimit );

        try
        {
            ProjectLayoutResponse.Node root = walk.visit( start, 0 );
            if ( root == null )
            {
                return ProjectLayoutResponse.failed( projectName, scopePath, Diagnostic.fatal(
                        DiagnosticCode.RESOURCE_NOT_ACCESSIBLE,
                        "'" + ( scopePath == null ? projectName : scopePath )
                                + "' is excluded from AI processing by .aiignore." ) );
            }
            return new ProjectLayoutResponse( ProjectLayoutResponse.Status.OK, projectName, scopePath,
                    depthLimit == Integer.MAX_VALUE ? null : depthLimit, root,
                    walk.files, walk.folders, walk.excluded, walk.truncated, Diagnostic.none() );
        }
        catch ( CoreException e )
        {
            logger.error( e.getMessage(), e );
            return ProjectLayoutResponse.failed( projectName, scopePath, Diagnostic.fatal(
                    DiagnosticCode.INTERNAL_ERROR, "Error retrieving project layout: " + e.getMessage() ) );
        }
    }

    /**
     * One walk of the resource tree, carrying the totals it accumulates.
     * <p>
     * A class rather than a method with out-parameters because the counts have to
     * survive the recursion, and a {@code StringBuilder} passed down - which is how
     * this worked when the result was Markdown - has nowhere to put them.
     */
    private final class LayoutWalk
    {
        private final int maxDepth;

        private int       files;

        private int       folders;

        private int       excluded;

        private boolean   truncated;

        LayoutWalk( int maxDepth )
        {
            this.maxDepth = maxDepth;
        }

        /** @return the node, or null when .aiignore excludes this resource */
        ProjectLayoutResponse.Node visit( IResource resource, int depth ) throws CoreException
        {
            if ( aiIgnoreService.isExcluded( resource ) )
            {
                excluded++;
                return null;
            }

            ProjectLayoutResponse.NodeType type = switch ( resource.getType() )
            {
                case IResource.PROJECT -> ProjectLayoutResponse.NodeType.PROJECT;
                case IResource.FOLDER -> ProjectLayoutResponse.NodeType.FOLDER;
                default -> ProjectLayoutResponse.NodeType.FILE;
            };

            if ( !( resource instanceof IContainer container ) )
            {
                files++;
                return new ProjectLayoutResponse.Node( resource.getName(),
                        resource.getProjectRelativePath().toString(), type, 0, List.of() );
            }

            if ( type == ProjectLayoutResponse.NodeType.FOLDER )
            {
                folders++;
            }
            IResource[] members = container.members();
            List<ProjectLayoutResponse.Node> children = new ArrayList<>();

            if ( depth < maxDepth )
            {
                for ( IResource member : members )
                {
                    ProjectLayoutResponse.Node child = visit( member, depth + 1 );
                    if ( child != null )
                    {
                        children.add( child );
                    }
                }
            }
            else if ( members.length > 0 )
            {
                // The listing stops here, and childCount says how much is behind it.
                truncated = true;
            }

            return new ProjectLayoutResponse.Node( resource.getName(),
                    resource.getProjectRelativePath().toString(), type, members.length, children );
        }
    }
    
    /**
     * How a project is configured.
     * <p>
     * A missing project and a closed one are separate statuses, not two wordings of
     * {@code "Error: …"}: the first is fixed by correcting the name, the second by
     * calling {@code openProject}.
     * <p>
     * Source folders and the output location are project-relative, because that is
     * what the reading and editing tools take. They used to be reported as
     * workspace-absolute paths inside a Markdown bullet list, which no tool accepts.
     *
     * @param projectName the name {@code listProjects} reports
     * @return the configuration, or a failed result carrying the reason as a code
     */
    public ProjectPropertiesResponse getProjectProperties( String projectName )
    {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject( projectName );
        if ( project == null || !project.exists() )
        {
            return ProjectPropertiesResponse.failed( projectName, ProjectPropertiesResponse.Status.PROJECT_NOT_FOUND,
                    Diagnostic.fatal( DiagnosticCode.PROJECT_NOT_FOUND,
                            "Project '" + projectName + "' not found. Use listProjects to see the workspace." ) );
        }
        if ( !project.isOpen() )
        {
            return ProjectPropertiesResponse.failed( projectName, ProjectPropertiesResponse.Status.PROJECT_CLOSED,
                    Diagnostic.fatal( DiagnosticCode.RESOURCE_NOT_ACCESSIBLE,
                            "Project '" + projectName + "' is closed. Call openProject on its directory first." ) );
        }

        try
        {
            ProjectPropertiesResponse.JavaProperties java = project.hasNature( JavaCore.NATURE_ID )
                    ? javaPropertiesOf( JavaCore.create( project ) )
                    : null;

            return ProjectPropertiesResponse.of( projectName, locationOf( project ),
                    List.of( project.getDescription().getNatureIds() ), buildFilesOf( project ), java );
        }
        catch ( CoreException e )
        {
            logger.error( e.getMessage(), e );
            return ProjectPropertiesResponse.failed( projectName, ProjectPropertiesResponse.Status.FAILED,
                    Diagnostic.fatal( DiagnosticCode.INTERNAL_ERROR,
                            "Error retrieving project properties: " + e.getMessage() ) );
        }
    }

    /**
     * The build descriptors present in the project root.
     * <p>
     * This is all that survives of the per-nature sections this tool used to render:
     * for a project that is not a Java project, which build system owns it is the only
     * fact worth a field, and the file that says so is the fact. Everything else those
     * sections produced - file counts, {@code .settings} contents, version-control
     * markers - is answered better by {@code getProjectLayout} or the git tools.
     */
    private static List<String> buildFilesOf( IProject project )
    {
        List<String> found = new ArrayList<>();
        for ( String candidate : BUILD_FILES )
        {
            if ( project.findMember( candidate ) != null )
            {
                found.add( candidate );
            }
        }
        return found;
    }

    private static final List<String> BUILD_FILES = List.of( "pom.xml", "build.gradle", "build.gradle.kts",
            "settings.gradle", "build.xml", "package.json", "CMakeLists.txt", "Makefile", "setup.py",
            "pyproject.toml", "requirements.txt", "Cargo.toml", "go.mod" );

    /**
     * The Java configuration of a project, with every path in the form a tool accepts.
     * <p>
     * Classpath entry paths are workspace-relative ({@code /Project/src}); source
     * folders and the output location are reported with that first segment removed,
     * project references as the bare project name, and libraries resolved to where
     * they are on disk so that a workspace jar and an external one mean the same
     * thing.
     */
    private ProjectPropertiesResponse.JavaProperties javaPropertiesOf( IJavaProject javaProject )
            throws JavaModelException
    {
        List<String> sourceFolders = new ArrayList<>();
        List<String> referencedProjects = new ArrayList<>();
        List<String> referencedLibraries = new ArrayList<>();

        for ( IClasspathEntry entry : javaProject.getRawClasspath() )
        {
            switch ( entry.getEntryKind() )
            {
                case IClasspathEntry.CPE_SOURCE -> sourceFolders.add( projectRelative( entry.getPath() ) );
                case IClasspathEntry.CPE_PROJECT -> referencedProjects.add( entry.getPath().segment( 0 ) );
                case IClasspathEntry.CPE_LIBRARY -> referencedLibraries.add( onDisk( entry.getPath() ) );
                default ->
                {
                    // Containers and variables resolve to the two kinds above; a caller
                    // asking "what is on the classpath" is served by the resolved form,
                    // which getProjectDependencies and getEffectivePom already give.
                }
            }
        }

        return new ProjectPropertiesResponse.JavaProperties(
                javaProject.getOption( JavaCore.COMPILER_COMPLIANCE, true ),
                javaProject.getOption( JavaCore.COMPILER_SOURCE, true ),
                javaProject.getOption( JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, true ),
                projectRelative( javaProject.getOutputLocation() ),
                sourceFolders, referencedProjects, referencedLibraries );
    }

    /**
     * A workspace-relative path with its project segment removed.
     *
     * @return the project-relative path, empty when the path is the project root itself
     */
    private static String projectRelative( IPath workspacePath )
    {
        if ( workspacePath == null )
        {
            return null;
        }
        return workspacePath.segmentCount() > 1 ? workspacePath.removeFirstSegments( 1 ).toString() : "";
    }

    /** A classpath library as an absolute filesystem path, whether or not it is in the workspace. */
    private static String onDisk( IPath classpathPath )
    {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IResource inWorkspace = root.findMember( classpathPath );
        if ( inWorkspace != null && inWorkspace.getLocation() != null )
        {
            return inWorkspace.getLocation().toOSString();
        }
        return classpathPath.toOSString();
    }

    
}