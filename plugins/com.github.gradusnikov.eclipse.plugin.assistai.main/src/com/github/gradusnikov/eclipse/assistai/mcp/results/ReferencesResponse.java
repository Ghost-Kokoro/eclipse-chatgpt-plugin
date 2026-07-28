package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * Where a Java element is used.
 * <p>
 * The search engine knows the resource, the offset and the enclosing element for every
 * match; that was flattened into lines like
 * {@code - /Project/src/A.java:42 in doWork}, which an agent had to split on ':' and
 * ' in ' to get back what the IDE already knew. Each reference now names the project
 * and project-relative path the reading and editing tools take.
 */
public record ReferencesResponse(
    String target,
    int totalReferences,
    int filesAffected,
    List<Reference> references,
    boolean truncated,
    String summaryText
)
{
    /**
     * @param lineNumber 1-based, or -1 when the offset could not be resolved
     * @param enclosingElement the method, field or type the reference sits in
     */
    public record Reference(
        String projectName,
        String filePath,
        int lineNumber,
        String enclosingElement,
        String lineContent
    )
    {
    }

    public static ReferencesResponse of( String target, List<Reference> references, boolean truncated )
    {
        long files = references.stream()
                .map( reference -> reference.projectName() + "/" + reference.filePath() )
                .distinct()
                .count();

        String summary = references.isEmpty()
                ? "No references to " + target + "."
                : references.size() + ( references.size() == 1 ? " reference in " : " references in " )
                        + files + ( files == 1 ? " file." : " files." );

        return new ReferencesResponse( target, references.size(), (int) files, references, truncated, summary );
    }

    /**
     * Whether the element appears to be unused, which is what a caller asks before
     * deleting it. Ignored by the mapper so the payload matches its schema.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isUnused()
    {
        return totalReferences == 0;
    }
}
