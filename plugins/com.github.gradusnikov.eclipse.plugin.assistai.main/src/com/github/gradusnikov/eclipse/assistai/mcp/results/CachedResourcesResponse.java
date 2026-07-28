package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;

import com.github.gradusnikov.eclipse.assistai.resources.CachedResource;
import com.github.gradusnikov.eclipse.assistai.resources.ResourceDescriptor;
import com.github.gradusnikov.eclipse.assistai.resources.ResourceDescriptor.ResourceType;

/**
 * The resources currently held in the conversation's resource cache.
 * <p>
 * The listing used to be a fixed-width table whose URI column was truncated from the
 * left, so the one field a caller needs to pass back to {@code getCachedResource} was
 * the field most likely to arrive mangled. Every entry now carries its URI whole,
 * plus the project and project-relative path the reading and editing tools accept, so
 * a listing can be acted on without reconstructing anything.
 * <p>
 * There is no {@code truncated} flag because nothing truncates: the cache itself caps
 * how many resources it holds, so this is always the whole of it.
 */
public record CachedResourcesResponse(
    int totalResources,
    int totalEstimatedTokens,
    List<CachedEntry> resources,
    String summaryText
)
{
    /**
     * One cached resource.
     *
     * @param uri the identifier {@code getCachedResource} takes
     * @param projectName the containing project, or null when the resource is not in
     *            the workspace - console output, or a type read from a JAR
     * @param filePath path relative to that project, or null when the resource is not
     *            a file in it
     * @param modificationStamp the workspace modification stamp the content was read
     *            at - the value an edit quotes as {@code expectedModificationStamp} to
     *            prove it read what it is replacing. Null when the resource has none,
     *            in which case no such check is possible
     * @param cacheRevision how many times this cache entry has been refreshed. Cache
     *            bookkeeping only: it is not stable across sessions, means nothing to
     *            any other tool, and must never be quoted to guard a write. Use
     *            {@code modificationStamp} for that
     */
    public record CachedEntry(
        String uri,
        ResourceType type,
        String displayName,
        String projectName,
        String filePath,
        String cachedAt,
        long cachedAtEpochMilli,
        Long modificationStamp,
        int estimatedTokens,
        int cacheRevision
    )
    {
    }

    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter
            .ofPattern( "yyyy-MM-dd HH:mm:ss" )
            .withZone( ZoneId.systemDefault() );

    /**
     * @param cached the cache contents, in the cache's own order - least recently used
     *            first, which is the order eviction will take them in
     */
    public static CachedResourcesResponse from( Map<URI, CachedResource> cached )
    {
        List<CachedEntry> entries = new ArrayList<>();
        int totalTokens = 0;

        if ( cached != null )
        {
            for ( Map.Entry<URI, CachedResource> entry : cached.entrySet() )
            {
                CachedResource resource = entry.getValue();
                if ( resource == null )
                {
                    continue;
                }
                entries.add( toEntry( entry.getKey(), resource ) );
                totalTokens += resource.estimateTokens();
            }
        }

        String summary = entries.isEmpty()
                ? "No resources cached. Reading tools - getSource, readProjectResource, "
                        + "getCurrentlyOpenedFile - load resources into the cache."
                : entries.size() + ( entries.size() == 1 ? " cached resource, ~" : " cached resources, ~" )
                        + totalTokens + " tokens.";

        return new CachedResourcesResponse( entries.size(), totalTokens, entries, summary );
    }

    private static CachedEntry toEntry( URI key, CachedResource resource )
    {
        ResourceDescriptor descriptor = resource.descriptor();
        URI uri = key != null ? key : descriptor.uri();

        // A workspace path is /Project/dir/file.ext; every tool that could act on this
        // resource wants the two halves separately.
        String projectName = null;
        String filePath = null;
        IPath workspacePath = descriptor.workspacePath();
        if ( workspacePath != null && workspacePath.segmentCount() > 0 )
        {
            projectName = workspacePath.segment( 0 );
            if ( workspacePath.segmentCount() > 1 )
            {
                filePath = workspacePath.removeFirstSegments( 1 ).toString();
            }
        }

        Instant cachedAt = resource.cachedAt();
        long stamp = resource.modificationStamp();

        return new CachedEntry(
                uri != null ? uri.toString() : null,
                descriptor.type(),
                descriptor.displayName(),
                projectName,
                filePath,
                cachedAt != null ? TIMESTAMP_FMT.format( cachedAt ) : null,
                cachedAt != null ? cachedAt.toEpochMilli() : 0L,
                stamp == IResource.NULL_STAMP ? null : Long.valueOf( stamp ),
                resource.estimateTokens(),
                resource.version() );
    }
}
