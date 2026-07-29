package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * The Eclipse target platform in force, as reported by {@code getActiveTarget},
 * {@code setActiveTarget} and {@code reloadTarget}.
 * <p>
 * All three describe the same object, so all three return this. They used to return
 * prose, and every failure path in the two writers returned a sentence rather than
 * throwing - and {@code McpServerFactory} marks a call as an error only when it throws.
 * A caller checking {@code isError}, which is the normal thing to check, was told the
 * target platform had loaded when it had not, and only found out at the next PDE launch.
 * <p>
 * Two distinctions this type exists to keep:
 * <ul>
 * <li>{@link #bundleCount()} is null when the target is not resolved, never {@code 0}.
 * Zero means "resolved, and it contains no bundles" - a different and much worse
 * situation than "not resolved yet".</li>
 * <li>"No explicit target platform is set" is {@link TargetStatus#RUNNING_PLATFORM},
 * not a failure. It is an ordinary, working workspace state - the one where PDE builds
 * against the running IDE - and {@code getActiveTarget} always reported it as such
 * while {@code reloadTarget} called it an error.</li>
 * </ul>
 * A failed set or reload still describes whatever target remains in force, because
 * that is the caller's next question.
 *
 * @param explicitTarget whether a {@code .target} file governs the workspace. False is
 *            the running-platform case, not an absence of information
 * @param name the target definition's own name, or null when it declares none. Not
 *            {@code "<unnamed>"}: a placeholder that reads like a name is worse than
 *            nothing
 * @param memento the handle string PDE addresses this target by
 * @param exists whether the handle still resolves to a target definition on disk
 * @param bundleCount how many bundles the resolved target contains; null when
 *            {@code resolved} is false
 */
public record ActiveTargetResponse(
    TargetStatus status,
    boolean explicitTarget,
    String name,
    String memento,
    boolean exists,
    boolean resolved,
    Integer bundleCount,
    List<Diagnostic> diagnostics
)
{
    /**
     * What the workspace is building against.
     * <p>
     * Only {@link #FAILED} is a failure; the other two are both working states, which
     * is the whole point of separating them from the diagnostics list.
     */
    public enum TargetStatus
    {
        /** An explicit target definition is active. */
        ACTIVE,

        /** No target file is set, so PDE builds against the running platform. */
        RUNNING_PLATFORM,

        /** The operation did not do what was asked; {@code diagnostics} says why. */
        FAILED
    }

    /** No explicit target: the workspace builds against the running IDE. */
    public static ActiveTargetResponse runningPlatform()
    {
        return new ActiveTargetResponse( TargetStatus.RUNNING_PLATFORM, false, null, null, false,
                false, null, List.of() );
    }

    /**
     * A target definition is in force.
     *
     * @param bundleCount null unless {@code resolved} - see the class comment
     */
    public static ActiveTargetResponse active( String name, String memento, boolean exists,
            boolean resolved, Integer bundleCount )
    {
        return new ActiveTargetResponse( TargetStatus.ACTIVE, true, name, memento, exists, resolved,
                resolved ? bundleCount : null, List.of() );
    }

    /**
     * A failure with nothing known about the current target - the state could not be
     * read either. Prefer {@link #withFailure(Diagnostic)}, which keeps what is known.
     */
    public static ActiveTargetResponse failed( Diagnostic diagnostic )
    {
        return new ActiveTargetResponse( TargetStatus.FAILED, false, null, null, false, false, null,
                List.of( diagnostic ) );
    }

    /**
     * This description, marked failed. Used to answer "the load did not happen - so
     * what am I actually building against now?" in one response instead of two calls.
     */
    public ActiveTargetResponse withFailure( Diagnostic diagnostic )
    {
        return new ActiveTargetResponse( TargetStatus.FAILED, explicitTarget, name, memento, exists,
                resolved, bundleCount, List.of( diagnostic ) );
    }

    /** Whether a PDE launch can expect the target's bundles to be there. */
    public boolean isUsable()
    {
        return status != TargetStatus.FAILED && ( !explicitTarget || resolved );
    }
}
