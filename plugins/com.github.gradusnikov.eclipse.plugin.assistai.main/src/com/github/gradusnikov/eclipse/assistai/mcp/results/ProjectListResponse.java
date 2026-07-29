package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * The projects in the workspace.
 * <p>
 * This is the first call an agent makes, because every other tool addresses a file by
 * {@code projectName} plus a project-relative path. The Markdown it used to return -
 * {@code - **Name** (Open) - Project Type: Java 21, Maven} - had to be unwrapped from
 * its bold markers to recover the one string those tools need, and "Open" versus
 * "Closed" was prose where the caller wanted a flag: a closed project cannot be read
 * from, searched or built until {@code openProject} runs.
 * <p>
 * Natures are reported as their ids rather than as friendly labels, because an id is
 * what identifies a project as Java ({@code org.eclipse.jdt.core.javanature}) or Maven
 * without guessing at wording.
 */
public record ProjectListResponse(
    int totalProjects,
    int openProjects,
    List<WorkspaceProject> projects,
    String summaryText
)
{
    /**
     * @param projectName the value every other tool takes as its {@code projectName} argument
     * @param open a closed project is invisible to reading, searching and building
     * @param natures nature ids; empty for a closed project, whose description cannot be read
     * @param location absolute filesystem path, or null for a project with no local content
     */
    public record WorkspaceProject(
        String projectName,
        boolean open,
        List<String> natures,
        String location
    )
    {
        /**
         * Whether this project's sources are indexed by JDT.
         * <p>
         * Deliberately not named {@code isJava}: a bean-style boolean getter on a record
         * is picked up by the serializer and would appear as a field the generated
         * schema never promised.
         */
        public boolean hasJavaNature()
        {
            return natures.contains( "org.eclipse.jdt.core.javanature" );
        }
    }

    public static ProjectListResponse of( List<WorkspaceProject> projects )
    {
        int open = (int) projects.stream().filter( WorkspaceProject::open ).count();

        String summary = projects.isEmpty()
                ? "No projects in the workspace."
                : projects.size() + ( projects.size() == 1 ? " project, " : " projects, " ) + open + " open.";

        return new ProjectListResponse( projects.size(), open, projects, summary );
    }
}
