package com.github.gradusnikov.eclipse.assistai.mcp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.e4.core.contexts.ContextInjectionFactory;
import org.eclipse.e4.core.contexts.EclipseContextFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.ui.PlatformUI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.gradusnikov.eclipse.assistai.Activator;
import com.github.gradusnikov.eclipse.assistai.mcp.McpJson;
import com.github.gradusnikov.eclipse.assistai.mcp.McpOutputSchemas;
import com.github.gradusnikov.eclipse.assistai.mcp.results.LineDelimiterPreference;
import com.github.gradusnikov.eclipse.assistai.resources.EditResult;
import com.github.gradusnikov.eclipse.assistai.services.AiIgnoreService;
import com.github.gradusnikov.eclipse.assistai.tools.ResourceUtilities;

/**
 * Normalising line delimiters to the one Eclipse is configured to write.
 * <p>
 * The mixed-delimiter case is the one that matters and the one nothing covered:
 * {@code applyPatch} rejoins every line with a single delimiter, so a three-line change
 * to a mixed file rewrote the whole file. {@code testApplyPatchPreservesCrLfLineDelimiter}
 * passes because it uses a uniformly-CRLF file, which is exactly the case that
 * behaviour gets right.
 */
public class LineDelimiterPDETest
{
    private static final String TEST_PROJECT_NAME = "LineDelimiterTestProject";

    private final NullProgressMonitor monitor = new NullProgressMonitor();
    private IProject project;
    private CodeEditingService service;

    @BeforeEach
    public void beforeEach() throws CoreException
    {
        project = ResourcesPlugin.getWorkspace().getRoot().getProject( TEST_PROJECT_NAME );
        if ( project.exists() )
        {
            project.delete( true, true, monitor );
        }
        project.create( monitor );
        project.open( monitor );

        IEclipseContext context = EclipseContextFactory.create();
        context.set( ILog.class, Activator.getDefault().getLog() );
        context.set( UISynchronize.class, PlatformUI.getWorkbench().getService( UISynchronize.class ) );
        context.set( AiIgnoreService.class, ContextInjectionFactory.make( AiIgnoreService.class, context ) );
        service = ContextInjectionFactory.make( CodeEditingService.class, context );
    }

    @AfterEach
    public void afterEach() throws CoreException
    {
        if ( project != null && project.exists() )
        {
            project.delete( true, true, monitor );
        }
    }

    private IFile write( String name, String content ) throws CoreException
    {
        IFile file = project.getFile( new Path( name ) );
        file.create( new ByteArrayInputStream( content.getBytes( StandardCharsets.UTF_8 ) ), true, monitor );
        return file;
    }

    /** Pins the project's delimiter so the assertions do not depend on the host platform. */
    private void setProjectDelimiter( String delimiter ) throws Exception
    {
        IEclipsePreferences node = new ProjectScope( project ).getNode( Platform.PI_RUNTIME );
        node.put( Platform.PREF_LINE_SEPARATOR, delimiter );
        node.flush();
    }

    // ---- the preference ----------------------------------------------------

    @Test
    public void readsTheProjectSettingAheadOfTheWorkspace() throws Exception
    {
        setProjectDelimiter( "\r\n" );

        LineDelimiterPreference preference = service.getLineDelimiterPreference( TEST_PROJECT_NAME );

        assertEquals( "\r\n", preference.delimiter() );
        assertEquals( LineDelimiterPreference.DelimiterName.CRLF, preference.name() );
        assertEquals( LineDelimiterPreference.Source.PROJECT, preference.source(),
                "a project-specific choice must be distinguishable from an inherited one" );
    }

    @Test
    public void fallsBackPastAProjectThatSetsNothing()
    {
        LineDelimiterPreference preference = service.getLineDelimiterPreference( TEST_PROJECT_NAME );

        assertNotNull( preference.delimiter() );
        assertTrue( preference.source() == LineDelimiterPreference.Source.WORKSPACE
                || preference.source() == LineDelimiterPreference.Source.DEFAULT,
                "with nothing set on the project, the value comes from further out" );
        assertEquals( InstanceScope.INSTANCE.getNode( Platform.PI_RUNTIME )
                .get( Platform.PREF_LINE_SEPARATOR, System.lineSeparator() ), preference.delimiter() );
    }

