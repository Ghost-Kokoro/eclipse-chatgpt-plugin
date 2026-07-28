package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * The outcome of applying one quick fix proposal to one problem marker.
 * <p>
 * This is the last link of a loop whose every other link is already typed:
 * {@code getCompilationErrors} hands back a {@code markerId} and a
 * {@link CompilationProblemsResponse.QuickFixOption#index()} precisely so that
 * fix-and-recheck is mechanical. It used to end in six English sentences that all began
 * {@code "Error"} - including the successful one, {@code "Error applying quick fix: …"}
 * being indistinguishable from {@code "Quick fix applied…"} to anything skimming for
 * the word "applied".
 * <p>
 * The four recoverable conditions each need a different next move, so each is a
 * {@link Status} rather than a phrase: a stale marker means re-run
 * {@code getCompilationErrors}; no proposals means edit by hand; a bad index means pick
 * one of {@link #availableProposals()}; and a throw means neither.
 * <p>
 * {@link #availableProposals()} is a field for the same reason: the bound used to be
 * the substring {@code "(0-3)"} inside a sentence, which is the only place a caller
 * could learn what indices exist after being refused.
 *
 * @param markerResolved whether the marker actually went away - the one fact the old
 *            success line carried, as the parenthetical
 *            {@code "applied (marker still present)"}. Null when nothing was applied,
 *            so "not attempted" stays distinguishable from "attempted and the problem
 *            is still there"
 * @param requestedIndex the index the caller asked for, echoed so a refusal can be
 *            matched to the call that caused it
 */
public record QuickFixResponse(
    Status status,
    long markerId,
    String projectName,
    String filePath,
    int requestedIndex,
    String appliedLabel,
    Boolean markerResolved,
    List<CompilationProblemsResponse.QuickFixOption> availableProposals,
    List<Diagnostic> diagnostics
)
{
    public enum Status
    {
        /** The proposal ran and its edit was written to the file. */
        APPLIED,
        /**
         * No marker carries that id any more. Usually because an earlier fix resolved
         * it: re-run {@code getCompilationErrors} for the current ids.
         */
        MARKER_NOT_FOUND,
        /** The marker exists, but the IDE offers no repair for it. */
        NO_PROPOSALS,
        /** {@code requestedIndex} names no entry of {@code availableProposals}. */
        INVALID_PROPOSAL_INDEX,
        /** A proposal was selected and applying it threw. Nothing may have been written. */
        APPLY_FAILED
    }

    /** The marker id is stale; nothing about the file is known. */
    public static QuickFixResponse markerNotFound( long markerId, int requestedIndex )
    {
        return new QuickFixResponse( Status.MARKER_NOT_FOUND, markerId, null, null, requestedIndex, null, null,
                List.of(), List.of( Diagnostic.fatal( DiagnosticCode.RESOURCE_NOT_FOUND,
                        "No problem marker with id " + markerId + " exists. It may have been resolved already;"
                                + " re-run getCompilationErrors for the current marker ids." ) ) );
    }

    /**
     * The marker is real but the IDE knows no repair for it.
     * <p>
     * No diagnostic: a problem with no quick fix is an ordinary state of the workspace,
     * not a fault of this call, and the only code that would fit is
     * {@code INTERNAL_ERROR} - which is documented as "anything unclassified" and would
     * have a caller branching on it treat a routine outcome as a bug.
     */
    public static QuickFixResponse noProposals( long markerId, String projectName, String filePath,
                                                int requestedIndex )
    {
        return new QuickFixResponse( Status.NO_PROPOSALS, markerId, projectName, filePath, requestedIndex, null, null,
                List.of(), List.of() );
    }

    /** The index is out of range; the proposals that do exist come back with it. */
    public static QuickFixResponse invalidProposalIndex( long markerId, String projectName, String filePath,
                                                         int requestedIndex,
                                                         List<CompilationProblemsResponse.QuickFixOption> available )
    {
        return new QuickFixResponse( Status.INVALID_PROPOSAL_INDEX, markerId, projectName, filePath, requestedIndex,
                null, null, available, List.of( Diagnostic.fatal( DiagnosticCode.INVALID_RANGE,
                        "Proposal index " + requestedIndex + " does not exist for marker " + markerId
                                + ". Pick one of the " + available.size() + " entries in availableProposals." ) ) );
    }

    /** The proposal threw. Whether anything was written is unknown - re-read the file. */
    public static QuickFixResponse applyFailed( long markerId, String projectName, String filePath, int requestedIndex,
                                                String label,
                                                List<CompilationProblemsResponse.QuickFixOption> available,
                                                Diagnostic diagnostic )
    {
        return new QuickFixResponse( Status.APPLY_FAILED, markerId, projectName, filePath, requestedIndex, label, null,
                available, List.of( diagnostic ) );
    }

    /** The proposal ran. {@code markerResolved} says whether the problem went with it. */
    public static QuickFixResponse applied( long markerId, String projectName, String filePath, int requestedIndex,
                                            String label, boolean markerResolved,
                                            List<CompilationProblemsResponse.QuickFixOption> available )
    {
        return new QuickFixResponse( Status.APPLIED, markerId, projectName, filePath, requestedIndex, label,
                Boolean.valueOf( markerResolved ), available, List.of() );
    }

    /**
     * Whether the file was changed. Derived rather than a field, and not serialized -
     * {@code McpJson} suppresses accessors so the payload matches the advertised schema.
     */
    public boolean changedResource()
    {
        return status == Status.APPLIED;
    }
}
