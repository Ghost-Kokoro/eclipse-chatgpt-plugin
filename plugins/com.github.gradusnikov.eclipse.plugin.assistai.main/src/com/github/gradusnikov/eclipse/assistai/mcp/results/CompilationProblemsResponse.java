package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * Compilation problems reported by the workspace.
 * <p>
 * This is the reply an agent consults after almost every edit, and the one it most
 * needs to decide something from: are there errors, where, and can the IDE fix them.
 * Deciding that from a Markdown document meant matching on {@code "**ERROR**"} and
 * counting headings.
 * <p>
 * Counts are of the problems that passed the severity filter, before any truncation,
 * so "are there errors?" is answerable even when the listing was cut short.
 */
public record CompilationProblemsResponse(
    String scope,
    int totalProblems,
    int errorCount,
    int warningCount,
    int infoCount,
    List<FileProblems> files,
    boolean truncated,
    String summaryText
)
{
    public enum Severity
    {
        ERROR, WARNING, INFO, UNKNOWN
    }

    /** Problems grouped by the file they are on, which is how they get fixed. */
    public record FileProblems(
        String projectName,
        String filePath,
        List<Problem> problems
    )
    {
    }

    /**
     * @param lineNumber 1-based, or -1 when the marker records no location
     * @param markerId the id {@code executeQuickFix} takes
     * @param problemId JDT's internal problem id, absent for non-Java markers
     * @param contextSnippet the offending line and its neighbours, the offending one
     *            prefixed with {@code "> "}; null when the file could not be read
     */
    public record Problem(
        Severity severity,
        int lineNumber,
        String message,
        long markerId,
        Integer problemId,
        String contextSnippet,
        String contextLanguage,
        List<QuickFixOption> quickFixes
    )
    {
    }

    /**
     * @param index the value to pass to {@code executeQuickFix} alongside the marker id
     */
    public record QuickFixOption(
        int index,
        String label,
        String description
    )
    {
    }

    /** Whether anything needs attention at error severity. */
    public boolean hasErrors()
    {
        return errorCount > 0;
    }
}
