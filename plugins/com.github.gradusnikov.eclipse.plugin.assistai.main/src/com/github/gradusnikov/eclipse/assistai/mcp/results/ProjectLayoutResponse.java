package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * The file and folder tree of a project, or of one directory inside it.
 * <p>
 * A tree, not a read: there is no content, no version and nothing to edit, so this is
 * its own record rather than a
 * {@link com.github.gradusnikov.eclipse.assistai.resources.ResourceReadResult}. The
 * shape is the payload - previously it was a Markdown bullet list whose nesting a
 * caller had to recover by counting leading spaces, with the type of each entry
 * spelled out as the words {@code (Directory)} and {@code (File)}.
 * <p>
 * Every {@link Node#filePath()} is project-relative, so it can be handed straight to
 * {@code readProjectResource} or the editing tools without a second lookup.
 *
 * @param scopePath the subdirectory the listing was limited to, or null for the whole
 *            project. A scope is reported rather than counted as truncation: the
 *            caller chose it
 * @param maxDepth the depth limit that was applied, or null when the tree was walked
 *            to the bottom
 * @param truncated whether {@code maxDepth} stopped the walk with folders left
 *            unlisted. The folders in question say so individually - their
 *            {@code childCount} exceeds the children they carry
 * @param excludedCount how many resources {@code .aiignore} kept out of the listing.
 *            Reported because an absent {@code target/} otherwise looks like an empty
 *            workspace rather than a filtered one
 */
public record ProjectLayoutResponse(
    Status status,
    String projectName,
    String scopePath,
    Integer maxDepth,
    Node root,
    int listedFiles,
    int listedFolders,
    int excludedCount,
    boolean truncated,
    List<Diagnostic> diagnostics
)
{
    public enum Status
    {
        /** The tree was walked; it may still have been cut short by maxDepth. */
        OK,
        /** Nothing could be listed - see diagnostics. */
        FAILED
    }

    public enum NodeType
    {
        PROJECT,
        FOLDER,
        FILE
    }

    /**
     * One entry in the tree.
     *
     * @param filePath project-relative, and empty for the project root itself, which
     *            is the form {@code readProjectResource} and the editing tools take.
     *            Never a workspace path
     * @param childCount how many direct children the container has in the workspace -
     *            which is not always how many are in {@link #children()}, because a
     *            depth limit or an {@code .aiignore} rule can hold some back. 0 for a
     *            file
     * @param children empty for a file, and also for a folder the walk stopped at
     */
    public record Node(
        String name,
        String filePath,
        NodeType type,
        int childCount,
        List<Node> children
    )
    {
        /**
         * Whether this container holds more than the listing shows. Derived from two
         * fields that are both already present, so it is not serialized.
         */
        @JsonIgnore
        public boolean isCollapsed()
        {
            return childCount > children.size();
        }
    }

    public static ProjectLayoutResponse failed( String projectName, String scopePath, Diagnostic diagnostic )
    {
        return new ProjectLayoutResponse( Status.FAILED, projectName, scopePath, null, null,
                0, 0, 0, false, List.of( diagnostic ) );
    }
}
