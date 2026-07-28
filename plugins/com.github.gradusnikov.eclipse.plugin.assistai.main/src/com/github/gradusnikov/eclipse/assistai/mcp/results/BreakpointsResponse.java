package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * The breakpoints set in the workspace.
 * <p>
 * The old rendering was {@code com.example.Main:42 [disabled] condition: i > 100},
 * which a caller had to split on ':' - a separator that also occurs inside the
 * condition - and which named the type but never the file, so the one thing a caller
 * wants next, opening the line, was not possible without a further search. Each entry
 * now carries the project and project-relative path the reading and editing tools take.
 */
public record BreakpointsResponse(
    int totalBreakpoints,
    int enabledCount,
    List<BreakpointInfo> breakpoints,
    String summaryText
)
{
    /**
     * One breakpoint.
     *
     * @param projectName the Eclipse project the breakpoint's marker lives in, or null
     *            when it is not attached to one
     * @param filePath path relative to the project root, or null when the breakpoint's
     *            source file could not be resolved - a breakpoint created against a
     *            project rather than a file resolves through its type name, and a
     *            binary type has no file in the workspace at all
     * @param typeName the fully qualified type, for Java breakpoints
     * @param lineNumber 1-based, or -1 for a breakpoint that is not on a line
     * @param condition the Java expression that gates the breakpoint, null when there
     *            is none
     * @param hitCount how many hits are skipped before the breakpoint triggers, 0 when
     *            the hit count is disabled
     * @param modelIdentifier the debug model that owns the breakpoint, for example
     *            {@code org.eclipse.jdt.debug}, which is what says whether the Java
     *            fields above could be filled in
     */
    public record BreakpointInfo(
        String projectName,
        String filePath,
        String typeName,
        int lineNumber,
        boolean enabled,
        String condition,
        int hitCount,
        String modelIdentifier
    )
    {
    }

    public static BreakpointsResponse of( List<BreakpointInfo> breakpoints )
    {
        int enabled = (int) breakpoints.stream().filter( BreakpointInfo::enabled ).count();

        String summary = breakpoints.isEmpty()
                ? "No breakpoints set."
                : breakpoints.size() + ( breakpoints.size() == 1 ? " breakpoint, " : " breakpoints, " )
                        + enabled + " enabled.";

        return new BreakpointsResponse( breakpoints.size(), enabled, List.copyOf( breakpoints ), summary );
    }
}
