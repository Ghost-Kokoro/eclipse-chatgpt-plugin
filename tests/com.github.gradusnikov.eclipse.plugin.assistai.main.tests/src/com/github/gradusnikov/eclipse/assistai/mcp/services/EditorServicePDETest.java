package com.github.gradusnikov.eclipse.assistai.mcp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.e4.core.contexts.ContextInjectionFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.util.tracker.ServiceTracker;

import com.github.gradusnikov.eclipse.assistai.Activator;
import com.github.gradusnikov.eclipse.assistai.mcp.results.DiagnosticCode;
import com.github.gradusnikov.eclipse.assistai.resources.ContentRange;
import com.github.gradusnikov.eclipse.assistai.resources.ResourceReadResult;
import com.github.gradusnikov.eclipse.assistai.services.AiIgnoreService;

/**
 * {@code getEditorSelection}, against a real editor with a real selection.
 * <p>
 * The tool used to compute 1-based line numbers and hand them to a formatter whose
 * contract was 0-based and which then printed {@code i + 1}, so the excerpt was shifted
 * by one line and labelled with numbers shifted by one again - and a selection touching
 * the file's last line threw {@code IllegalArgumentException("Illegal line range")}.
 * Neither could be caught without an editor open, which is why the tool shipped that
 * way; both are asserted here.
 */
public class EditorServicePDETest
{
    /** Deliberately without a trailing newline, so "the last line" is line 3. */
    private static final String SOURCE = "alpha\nbravo\ncharlie";

    private String                    testProjectName;
    private IProject                  project;
    private IFile                     file;
    private EditorService             editorService;
    private final NullProgressMonitor monitor = new NullProgressMonitor();

    @BeforeEach
    public void beforeEach() throws CoreException
    {
        Assumptions.assumeTrue( PlatformUI.isWorkbenchRunning(),
                "getEditorSelection is about the workbench; there is nothing to assert without one" );

        BundleContext bundleContext = FrameworkUtil.getBundle( EditorServicePDETest.class ).getBundleContext();
        ServiceTracker<IWorkspace, IWorkspace> workspaceTracker =
                new ServiceTracker<>( bundleContext, IWorkspace.class, null );
        workspaceTracker.open();
        IWorkspaceRoot root = workspaceTracker.getService().getRoot();

        testProjectName = "EditorSelectionTestProject_" + UUID.randomUUID();
        project = root.getProject( testProjectName );
        IProjectDescription description = root.getWorkspace().newProjectDescription( testProjectName );
        project.create( description, monitor );
        project.open( monitor );

        file = project.getFile( new Path( "notes.txt" ) );
        file.create( new ByteArrayInputStream( SOURCE.getBytes() ), true, monitor );

        // The application context already provides UISynchronize, which EditorService
        // needs to reach the display thread.
        IEclipseContext context = PlatformUI.getWorkbench().getService( IEclipseContext.class ).createChild();
        context.set( ILog.class, Activator.getDefault().getLog() );
        context.set( AiIgnoreService.class, ContextInjectionFactory.make( AiIgnoreService.class, context ) );
        editorService = ContextInjectionFactory.make( EditorService.class, context );
    }

    @AfterEach
    public void afterEach() throws CoreException
    {
        if ( PlatformUI.isWorkbenchRunning() )
        {
            PlatformUI.getWorkbench().getDisplay().syncExec( () -> {
                IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                if ( window != null && window.getActivePage() != null )
                {
                    window.getActivePage().closeAllEditors( false );
                }
            } );
        }
        if ( project != null && project.exists() )
        {
            project.delete( true, true, monitor );
        }
    }

    // ---- the defect ------------------------------------------------------

    @Test
    public void reportsASelectionOnTheLastLineInsteadOfThrowing()
    {
        select( SOURCE.indexOf( "charlie" ), "charlie".length() );

        ResourceReadResult result = editorService.readEditorSelection();

        assertNotEquals( ResourceReadResult.ReadStatus.FAILED, result.status(), diagnosticsOf( result ) );
        assertEquals( "charlie", result.content() );
        assertEquals( 3, result.returnedRange().startLine(), "the last line is line 3, not line 4" );
        assertEquals( 3, result.returnedRange().endLine() );
        assertEquals( 1, result.returnedRange().startColumn() );
        assertEquals( 8, result.returnedRange().endColumn(), "a column is part of a selection, and now survives" );
        assertEquals( 3, result.totalLines() );
    }

