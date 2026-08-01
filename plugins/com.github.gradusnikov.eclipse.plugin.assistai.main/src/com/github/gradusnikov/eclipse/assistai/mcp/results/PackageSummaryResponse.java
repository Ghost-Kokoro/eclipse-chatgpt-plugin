package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

public record PackageSummaryResponse(
    String packageName,
    String projectName,
    int totalTypes,
    List<TypeSummary> types,
    String summaryText
)
{
    public record TypeSummary(
        String simpleName,
        String typeKind,
        String javadocSummary,
        int methodCount,
        int fieldCount,
        List<String> superInterfaces
    )
    {
    }

    public static PackageSummaryResponse of( String packageName, String projectName, List<TypeSummary> types )
    {
        String summary = types.isEmpty()
                ? "Package '" + packageName + "' is empty or not found."
                : types.size() + ( types.size() == 1 ? " type" : " types" ) + " in package '" + packageName + "'.";
        return new PackageSummaryResponse( packageName, projectName, types.size(), types, summary );
    }
}
