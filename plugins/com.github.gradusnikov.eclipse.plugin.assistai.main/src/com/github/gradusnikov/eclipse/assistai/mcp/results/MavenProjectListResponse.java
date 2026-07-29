package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * The Maven projects m2e knows about in this workspace.
 * <p>
 * The sibling of {@link ProjectListResponse}, and for the same reason: the coordinates
 * used to arrive as {@code ": "}-separated text inside an indented bullet
 * ({@code   GroupId: com.example}), so a caller wanting an artifactId had to split on a
 * separator that appears inside version qualifiers and project names alike.
 * <p>
 * {@code projectName} is the Eclipse project name, which is what every other tool
 * takes; the Maven coordinates are what a build command takes. They are frequently not
 * the same string, which is why both are here.
 */
public record MavenProjectListResponse(
    int totalProjects,
    List<MavenProject> projects,
    String summaryText
)
{
    /**
     * @param projectName the Eclipse project name - the argument the other tools take
     * @param packaging jar, pom, bundle, eclipse-plugin ...; a {@code pom} project
     *            builds nothing itself and is usually an aggregator
     */
    public record MavenProject(
        String projectName,
        String groupId,
        String artifactId,
        String version,
        String packaging
    )
    {
        /** {@code groupId:artifactId:version}, the form a Maven command line takes. */
        public String coordinates()
        {
            return groupId + ":" + artifactId + ":" + version;
        }
    }

    public static MavenProjectListResponse of( List<MavenProject> projects )
    {
        // Convention 7: an empty result is a count of zero, not a sentence saying so.
        String summary = projects.isEmpty()
                ? "No Maven projects in the workspace."
                : projects.size() + ( projects.size() == 1 ? " Maven project." : " Maven projects." );

        return new MavenProjectListResponse( projects.size(), projects, summary );
    }
}
