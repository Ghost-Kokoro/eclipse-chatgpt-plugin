package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * Who calls a method, and what it calls.
 * <p>
 * The tool answered "who calls this?" but not "where", so every use cost a follow-up
 * {@code findReferences}. JDT's {@code MethodWrapper} holds the {@code IMethod}, so the
 * project, the project-relative path and the line were all in hand and were discarded
 * for {@code cu.getElementName()} - a bare file name, which no reading or editing tool
 * accepts. A node now carries the same location triple
 * {@link ReferencesResponse.Reference} does.
 * <p>
 * Depth was two spaces of indentation. It is a field, and the listing is the same
 * pre-order walk, so the tree is reconstructible without counting characters. A flat
 * list rather than nested {@code calls}: the schema generator stops at a
 * self-referencing record, so nested children would be advertised as objects of
 * unspecified shape - the caller would be told less about the deeper levels than about
 * the first.
 *
 * @param maxDepth how far the caller walk went; a node at this depth may have callers
 *            that were not visited
 */
public record CallHierarchyResponse(
    Status status,
    String target,
    String methodName,
    String declaringType,
    int maxDepth,
    int totalCallers,
    int totalCallees,
    List<CallNode> callers,
    List<CallNode> callees,
    List<Diagnostic> diagnostics
)
{
    public enum Status
    {
        OK,
        /** No open project resolves the class name. */
        TYPE_NOT_FOUND,
        /** The class resolves but declares no such method. */
        METHOD_NOT_FOUND,
        /** The hierarchy could not be computed; see diagnostics. */
        FAILED
    }

    /**
     * One method in the hierarchy.
     *
     * @param depth 1 for a direct caller or callee of the target, 2 for a caller of
     *            one of those, and so on
     * @param signature the parameter list as it reads in source, without parentheses -
     *            the same rendering {@code getClassOutline} and {@code getMethodSource}
     *            use, so an overload named here can be fed back to them
     * @param projectName null when the method is not workspace source - a method in a
     *            JAR has nowhere to open
     * @param lineNumber 1-based, or -1 when there is no source to resolve it against
     */
    public record CallNode(
        int depth,
        String methodName,
        String declaringType,
        String signature,
        String projectName,
        String filePath,
        int lineNumber
    )
    {
    }

    /** A hierarchy that was computed, however small. */
    public static CallHierarchyResponse of( String target, String methodName, String declaringType, int maxDepth,
                                            List<CallNode> callers, List<CallNode> callees,
                                            List<Diagnostic> diagnostics )
    {
        return new CallHierarchyResponse( Status.OK, target, methodName, declaringType, maxDepth,
                callers.size(), callees.size(), callers, callees, diagnostics );
    }

    /** Nothing was walked, and the status says why. */
    public static CallHierarchyResponse failed( String target, String methodName, String declaringType, int maxDepth,
                                                Status status, Diagnostic diagnostic )
    {
        return new CallHierarchyResponse( status, target, methodName, declaringType, maxDepth, 0, 0,
                List.of(), List.of(), List.of( diagnostic ) );
    }

    /**
     * Whether anything calls this method - the question asked before deleting it.
     * Derived, and not serialized: {@code McpJson} suppresses accessors so the payload
     * matches the advertised schema.
     */
    public boolean hasCallers()
    {
        return totalCallers > 0;
    }
}
