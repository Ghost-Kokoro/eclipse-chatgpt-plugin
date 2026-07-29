package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

import com.github.gradusnikov.eclipse.assistai.resources.ResourceVersion;
import com.github.gradusnikov.eclipse.assistai.tools.UnifiedDiffs;

/**
 * The difference between two versions of one file.
 * <p>
 * The diff body stays a string, because a unified diff is already a precise machine
 * format with an established parser everywhere - re-expressing its hunks as nested
 * records would gain nothing and lose interoperability. What is lifted out are the
 * facts a caller would otherwise have to parse it to learn: whether the two sides
 * differ at all, by how much, and which two versions were compared.
 * <p>
 * {@code identical} is a field rather than something inferred from an empty body,
 * because "no differences" and "the diff could not be produced" are different
 * outcomes that both render as no hunks.
 *
 * @param fromVersion the older side; for a local-history comparison this carries the
 *            {@code historyTimestamp} that addresses it again
 * @param toVersion the newer side, whose {@code modificationStamp} is the token an
 *            edit built from this diff quotes as {@code expectedModificationStamp}
 */
public record DiffResponse(
    String projectName,
    String filePath,
    String fromLabel,
    String toLabel,
    ResourceVersion fromVersion,
    ResourceVersion toVersion,
    boolean identical,
    int addedLines,
    int removedLines,
    String unifiedDiff,
    List<Diagnostic> diagnostics
)
{
    /**
     * A comparison that could not be made, carrying the reason as a code.
     * <p>
     * An empty {@code unifiedDiff} otherwise means both "the two sides are identical"
     * and "the diff could not be produced", which is exactly the ambiguity
     * {@code identical} exists to remove.
     */
    public static DiffResponse failed( String projectName, String filePath, Diagnostic diagnostic )
    {
        return new DiffResponse( projectName, filePath, null, null,
                ResourceVersion.UNKNOWN, ResourceVersion.UNKNOWN,
                false, 0, 0, "", List.of( diagnostic ) );
    }

    /** Builds a response from a diff JGit has already computed. */
    public static DiffResponse of( String projectName, String filePath,
                                   String fromLabel, ResourceVersion fromVersion,
                                   String toLabel, ResourceVersion toVersion,
                                   UnifiedDiffs.Unified diff )
    {
        return new DiffResponse(
                projectName, filePath,
                fromLabel, toLabel,
                fromVersion, toVersion,
                diff.isEmpty(),
                diff.addedLines(),
                diff.removedLines(),
                diff.body(),
                Diagnostic.none() );
    }
}
