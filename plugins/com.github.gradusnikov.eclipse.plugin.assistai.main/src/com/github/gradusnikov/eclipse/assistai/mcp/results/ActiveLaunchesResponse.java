package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * The launches Eclipse currently has running.
 * <p>
 * The tool used to answer with a numbered listing -
 * {@code 1. Foo (com.example.Main) [debug] - Foo (terminated)} - which a caller had to
 * unpick with a regular expression, and which had no room at all for the process id.
 * Worse, "nothing is running" arrived as the sentence {@code "No active launches."},
 * indistinguishable from a failure without reading prose. That state is now the empty
 * list with {@code totalLaunches == 0}.
 */
public record ActiveLaunchesResponse(
    int totalLaunches,
    List<ActiveLaunch> launches,
    String summaryText
)
{
    /**
     * One live launch.
     *
     * @param name the launch configuration's name, which {@code launchConfiguration}
     *            and {@code stopApplication} take
     * @param mode {@code run} or {@code debug}, the same words the launching tools use
     * @param terminated whether the launch as a whole has finished. Only live launches
     *            are listed, so this is normally false; a launch can still carry a
     *            terminated process below it, which is how a caller sees that the JVM
     *            exited before Eclipse reaped the launch
     * @param mainType the fully qualified main class, or null when the configuration
     *            names none
     * @param projectName the project the configuration runs from, or null when it names
     *            none
     * @param pid the operating system process id of the launch's first process, or null
     *            when the debug plug-in recorded none. Repeated out of
     *            {@link #processes()} because a Java launch has exactly one process and
     *            the pid is what a caller reaches for
     */
    public record ActiveLaunch(
        String name,
        String mode,
        boolean terminated,
        String mainType,
        String projectName,
        Long pid,
        List<LaunchProcess> processes
    )
    {
    }

    /**
     * @param terminated the per-process flag the old text rendering appended as
     *            {@code (terminated)}
     * @param pid null when the debug plug-in recorded no process id
     */
    public record LaunchProcess(
        String label,
        boolean terminated,
        Long pid
    )
    {
    }

    public static ActiveLaunchesResponse of( List<ActiveLaunch> launches )
    {
        String summary = launches.isEmpty()
                ? "No active launches."
                : launches.size() + ( launches.size() == 1 ? " active launch." : " active launches." );

        return new ActiveLaunchesResponse( launches.size(), List.copyOf( launches ), summary );
    }
}
