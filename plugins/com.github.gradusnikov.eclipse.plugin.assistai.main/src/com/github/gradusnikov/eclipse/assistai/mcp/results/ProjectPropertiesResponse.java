package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * How one project is configured.
 * <p>
 * Two things drove the conversion. A missing project and a closed one both arrived as
 * {@code "Error: …"}, and they need opposite next moves - fix the name, or call
 * {@code openProject} - so they are separate {@link Status} values. And the source
 * folders, which are the fact an agent needs in order to know where a new class may
 * go, arrived as workspace-absolute paths ({@code /Project/src}) inside a Markdown
 * bullet list; convention 4 forbids that, because no reading or editing tool accepts
 * one. They are project-relative here.
 * <p>
 * The C/C++ and Python sections this used to render were removed rather than
 * converted: every fact in them was either already in {@link #natures()} or
 * {@link #buildFiles()}, or is answered better by another tool -
 * {@code getProjectLayout} for file counts and {@code .settings} contents, the
 * {@code eclipse-git} tools for version control.
 *
 * @param location absolute filesystem path, or null for a project with no local content
 * @param natures nature ids rather than friendly labels, matching
 *            {@link ProjectListResponse}: {@code org.eclipse.jdt.core.javanature} is
 *            what identifies a Java project whatever the wording changes to
 * @param buildFiles the build descriptors present in the project root, by name -
 *            {@code pom.xml}, {@code build.gradle}, {@code Makefile}. Empty is a fact,
 *            not a failure
 * @param java null when the project has no Java nature, which is not the same as a
 *            Java project with nothing configured
 */
public record ProjectPropertiesResponse(
    Status status,
    String projectName,
    String location,
    List<String> natures,
    List<String> buildFiles,
    JavaProperties java,
    List<Diagnostic> diagnostics
)
{
    public enum Status
    {
        OK,
        /** No project of that name. Fix the name; {@code listProjects} has the real ones. */
        PROJECT_NOT_FOUND,
        /** The project exists but is closed. Call {@code openProject} on its directory. */
        PROJECT_CLOSED,
        /** The project was readable but its configuration was not - see diagnostics. */
        FAILED
    }

    /**
     * The Java configuration of a project with the JDT nature.
     *
     * @param sourceFolders project-relative, and the answer to "where may a new class
     *            go?". An entry is empty when the project root is itself a source
     *            folder
     * @param outputLocation project-relative; where the compiler writes class files,
     *            which is what a caller excludes from a search
     * @param referencedProjects the {@code projectName} of each project on this one's
     *            classpath, in the form the other tools take
     * @param referencedLibraries absolute filesystem paths. A library inside the
     *            workspace is resolved to where it is on disk, so every entry means
     *            the same thing
     */
    public record JavaProperties(
        String complianceLevel,
        String sourceCompatibility,
        String targetCompatibility,
        String outputLocation,
        List<String> sourceFolders,
        List<String> referencedProjects,
        List<String> referencedLibraries
    )
    {
    }

    public static ProjectPropertiesResponse failed( String projectName, Status status, Diagnostic diagnostic )
    {
        return new ProjectPropertiesResponse( status, projectName, null, List.of(), List.of(), null,
                List.of( diagnostic ) );
    }

    public static ProjectPropertiesResponse of( String projectName, String location, List<String> natures,
            List<String> buildFiles, JavaProperties java )
    {
        return new ProjectPropertiesResponse( Status.OK, projectName, location, natures, buildFiles, java,
                Diagnostic.none() );
    }
}
