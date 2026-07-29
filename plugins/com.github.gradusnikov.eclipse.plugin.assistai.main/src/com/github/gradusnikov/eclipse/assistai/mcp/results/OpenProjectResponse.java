package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * The outcome of opening or importing a directory as a workspace project.
 * <p>
 * The field that matters is {@link #projectName()}: it is the name <em>Eclipse
 * assigned</em>, taken from the directory's {@code .project} file or, failing that,
 * from the directory name - and it is not necessarily the last segment of the path the
 * caller passed. Every tool called next takes that name as its {@code projectName}
 * argument, and the only way to learn it used to be to pull it out of
 * {@code Project '(.*)'} in one of three differently worded sentences.
 * <p>
 * The three ways this can succeed are a {@link Status} rather than three wordings,
 * because they mean different things to a caller: {@link Status#IMPORTED} added a
 * project the workspace did not have, {@link Status#OPENED} un-closed one it already
 * had, and {@link Status#ALREADY_OPEN} changed nothing at all - which is the answer to
 * "do I need to call this?", not a failure.
 *
 * @param directoryPath echoed back, because {@code projectName} may differ from its
 *            last segment and a caller comparing the two needs both
 * @param location where the project's content actually lives on disk. For an imported
 *            directory this is that directory; for a project that was already in the
 *            workspace it may be somewhere else entirely
 */
public record OpenProjectResponse(
    Status status,
    String projectName,
    String directoryPath,
    String location,
    List<Diagnostic> diagnostics
)
{
    public enum Status
    {
        /** The workspace did not know this project; it now does, and it is open. */
        IMPORTED,
        /** The project was already in the workspace but closed; it is now open. */
        OPENED,
        /** The project was already open. Nothing was changed. */
        ALREADY_OPEN,
        /** Nothing was opened - see diagnostics. */
        FAILED
    }

    /**
     * Nothing was opened.
     * <p>
     * {@code projectName} is null rather than a guess at what the directory would have
     * been called: a caller must not address a project that does not exist.
     */
    public static OpenProjectResponse failed( String directoryPath, Diagnostic diagnostic )
    {
        return new OpenProjectResponse( Status.FAILED, null, directoryPath, null, List.of( diagnostic ) );
    }

    public static OpenProjectResponse of( Status status, String projectName, String directoryPath, String location )
    {
        return new OpenProjectResponse( status, projectName, directoryPath, location, Diagnostic.none() );
    }
}
