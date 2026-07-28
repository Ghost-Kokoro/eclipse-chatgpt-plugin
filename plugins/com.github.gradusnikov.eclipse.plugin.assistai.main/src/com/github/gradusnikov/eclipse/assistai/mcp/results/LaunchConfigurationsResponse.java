package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * The launch configurations saved in the workspace.
 * <p>
 * This tool already answered with JSON, hand-assembled field by field into a Jackson
 * {@code ArrayNode} and handed over as a string. Because each field was written only
 * when non-empty, a caller could not tell a configuration that names no project from
 * one whose project it simply failed to read, and the tool advertised no schema at all.
 * The record makes the shape declared rather than described in the tool's own
 * description.
 */
public record LaunchConfigurationsResponse(
    String typeFilter,
    int totalConfigurations,
    List<LaunchConfigurationInfo> configurations,
    String summaryText
)
{
    /**
     * @param name the exact name {@code launchConfiguration}, {@code runJUnitTests} and
     *            {@code runJUnitPluginTests} take as their launcher
     * @param typeId the launch configuration type identifier, for example
     *            {@code org.eclipse.jdt.junit.launchconfig}, and what {@code typeFilter}
     *            matches against
     * @param typeName the type's human-readable label, for example {@code JUnit}
     * @param projectName the project the configuration runs from, empty when it names
     *            none
     * @param mainClass the fully qualified main type, empty when the configuration
     *            names none
     */
    public record LaunchConfigurationInfo(
        String name,
        String typeId,
        String typeName,
        String projectName,
        String mainClass
    )
    {
    }

    /**
     * @param typeFilter the filter that produced this listing, echoed so a caller can
     *            see that an empty result came from a filter and not from an empty
     *            workspace
     */
    public static LaunchConfigurationsResponse of( String typeFilter,
            List<LaunchConfigurationInfo> configurations )
    {
        String summary = configurations.isEmpty()
                ? ( typeFilter == null || typeFilter.isBlank()
                        ? "No launch configurations."
                        : "No launch configurations matching '" + typeFilter + "'." )
                : configurations.size()
                        + ( configurations.size() == 1 ? " launch configuration." : " launch configurations." );

        return new LaunchConfigurationsResponse( typeFilter, configurations.size(),
                List.copyOf( configurations ), summary );
    }
}
