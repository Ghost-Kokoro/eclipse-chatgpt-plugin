package com.github.gradusnikov.eclipse.assistai.resources;

import org.eclipse.core.resources.IFileState;
import org.eclipse.core.resources.IResource;

/**
 * The version of a resource, expressed entirely in Eclipse's own terms.
 * <p>
 * This plugin mints no version identifiers of its own. The workspace already
 * maintains everything needed:
 * <ul>
 * <li>{@link IResource#getModificationStamp()} changes on every modification,
 * including ones this plugin did not make, and is preserved across sessions. It is
 * what Eclipse's own editors use to detect a stale buffer, and it is the token an
 * edit supplies to prove it read the content it is replacing.</li>
 * <li>{@link IResource#getLocalTimeStamp()} exposes changes made outside the
 * workspace.</li>
 * <li>{@link IFileState#getModificationTime()} identifies a state in local history,
 * which is where every content this plugin displaces is kept.</li>
 * </ul>
 * A content hash would only be a weaker restatement of the modification stamp, so
 * there is not one.
 * <p>
 * {@code modificationStamp} is boxed, and null when there is none. It must never carry
 * {@link IResource#NULL_STAMP}: that value is {@code -1}, {@link #matches(long)} reads
 * {@code -1} as "the caller did not ask for the check", and so a caller that read a
 * history version and quoted its stamp back as {@code expectedModificationStamp} would
 * silently disable the guard it was trying to use. A sentinel does not leave the
 * plugin.
 */
public record ResourceVersion(
    Long modificationStamp,
    long localTimeStamp,
    Long historyTimestamp,
    boolean inSyncWithFileSystem
)
{
    /** A resource that does not exist, or whose version could not be read. */
    public static final ResourceVersion UNKNOWN =
            new ResourceVersion( null, 0L, null, false );

    /**
     * Captures the current version of a workspace resource.
     *
     * @return {@link #UNKNOWN} for a null or non-existent resource - including
     *         attached and decompiled library sources, which have no workspace
     *         version
     */
    public static ResourceVersion of( IResource resource )
    {
        if ( resource == null || !resource.exists() )
        {
            return UNKNOWN;
        }
        long stamp = resource.getModificationStamp();
        return new ResourceVersion(
                stamp == IResource.NULL_STAMP ? null : stamp,
                resource.getLocalTimeStamp(),
                null,
                resource.isSynchronized( IResource.DEPTH_ZERO ) );
    }

    /**
     * Describes content read out of local history rather than from the workspace.
     * Such content is immutable and has no modification stamp: it is addressed by its
     * history timestamp alone.
     */
    public static ResourceVersion ofHistoryState( IFileState state )
    {
        if ( state == null )
        {
            return UNKNOWN;
        }
        return new ResourceVersion(
                null,
                state.getModificationTime(),
                state.getModificationTime(),
                true );
    }

    /**
     * Whether a usable modification stamp was captured.
     * <p>
     * Ignored by the mapper, like every derived accessor on a response record: an
     * {@code isXxx} method is serialized as a field the generated schema does not
     * mention, and the payload must match its schema.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isKnown()
    {
        return modificationStamp != null;
    }

    /** Whether this version describes content read from local history. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isHistorical()
    {
        return historyTimestamp != null;
    }

    /**
     * Whether an edit that read {@code expectedModificationStamp} may still be applied.
     * <p>
     * An unknown expectation means the caller did not ask for the check, which keeps
     * the guard opt-in for tools that have not adopted it yet.
     */
    public boolean matches( long expectedModificationStamp )
    {
        if ( expectedModificationStamp == IResource.NULL_STAMP )
        {
            return true;
        }
        // An unknown version cannot satisfy a specific expectation: the caller asked for
        // a guarantee this resource cannot give, so the edit is refused rather than
        // waved through.
        return modificationStamp != null && modificationStamp == expectedModificationStamp;
    }
}
