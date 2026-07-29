package com.github.gradusnikov.eclipse.assistai.resources;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;

import com.github.gradusnikov.eclipse.assistai.mcp.results.Diagnostic;
import com.github.gradusnikov.eclipse.assistai.mcp.results.DiagnosticCode;
import com.github.gradusnikov.eclipse.assistai.tools.LineOffsets;
import com.github.gradusnikov.eclipse.assistai.tools.ResourceUtilities;

/**
 * A cached resource with its content and metadata.
 * Immutable record - updates create new instances.
 * <p>
 * Two notions of "version" meet here and must not be confused:
 * <ul>
 * <li>{@link #modificationStamp()} is the workspace's own
 * {@link IResource#getModificationStamp()}. It is the only one that means anything
 * outside this cache, and it is what an edit quotes to prove it read the content it
 * is replacing.</li>
 * <li>{@link #version()} and {@link #contentHash()} are cache bookkeeping: a counter
 * incremented on each refresh and a {@link String#hashCode()} used to spot a no-op
 * update. Neither is stable across sessions and neither is fit to guard a write.
 * They are not part of any protocol.</li>
 * </ul>
 */
public record CachedResource(
    ResourceDescriptor descriptor,
    String content,
    Instant cachedAt,
    int version,
    long contentHash,
    Instant fileModifiedAt,
    long modificationStamp
) {
    
    public static CachedResource create(ResourceDescriptor descriptor, String content) {
        return create(descriptor, content, 1);
    }
    
    public static CachedResource create(ResourceDescriptor descriptor, String content, int version) {
        return create(descriptor, content, version, 0L, IResource.NULL_STAMP);
    }
    
    public static CachedResource create(ResourceDescriptor descriptor, String content, int version, long fileModificationTime) {
        return create(descriptor, content, version, fileModificationTime, IResource.NULL_STAMP);
    }
    
    /**
     * @param modificationStamp the workspace modification stamp of the resource this
     *            content was read from, or {@link IResource#NULL_STAMP} when it has
     *            none - a decompiled class or a non-workspace result
     */
    public static CachedResource create(ResourceDescriptor descriptor, String content, int version,
                                        long fileModificationTime, long modificationStamp) {
        return new CachedResource(
            descriptor,
            content,
            Instant.now(),
            version,
            content != null ? content.hashCode() : 0,
            fileModificationTime > 0 ? Instant.ofEpochMilli(fileModificationTime) : null,
            modificationStamp
        );
    }
    
    public CachedResource withUpdatedContent(String newContent) {
        return new CachedResource(
            descriptor,
            newContent,
            Instant.now(),
            version + 1,
            newContent != null ? newContent.hashCode() : 0,
            fileModifiedAt,
            modificationStamp
        );
    }
    
    public CachedResource withUpdatedContent(String newContent, long fileModificationTime) {
        return new CachedResource(
            descriptor,
            newContent,
            Instant.now(),
            version + 1,
            newContent != null ? newContent.hashCode() : 0,
            fileModificationTime > 0 ? Instant.ofEpochMilli(fileModificationTime) : null,
            modificationStamp
        );
    }
    
    /**
     * Checks if content has changed compared to provided content.
     */
    public boolean hasContentChanged(String newContent) {
        if (newContent == null) {
            return content != null;
        }
        return newContent.hashCode() != contentHash;
    }
    
    /**
     * Whether the cached content is still what the workspace holds.
     * <p>
     * Unlike {@link #hasContentChanged(String)} this needs no content to compare
     * against: the workspace bumps the stamp on every modification, including ones
     * made outside this plugin.
     */
    public boolean isStaleAgainst(long currentModificationStamp) {
        if (modificationStamp == IResource.NULL_STAMP || currentModificationStamp == IResource.NULL_STAMP) {
            return false;
        }
        return modificationStamp != currentModificationStamp;
    }
    
    /**
     * Estimates token count (rough: ~4 chars per token).
     */
    public int estimateTokens() {
        return content != null ? content.length() / 4 : 0;
    }
    
    /**
     * Formats this resource as an XML element for the context block.
     */
    public String toXmlElement() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
            "<resource uri=\"%s\" type=\"%s\" name=\"%s\" version=\"%d\" cached=\"%s\"",
            escapeXml(descriptor.uri().toString()),
            descriptor.type(),
            escapeXml(descriptor.displayName()),
            version,
            cachedAt
        ));
        if (fileModifiedAt != null) {
            sb.append(String.format(" fileModified=\"%s\"", fileModifiedAt));
        }
        if (modificationStamp != IResource.NULL_STAMP) {
            sb.append(String.format(" modificationStamp=\"%d\"", modificationStamp));
        }
        sb.append(">\n");
        sb.append(content);
        sb.append("\n</resource>");
        return sb.toString();
    }
    
    /**
     * Presents this entry on the same shape a fresh read returns.
     * <p>
     * A caller should not have to handle cached and freshly-read content differently:
     * both are the text of a resource at some version. The difference that does matter
     * is whether the cached copy is still current, and that is reported -
     * {@code version.inSyncWithFileSystem} compares the stamp this content was taken
     * at against the workspace's stamp now, and a stale entry carries a
     * {@link DiagnosticCode#RESOURCE_OUT_OF_SYNC} diagnostic saying so.
     */
    public ResourceReadResult toReadResult() {
        Optional<IFile> file = descriptor.toWorkspaceFile();
        long liveStamp = file.map(IResource::getModificationStamp).orElse((long) IResource.NULL_STAMP);
        boolean stale = isStaleAgainst(liveStamp);

        return ResourceReadResult.of(
            descriptor,
            content,
            file.isPresent() ? SourceOrigin.WORKSPACE_SOURCE : SourceOrigin.ATTACHED_SOURCE,
            new ResourceVersion(
                // Same rule as ResourceVersion.of: NULL_STAMP is not a value a caller
                // may quote back, so a cached non-workspace resource reports no stamp
                // rather than -1.
                modificationStamp == IResource.NULL_STAMP ? null : modificationStamp,
                fileModifiedAt == null ? 0L : fileModifiedAt.toEpochMilli(),
                null,
                !stale),
            stale
                ? List.of(Diagnostic.retryable(DiagnosticCode.RESOURCE_OUT_OF_SYNC,
                    "This cached copy was taken at modificationStamp " + modificationStamp
                    + ", but the workspace is now at " + liveStamp
                    + ". Re-read the file to get the current content."))
                : Diagnostic.none());
    }

    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;");
    }
}
