package com.github.gradusnikov.eclipse.assistai.mcp.results;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.core.runtime.preferences.InstanceScope;

/**
 * The line delimiter Eclipse is configured to write in a project.
 * <p>
 * Resolved through {@link org.eclipse.core.runtime.preferences.IPreferencesService},
 * in the order the platform itself uses: a project-specific setting, then the
 * workspace setting, then the JVM default. This is the same value the Java editor
 * writes with, so a tool that produces text for a file and a user typing into it agree
 * - which is the whole point of asking rather than guessing from the file's current
 * content.
 * <p>
 * {@code delimiter} is the literal string, which is awkward to read in a JSON payload
 * because it is made of escapes; {@code name} is the same fact as a token a caller can
 * branch on without unescaping anything.
 *
 * @param source where the value came from, so a caller can tell a deliberate
 *            project-specific choice from an inherited default
 */
public record LineDelimiterPreference(
    String projectName,
    String delimiter,
    DelimiterName name,
    Source source
)
{
    public enum DelimiterName
    {
        LF,
        CRLF,
        /** Classic Mac OS. Eclipse still offers it, so it is still reportable. */
        CR,
        /** Configured to something the platform does not recognise as a delimiter. */
        OTHER
    }

    public enum Source
    {
        /** Set on the project itself, overriding the workspace. */
        PROJECT,
        /** The workspace-wide setting. */
        WORKSPACE,
        /** Neither was set; this is the JVM default for the host platform. */
        DEFAULT
    }

    /**
     * Reads the preference for a project.
     *
     * @param project may be null, in which case only the workspace and default scopes
     *            are consulted
     */
    public static LineDelimiterPreference of( IProject project )
    {
        IScopeContext[] scopes = project == null
                ? new IScopeContext[] { InstanceScope.INSTANCE }
                : new IScopeContext[] { new ProjectScope( project ), InstanceScope.INSTANCE };

        String delimiter = Platform.getPreferencesService().getString(
                Platform.PI_RUNTIME, Platform.PREF_LINE_SEPARATOR, System.lineSeparator(), scopes );

        return new LineDelimiterPreference(
                project == null ? null : project.getName(),
                delimiter,
                nameOf( delimiter ),
                sourceOf( project ) );
    }

    private static DelimiterName nameOf( String delimiter )
    {
        return switch ( delimiter )
        {
            case "\n" -> DelimiterName.LF;
            case "\r\n" -> DelimiterName.CRLF;
            case "\r" -> DelimiterName.CR;
            default -> DelimiterName.OTHER;
        };
    }

    /**
     * Which scope actually supplied the value.
     * <p>
     * Each scope is queried on its own with no default, because the resolved value
     * cannot say where it came from - a project set to the same delimiter as the
     * workspace is indistinguishable from one that inherits it, and those are different
     * answers to "may I change the workspace setting".
     */
    private static Source sourceOf( IProject project )
    {
        if ( project != null && new ProjectScope( project ).getNode( Platform.PI_RUNTIME )
                .get( Platform.PREF_LINE_SEPARATOR, null ) != null )
        {
            return Source.PROJECT;
        }
        if ( InstanceScope.INSTANCE.getNode( Platform.PI_RUNTIME )
                .get( Platform.PREF_LINE_SEPARATOR, null ) != null )
        {
            return Source.WORKSPACE;
        }
        return Source.DEFAULT;
    }
}
