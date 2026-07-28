package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

import com.github.gradusnikov.eclipse.assistai.resources.SourceOrigin;

/**
 * How one Java type resolves on one project's classpath.
 * <p>
 * This is a diagnostic report, and most of it a person reads. One part of it a program
 * acts on, and that part was wrong: {@code Workspace resource: /Project/src/A.java} is
 * a workspace-absolute path, which no reading or editing tool accepts - an agent that
 * copied it got {@code RESOURCE_NOT_FOUND}. The location is {@link #projectName()}
 * plus a project-relative {@link #filePath()} here, per convention 4.
 * <p>
 * The {@code Kind:} and {@code Source strategy:} lines were two prose renderings of one
 * fact that already had a type: {@link SourceOrigin}, which
 * {@code getSource} and {@code readProjectResource} report on every read. It is reused
 * rather than restated, so "can I edit what getSource gives me?" is answered the same
 * way whichever tool asked.
 *
 * @param sourceOrigin what {@code getSource} would return content from -
 *            {@link SourceOrigin#DECOMPILED_CLASS} is a prediction, since nothing has
 *            tried to decompile yet
 * @param searchedProjectName the project whose classpath was searched: the argument,
 *            echoed, because resolution is per-project and the same name can resolve
 *            differently elsewhere
 * @param projectName the project that owns the type's file, which is not always the
 *            searched one - a type can come from a referenced project. Null when no
 *            workspace resource backs the type
 * @param filePath project-relative within {@link #projectName()}; null when no
 *            workspace resource backs the type
 * @param packageFragmentRoot the classpath root as the classpath states it - a jar
 *            path or a source folder. A coordinate, not a resource to read
 * @param classpathEntryPath the entry as the classpath states it; for a container, the
 *            container id rather than what it resolved to
 */
public record TypeResolutionResponse(
    Status status,
    String requestedTypeName,
    String resolvedTypeName,
    String searchedProjectName,
    SourceOrigin sourceOrigin,
    String projectName,
    String filePath,
    RootKind rootKind,
    String packageFragmentRoot,
    String sourceAttachmentPath,
    ClasspathEntryKind classpathEntryKind,
    String classpathEntryPath,
    String classFilePath,
    List<Diagnostic> diagnostics
)
{
    public enum Status
    {
        OK,
        /** The type is not on that project's classpath. Not an error in the request. */
        TYPE_NOT_RESOLVED,
        /** No open Java project of that name; {@code listProjects} has the real ones. */
        PROJECT_NOT_FOUND
    }

    /** Where the classpath root physically is, which decides whether it can be edited. */
    public enum RootKind
    {
        WORKSPACE_FOLDER,
        WORKSPACE_ARCHIVE,
        EXTERNAL_FOLDER,
        EXTERNAL_ARCHIVE
    }

    /** The kinds of {@code IClasspathEntry}, as names rather than the raw int. */
    public enum ClasspathEntryKind
    {
        SOURCE,
        PROJECT,
        LIBRARY,
        VARIABLE,
        CONTAINER,
        UNKNOWN
    }

    public static TypeResolutionResponse failed( String requestedTypeName, String searchedProjectName,
            Status status, Diagnostic diagnostic )
    {
        return new TypeResolutionResponse( status, requestedTypeName, null, searchedProjectName, null,
                null, null, null, null, null, null, null, null, List.of( diagnostic ) );
    }
}
