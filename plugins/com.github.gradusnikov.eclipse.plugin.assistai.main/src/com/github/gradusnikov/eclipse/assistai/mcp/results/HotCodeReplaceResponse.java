package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * Whether new bytecode actually reached the running VM.
 * <p>
 * What this replaces triggered an incremental build and immediately returned
 * {@code "Hot code replace triggered. Changed classes will be reloaded in the debug
 * session."} - a sentence about an intention, not an observation; the code comment
 * admitted it only ensured the build happened. A replace that failed on a schema change,
 * and one that left obsolete methods on the stack, returned the identical sentence as
 * one that worked, and the caller then debugged stale bytecode believing it was new.
 * <p>
 * JDT publishes exactly the three real outcomes through
 * {@code IJavaHotCodeReplaceListener} - {@code hotCodeReplaceSucceeded},
 * {@code hotCodeReplaceFailed} (a null exception meaning the VM does not support it) and
 * {@code obsoleteMethods}. Those are {@link Status#SUCCEEDED}, {@link Status#FAILED} /
 * {@link Status#NOT_SUPPORTED} and {@link Status#OBSOLETE_METHODS}.
 *
 * @param nameOrClass the filter the caller passed, echoed back
 * @param launchName the debug session, null when none matched
 * @param projectName the project that was rebuilt. Null means the launch named no
 *            project and the whole workspace was built - which is what the tool used to
 *            do unconditionally, paying for every project to answer about one
 * @param waitedMillis how long this call waited for the VM's answer after the build
 */
public record HotCodeReplaceResponse(
    Status status,
    String nameOrClass,
    String launchName,
    String projectName,
    long waitedMillis,
    List<Diagnostic> diagnostics,
    String summaryText
)
{
    public enum Status
    {
        /** The VM took the new bytecode. */
        SUCCEEDED,
        /**
         * The VM took it, but frames already on the stack are running the old code. The
         * program is in a mixed state until those frames return - which is why this is
         * not {@link #SUCCEEDED}.
         */
        OBSOLETE_METHODS,
        /** The VM refused - a schema change, usually. The running code is unchanged. */
        FAILED,
        /** This VM cannot hot swap at all. Restarting is the only way to pick up changes. */
        NOT_SUPPORTED,
        /**
         * Nothing needed replacing: the build produced no changed class and the VM is
         * not running anything the workspace has since changed. Common with autobuild
         * on, where the replace already happened before this call.
         */
        IN_SYNC,
        /**
         * The build finished, the VM is out of sync, and no replace was reported within
         * the wait. Distinct from {@link #IN_SYNC}: here there is something to replace.
         */
        TIMED_OUT,
        /** No debug session matched the filter. A state, not an error. */
        SESSION_NOT_FOUND,
        /** The session matched but holds no Java debug target to hot swap. */
        NO_JAVA_TARGET
    }

    /** Whether the VM is now running the workspace's code. Not serialized. */
    public boolean vmIsCurrent()
    {
        return status == Status.SUCCEEDED || status == Status.IN_SYNC;
    }

    public static HotCodeReplaceResponse sessionNotFound( String nameOrClass )
    {
        return new HotCodeReplaceResponse( Status.SESSION_NOT_FOUND, nameOrClass, null, null, 0, Diagnostic.none(),
                "No active debug session matching '" + nameOrClass + "'." );
    }

    public static HotCodeReplaceResponse noJavaTarget( String nameOrClass, String launchName )
    {
        return new HotCodeReplaceResponse( Status.NO_JAVA_TARGET, nameOrClass, launchName, null, 0, Diagnostic.none(),
                "'" + launchName + "' holds no Java debug target to hot swap." );
    }

    public static HotCodeReplaceResponse notSupported( String nameOrClass, String launchName, String projectName )
    {
        return new HotCodeReplaceResponse( Status.NOT_SUPPORTED, nameOrClass, launchName, projectName, 0,
                Diagnostic.none(),
                "The VM running '" + launchName + "' does not support hot code replace; restart it to pick up changes." );
    }

    public static HotCodeReplaceResponse succeeded( String nameOrClass, String launchName, String projectName,
            long waitedMillis )
    {
        return new HotCodeReplaceResponse( Status.SUCCEEDED, nameOrClass, launchName, projectName, waitedMillis,
                Diagnostic.none(), "Hot code replace succeeded in '" + launchName + "'." );
    }

    public static HotCodeReplaceResponse obsoleteMethods( String nameOrClass, String launchName, String projectName,
            long waitedMillis )
    {
        return new HotCodeReplaceResponse( Status.OBSOLETE_METHODS, nameOrClass, launchName, projectName, waitedMillis,
                Diagnostic.none(),
                "Hot code replace succeeded in '" + launchName
                        + "', but obsolete methods remain on the stack; drop to frame or restart to leave them." );
    }

    public static HotCodeReplaceResponse failed( String nameOrClass, String launchName, String projectName,
            long waitedMillis, Diagnostic diagnostic )
    {
        return new HotCodeReplaceResponse( Status.FAILED, nameOrClass, launchName, projectName, waitedMillis,
                List.of( diagnostic ), diagnostic.message() );
    }

    public static HotCodeReplaceResponse inSync( String nameOrClass, String launchName, String projectName,
            long waitedMillis )
    {
        return new HotCodeReplaceResponse( Status.IN_SYNC, nameOrClass, launchName, projectName, waitedMillis,
                Diagnostic.none(),
                "Nothing to replace: the VM running '" + launchName + "' is already in sync with the workspace." );
    }

    public static HotCodeReplaceResponse timedOut( String nameOrClass, String launchName, String projectName,
            long waitedMillis, Diagnostic diagnostic )
    {
        return new HotCodeReplaceResponse( Status.TIMED_OUT, nameOrClass, launchName, projectName, waitedMillis,
                List.of( diagnostic ),
                "The VM running '" + launchName + "' is out of sync and reported no replace within "
                        + waitedMillis + " ms." );
    }
}
