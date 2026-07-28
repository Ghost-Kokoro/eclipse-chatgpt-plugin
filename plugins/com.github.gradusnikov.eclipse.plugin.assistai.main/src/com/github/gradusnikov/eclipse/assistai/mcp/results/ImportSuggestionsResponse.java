package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * Candidate types for the unresolved names in a Java file.
 * <p>
 * The whole point of the tool is the fully qualified name, and it used to arrive
 * wrapped in backticks inside an {@code import …;} statement inside a two-space bullet,
 * so a caller stripped four decorations to get back {@code com.example.Foo}.
 * {@link UnresolvedType#candidates()} holds the bare names.
 * <p>
 * Three different situations produced three different sentences that all meant "you got
 * no candidates": a type with no match in the workspace, a file with no unresolved
 * types at all, and a project name that does not name a project. They are now
 * {@code totalCandidates == 0}, {@code totalUnresolvedTypes == 0}, and a
 * {@link Status} respectively. The last of those also stops conflating "no such
 * project" with "the project is closed", which have different remedies - the second is
 * one {@code openProject} call away.
 *
 * @param totalCandidates every candidate across every unresolved type, so "did this
 *            find anything at all" is one field rather than a walk
 */
public record ImportSuggestionsResponse(
    Status status,
    String projectName,
    String filePath,
    int totalUnresolvedTypes,
    int totalCandidates,
    List<UnresolvedType> unresolvedTypes,
    List<Diagnostic> diagnostics
)
{
    public enum Status
    {
        OK,
        /** No project of that name exists in the workspace. */
        PROJECT_NOT_FOUND,
        /** The project exists but is closed; open it and ask again. */
        PROJECT_CLOSED,
        /** The project is open but holds no such file. */
        FILE_NOT_FOUND,
        /** The search itself failed; see diagnostics. */
        FAILED
    }

    /**
     * One name the compiler could not resolve, with what the workspace offers for it.
     *
     * @param lineNumber 1-based, or -1 when the marker records no location
     * @param candidates fully qualified names, ready to paste after {@code import}
     */
    public record UnresolvedType(
        String typeName,
        int lineNumber,
        String message,
        List<String> candidates
    )
    {
    }

    /** A file that was examined, however few unresolved names it turned out to have. */
    public static ImportSuggestionsResponse of( String projectName, String filePath,
                                                List<UnresolvedType> unresolvedTypes )
    {
        int candidates = unresolvedTypes.stream().mapToInt( type -> type.candidates().size() ).sum();
        return new ImportSuggestionsResponse( Status.OK, projectName, filePath, unresolvedTypes.size(), candidates,
                unresolvedTypes, List.of() );
    }

    /** Nothing was examined, and the status says why. */
    public static ImportSuggestionsResponse failed( String projectName, String filePath, Status status,
                                                    Diagnostic diagnostic )
    {
        return new ImportSuggestionsResponse( status, projectName, filePath, 0, 0, List.of(),
                List.of( diagnostic ) );
    }

    /**
     * Whether there is anything to act on. Derived, and not serialized: {@code McpJson}
     * suppresses accessors so the payload matches the advertised schema.
     */
    public boolean hasCandidates()
    {
        return totalCandidates > 0;
    }
}
