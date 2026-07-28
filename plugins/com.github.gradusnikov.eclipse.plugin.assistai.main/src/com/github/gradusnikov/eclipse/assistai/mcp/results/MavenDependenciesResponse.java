package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * The dependencies one project's pom declares.
 * <p>
 * These are the dependencies of the <em>project model</em> - what the pom itself
 * declares, after inheritance from its parent - and not the resolved transitive graph.
 * That is a property of the tool, so it is stated in the tool's description rather
 * than shipped as a {@code Note:} line inside the payload, where it looked like part
 * of the answer.
 */
public record MavenDependenciesResponse(
    String projectName,
    int totalDependencies,
    List<MavenDependency> dependencies,
    String summaryText
)
{
    /**
     * One declared dependency.
     *
     * @param version null when the pom does not state one here, which is the ordinary
     *            case for a dependency whose version comes from a parent's
     *            {@code dependencyManagement}. It used to be rendered as the literal
     *            text {@code null}, indistinguishable from a version so named
     * @param scope the declared scope, or {@code "compile"} when the pom omits it -
     *            the default Maven itself applies, so a caller comparing scopes does
     *            not have to know the rule
     */
    public record MavenDependency(
        String groupId,
        String artifactId,
        String version,
        String scope
    )
    {
        /** Whether the version comes from somewhere other than this pom. */
        public boolean versionManagedElsewhere()
        {
            return version == null;
        }
    }

    public static MavenDependenciesResponse of( String projectName, List<MavenDependency> dependencies )
    {
        // Convention 7: an empty result is a count of zero, not a sentence saying so.
        String summary = dependencies.isEmpty()
                ? "No dependencies declared by '" + projectName + "'."
                : dependencies.size() + ( dependencies.size() == 1 ? " dependency" : " dependencies" )
                        + " declared by '" + projectName + "'.";

        return new MavenDependenciesResponse( projectName, dependencies.size(), dependencies, summary );
    }
}