    @Test
    public void reportsTheLineTheSelectionActuallyStartsOn()
    {
        select( SOURCE.indexOf( "bravo" ), "bravo".length() );

        ResourceReadResult result = editorService.readEditorSelection();

        assertEquals( "bravo", result.content() );
        assertEquals( 2, result.returnedRange().startLine(),
                "the excerpt used to be shifted by one line and labelled with numbers shifted by one again" );
        assertEquals( 2, result.returnedRange().endLine() );
    }

    @Test
    public void aSelectionIsAddressedTheWayTheEditingToolsAddressFiles()
    {
        select( 0, "alpha".length() );

        ResourceReadResult result = editorService.readEditorSelection();

        assertEquals( testProjectName, result.projectName() );
        assertEquals( "notes.txt", result.filePath() );
        assertTrue( result.version().isKnown(),
                "the selection carries the stamp an edit passes as expectedModificationStamp" );
    }

    @Test
    public void nothingSelectedIsAnOkResultWithAZeroWidthRange()
    {
        select( SOURCE.indexOf( "bravo" ), 0 );

        ResourceReadResult result = editorService.readEditorSelection();

        assertNotEquals( ResourceReadResult.ReadStatus.FAILED, result.status(),
                "a caret is an ordinary answer, not a failure" );
        assertEquals( "", result.content() );
        ContentRange range = result.returnedRange();
        assertTrue( range.isEmpty(), "a caret is a zero-width range: " + range );
        assertEquals( 2, range.startLine() );
    }

    @Test
    public void selectingEverythingIsACompleteRead()
    {
        select( 0, SOURCE.length() );

        ResourceReadResult result = editorService.readEditorSelection();

        assertEquals( ResourceReadResult.ReadStatus.OK, result.status(),
                "PARTIAL means 'not the whole file', and this is the whole file" );
        assertEquals( SOURCE, result.content() );
        assertFalse( result.truncated(), "nothing was cut short; the selection came back whole" );
    }

    @Test
    public void reportsNoEditorAsACodeRatherThanAnException()
    {
        closeAllEditors();

        ResourceReadResult result = editorService.readEditorSelection();

        assertEquals( ResourceReadResult.ReadStatus.FAILED, result.status() );
        assertEquals( DiagnosticCode.RESOURCE_NOT_FOUND, result.diagnostics().get( 0 ).code(),
                "no editor open is a state of the workbench, reported as a field" );
        assertEquals( "", result.content(), "a failure must not look like an empty selection" );
    }

    // ---- fixture ---------------------------------------------------------

    /** Opens the test file and selects an offset range in it, on the display thread. */
    private void select( int offset, int length )
    {
        AtomicReference<Exception> failure = new AtomicReference<>();
        PlatformUI.getWorkbench().getDisplay().syncExec( () -> {
            try
            {
                IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                Assumptions.assumeTrue( window != null && window.getActivePage() != null,
                        "no active workbench page in this harness" );
                IWorkbenchPage page = window.getActivePage();
                ITextEditor editor = (ITextEditor) IDE.openEditor( page, file, "org.eclipse.ui.DefaultTextEditor" );
                page.activate( editor );
                editor.selectAndReveal( offset, length );
            }
            catch ( Exception e )
            {
                failure.set( e );
            }
        } );
        if ( failure.get() != null )
        {
            throw new AssertionError( "could not open and select in the editor", failure.get() );
        }
    }

    private void closeAllEditors()
    {
        PlatformUI.getWorkbench().getDisplay().syncExec( () -> {
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if ( window != null && window.getActivePage() != null )
            {
                window.getActivePage().closeAllEditors( false );
            }
        } );
    }

    private static String diagnosticsOf( ResourceReadResult result )
    {
        return String.valueOf( result.diagnostics() );
    }
}
