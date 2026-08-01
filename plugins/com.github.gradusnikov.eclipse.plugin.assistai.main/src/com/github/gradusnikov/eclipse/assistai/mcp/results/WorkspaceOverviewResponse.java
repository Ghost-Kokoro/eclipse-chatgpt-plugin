package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

public record WorkspaceOverviewResponse(
    int totalProjects,
    int totalPackages,
    int totalTypes,
    List<ProjectOverview> projects,
    String summaryText
)
{
    public record ProjectOverview(
        String projectName,
        int packageCount,
        int typeCount,
        List<PackageOverview> packages
    )
    {
    }

    public record PackageOverview(
        String packageName,
        int typeCount,
        List<String> typeNames
    )
    {
    }

    public static WorkspaceOverviewResponse of( List<ProjectOverview> projects )
    {
        int totalPackages = projects.stream().mapToInt( ProjectOverview::packageCount ).sum();
        int totalTypes = projects.stream().mapToInt( ProjectOverview::typeCount ).sum();
        String summary = projects.size() + ( projects.size() == 1 ? " project, " : " projects, " )
                + totalPackages + ( totalPackages == 1 ? " package, " : " packages, " )
                + totalTypes + ( totalTypes == 1 ? " type." : " types." );
        return new WorkspaceOverviewResponse( projects.size(), totalPackages, totalTypes, projects, summary );
    }
}
