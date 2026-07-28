package com.github.gradusnikov.eclipse.assistai.resources;

/**
 * Where the content of a read came from.
 * <p>
 * This decides whether an edit is even possible, so it is reported rather than left
 * for the caller to infer from the path. Only {@link #WORKSPACE_SOURCE} is writable;
 * everything else is a view of something the workspace does not own.
 */
public enum SourceOrigin
{
    /** A file in the workspace. Editable, versioned, and kept in local history. */
    WORKSPACE_SOURCE,

    /** Source attached to a library. Readable, never editable. */
    ATTACHED_SOURCE,

    /** Reconstructed from bytecode because no source was attached. */
    DECOMPILED_CLASS,

    /** A past state from Eclipse local history. Immutable by definition. */
    LOCAL_HISTORY;

    /** Whether content of this origin can be written back. */
    public boolean isEditable()
    {
        return this == WORKSPACE_SOURCE;
    }
}
