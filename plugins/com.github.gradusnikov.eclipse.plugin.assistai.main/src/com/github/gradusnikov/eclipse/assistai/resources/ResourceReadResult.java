package com.github.gradusnikov.eclipse.assistai.resources;

import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IPath;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.gradusnikov.eclipse.assistai.mcp.McpJson;
import com.github.gradusnikov.eclipse.assistai.mcp.results.Diagnostic;
import com.github.gradusnikov.eclipse.assistai.tools.LineOffsets;

/**
 * The outcome of reading a resource.
 * <p>
 * {@link #content()} is exact source text: no Markdown fence, no header line and no
 * line-number prefixes. The line to attribute the first line of {@code content} to is
 * {@link #returnedRange()}{@code .startLine()} - a field, rather than something a
 * caller reconstructs by counting.
 * <p>
 * {@link #version()} is what makes an edit safe: its {@code modificationStamp} is the
 * value passed back as {@code expectedModificationStamp}, so a write can be rejected
 * if the file moved on since the read. Before this existed there was no way to obtain
 * one, which left the whole optimistic-concurrency mechanism unusable.
 * <p>
 * The location is carried as {@code projectName} plus a project-relative
 * {@code filePath} rather than as a {@link ResourceDescriptor}: that type holds an
 * {@code IPath}, which does not serialize, and a workspace-absolute path is not what
 * the reading and editing tools accept anyway.
 */
public record ResourceReadResult(
    ReadStatus status,
    String uri,
    String projectName,
    String filePath,
    String language,
    ResourceVersion version,
    ContentRange returnedRange,
    int totalLines,
    String content,
    SourceOrigin origin,
    boolean readOnly,
    boolean truncated,
    List<ContentRange> omittedRanges,
    List<Diagnostic> diagnostics
)
{
    public enum ReadStatus
    {
        OK,
        /** Content was returned, but not all of it - see truncated and omittedRanges. */
        PARTIAL,
        FAILED
    }

    /** A read that produced nothing, carrying the reason as a code. */
    public static ResourceReadResult failed( String projectName, String filePath, Diagnostic diagnostic )
    {
        return new ResourceReadResult(
                ReadStatus.FAILED, null, projectName, filePath, null,
                ResourceVersion.UNKNOWN, null, 0, "", null, true, false,
                List.of(), List.of( diagnostic ) );
    }

    /**
     * A complete read of content already in hand, described by a resource descriptor.
     * <p>
     * The fields a caller acts on are derived here rather than at each call site,
     * because the derivations have to agree: {@code readOnly} is exactly
     * "the origin is not editable", the range covers the whole content because that is
     * what this factory is for, and the project and path come from the descriptor's
     * workspace path so they are the pair the editing tools accept.
     *
     * @param origin decides editability - attached and decompiled sources look like
     *            ordinary Java and are not writable
     * @param version the version this content was taken at, which is not always the
     *            resource's version now: a cached copy carries the older one
     */
    public static ResourceReadResult of( ResourceDescriptor descriptor, String content, SourceOrigin origin,
                                         ResourceVersion version, List<Diagnostic> diagnostics )
    {
        String text = content == null ? "" : content;
        int totalLines = LineOffsets.countLines( text );
        IPath path = descriptor.workspacePath();

        return new ResourceReadResult(
                ReadStatus.OK,
                descriptor.uri() == null ? null : descriptor.uri().toString(),
                path != null && path.segmentCount() > 0 ? path.segment( 0 ) : null,
                path != null && path.segmentCount() > 1 ? path.removeFirstSegments( 1 ).toString() : null,
                languageOf( descriptor ),
                version,
                new ContentRange( 1, 1, Math.max( 1, totalLines ), 1 ),
                totalLines,
                text,
                origin,
                !origin.isEditable(),
                false,
                List.of(),
                diagnostics );
    }

    /**
     * The language tag for a resource, taken from its file extension.
     * <p>
     * The extension rather than Eclipse's content-type lookup, because a descriptor may
     * name something with no workspace file behind it at all - a decompiled class, a
     * console.
     */
    private static String languageOf( ResourceDescriptor descriptor )
    {
        String name = descriptor.displayName();
        int dot = name == null ? -1 : name.lastIndexOf( '.' );
        return dot > 0 && dot < name.length() - 1 ? name.substring( dot + 1 ) : null;
    }

    /**
     * Whether the caller may attempt to write this resource back.
     * <p>
     * Ignored by the mapper: an {@code isXxx} accessor would otherwise be serialized
     * as an extra field that the advertised schema - generated from the record
     * components - does not mention, so the payload and its schema would disagree.
     */
    @JsonIgnore
    public boolean isEditable()
    {
        return !readOnly && origin != null && origin.isEditable();
    }

    /**
     * Recovers a read result from the {@code structuredContent} of a tool call.
     * <p>
     * The in-IDE chat consumes tool results as an MCP {@code CallToolResult}, where
     * the payload has already become a map. It needs the typed form back to decide
     * whether the result is a cacheable resource. Recognition is by shape, since MCP
     * carries no discriminator.
     *
     * @return the read result, or null when the payload is something else
     */
    public static ResourceReadResult fromStructuredContent( Object structuredContent )
    {
        if ( !( structuredContent instanceof Map<?, ?> map ) )
        {
            return null;
        }
        if ( !map.containsKey( "content" ) || !map.containsKey( "origin" ) || !map.containsKey( "returnedRange" ) )
        {
            return null;
        }
        try
        {
            return McpJson.convert( map, ResourceReadResult.class );
        }
        catch ( RuntimeException e )
        {
            // Shape matched by coincidence; treat it as an ordinary result.
            return null;
        }
    }

    /** Whether this read produced content worth putting in the resource cache. */
    @JsonIgnore
    public boolean isCacheable()
    {
        return status != ReadStatus.FAILED && uri != null && content != null && !content.isEmpty();
    }
}