    @Test
    public void serializesExactlyTheFieldsItAdvertises()
    {
        @SuppressWarnings( "unchecked" )
        Map<String, Object> advertised = (Map<String, Object>) McpOutputSchemas
                .forType( LineDelimiterPreference.class ).get( "properties" );

        assertEquals( advertised.keySet(),
                McpJson.toMap( service.getLineDelimiterPreference( TEST_PROJECT_NAME ) ).keySet() );
    }

    // ---- normalising -------------------------------------------------------

    @Test
    public void rewritesAMixedFileToTheConfiguredDelimiter() throws Exception
    {
        setProjectDelimiter( "\n" );
        IFile file = write( "Mixed.txt", "one\r\ntwo\nthree\rfour\n" );

        EditResult result = service.normalizeLineDelimiters( TEST_PROJECT_NAME, "Mixed.txt",
                org.eclipse.core.resources.IResource.NULL_STAMP, false );

        assertEquals( EditResult.EditStatus.APPLIED, result.status() );
        assertEquals( "one\ntwo\nthree\nfour\n", ResourceUtilities.readFileContent( file ) );
    }

    @Test
    public void leavesTheTextItselfAlone() throws Exception
    {
        setProjectDelimiter( "\r\n" );
        IFile file = write( "Text.txt", "alpha\nbeta  \n\ngamma\n" );

        service.normalizeLineDelimiters( TEST_PROJECT_NAME, "Text.txt",
                org.eclipse.core.resources.IResource.NULL_STAMP, false );

        assertEquals( "alpha\r\nbeta  \r\n\r\ngamma\r\n", ResourceUtilities.readFileContent( file ),
                "trailing spaces and blank lines are content, not formatting" );
    }

    @Test
    public void doesNotAddATerminatorToAFileThatHadNone() throws Exception
    {
        setProjectDelimiter( "\n" );
        IFile file = write( "NoFinalNewline.txt", "one\r\ntwo" );

        service.normalizeLineDelimiters( TEST_PROJECT_NAME, "NoFinalNewline.txt",
                org.eclipse.core.resources.IResource.NULL_STAMP, false );

        assertEquals( "one\ntwo", ResourceUtilities.readFileContent( file ),
                "whether a file ends with a newline is the caller's business, not ours" );
    }

    @Test
    public void aFileThatAlreadyMatchesIsNotRewritten() throws Exception
    {
        setProjectDelimiter( "\n" );
        IFile file = write( "Already.txt", "one\ntwo\n" );
        long before = file.getModificationStamp();

        EditResult result = service.normalizeLineDelimiters( TEST_PROJECT_NAME, "Already.txt",
                org.eclipse.core.resources.IResource.NULL_STAMP, false );

        assertEquals( EditResult.EditStatus.APPLIED, result.status(),
                "being already correct is the outcome the caller wanted, not a fault" );
        assertEquals( "", result.unifiedDiff() );
        assertTrue( result.affectedResources().isEmpty(), "nothing was written, so nothing changed" );
        assertTrue( result.diagnostics().isEmpty() );
        assertEquals( before, file.getModificationStamp(), "the file must not be touched at all" );
    }

    @Test
    public void previewReportsTheChangeWithoutWritingIt() throws Exception
    {
        setProjectDelimiter( "\n" );
        IFile file = write( "Preview.txt", "one\r\ntwo\r\n" );

        EditResult result = service.normalizeLineDelimiters( TEST_PROJECT_NAME, "Preview.txt",
                org.eclipse.core.resources.IResource.NULL_STAMP, true );

        assertEquals( EditResult.EditStatus.PREVIEW, result.status() );
        assertEquals( "one\r\ntwo\r\n", ResourceUtilities.readFileContent( file ) );
    }

    @Test
    public void aStaleStampIsRejected() throws Exception
    {
        setProjectDelimiter( "\n" );
        write( "Stale.txt", "one\r\ntwo\r\n" );

        EditResult result = service.normalizeLineDelimiters( TEST_PROJECT_NAME, "Stale.txt", 1L, false );

        assertEquals( EditResult.EditStatus.REJECTED, result.status() );
        assertFalse( result.diagnostics().isEmpty() );
    }
}
