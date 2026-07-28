package com.github.gradusnikov.eclipse.assistai.mcp.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.gradusnikov.eclipse.assistai.Activator;
import com.github.gradusnikov.eclipse.assistai.mcp.McpJson;
import com.github.gradusnikov.eclipse.assistai.mcp.McpOutputSchemas;
import com.github.gradusnikov.eclipse.assistai.mcp.annotations.Tool;
import org.eclipse.core.runtime.ILog;
import org.eclipse.e4.core.contexts.ContextInjectionFactory;
import org.eclipse.e4.core.contexts.EclipseContextFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.ui.PlatformUI;

import com.github.gradusnikov.eclipse.assistai.mcp.results.DiagnosticCode;
import org.eclipse.core.runtime.ILog;
import org.eclipse.e4.core.contexts.ContextInjectionFactory;
import org.eclipse.e4.core.contexts.EclipseContextFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.ui.PlatformUI;

import com.github.gradusnikov.eclipse.assistai.mcp.results.DiagnosticCode;
import com.github.gradusnikov.eclipse.assistai.mcp.results.DiffResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.FileHistoryResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.services.LocalHistoryService;
import com.github.gradusnikov.eclipse.assistai.resources.EditResult;
import com.github.gradusnikov.eclipse.assistai.services.AiIgnoreService;
import com.github.gradusnikov.eclipse.assistai.mcp.results.FileHistoryResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.services.LocalHistoryService;
import com.github.gradusnikov.eclipse.assistai.resources.EditResult;
import com.github.gradusnikov.eclipse.assistai.services.AiIgnoreService;
import com.github.gradusnikov.eclipse.assistai.resources.CachedResource;
import com.github.gradusnikov.eclipse.assistai.mcp.results.DiagnosticCode;
import com.github.gradusnikov.eclipse.assistai.resources.ResourceCache;
import com.github.gradusnikov.eclipse.assistai.resources.ResourceDescriptor;
import com.github.gradusnikov.eclipse.assistai.resources.ResourceReadResult;
import com.github.gradusnikov.eclipse.assistai.resources.ResourceVersion;
import com.github.gradusnikov.eclipse.assistai.resources.SourceOrigin;
import com.github.gradusnikov.eclipse.assistai.tools.UnifiedDiffs;

/**
 * The eclipse-context tools, after they stopped returning prose.
 * <p>
 * Two things here are easy to break silently. A tool whose {@code outputType} drifts
 * from its return type advertises a schema for something it does not send, and
 * nothing complains until a client validates. And the cache used to reformat content
 * on its way in - banner plus line numbers - which made the cached copy differ from
 * the file it came from without any error.
 */
public class EclipseContextMcpServerPDETest
{
    private static final String TEST_PROJECT_NAME = "ContextToolsTestProject";
    private static final String SOURCE = "package p;\n\npublic class A\n{\n    void run() {}\n}\n";

    private final NullProgressMonitor monitor = new NullProgressMonitor();
    private IProject project;
    private IFile file;

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

        file = project.getFile( new Path( "A.java" ) );
        file.create( new ByteArrayInputStream( SOURCE.getBytes( StandardCharsets.UTF_8 ) ), true, monitor );
    }

    @AfterEach
    public void afterEach() throws CoreException
    {
        if ( project != null && project.exists() )
        {
            project.delete( true, true, monitor );
        }
    }

    // ---- the schema a client is handed ------------------------------------

    @Test
    public void everyToolAdvertisesTheTypeItReturns() throws Exception
    {
        for ( Method method : EclipseContextMcpServer.class.getDeclaredMethods() )
        {
            Tool tool = method.getAnnotation( Tool.class );
            if ( tool == null )
            {
                continue;
            }
            assertNotEquals( Void.class, tool.outputType(),
                    tool.name() + " returns a record but advertises no outputType, so a client "
                            + "receives structured content it was never given a schema for" );
            assertEquals( method.getReturnType(), tool.outputType(),
                    tool.name() + " advertises a schema for a type it does not return" );
        }
    }

    /**
     * A context holding just what {@link LocalHistoryService} injects. {@code
     * UISynchronize} is only reached on the success path of a restore, which these
     * tests deliberately do not take.
     */
    private static IEclipseContext diContext()
    {
        IEclipseContext context = EclipseContextFactory.create();
        context.set( ILog.class, Activator.getDefault().getLog() );
        context.set( UISynchronize.class, PlatformUI.getWorkbench().getService( UISynchronize.class ) );
        context.set( AiIgnoreService.class, ContextInjectionFactory.make( AiIgnoreService.class, context ) );
        return context;
    }

    @Test
    public void theHistoryToolsReportFailuresInTheFieldTheyAdvertise() throws Exception
    {
        // Each of these declares a diagnostics list in its schema. They used to throw
        // instead of filling it, so a client reading structuredContent - which the
        // advertised outputSchema tells it to do - got nothing at all on failure and had
        // to fall back to matching English.
        LocalHistoryService service = ContextInjectionFactory.make( LocalHistoryService.class, diContext() );

        FileHistoryResponse history = service.getFileHistory( "NoSuchProject", "A.java", null );
        assertEquals( DiagnosticCode.PROJECT_NOT_FOUND, history.diagnostics().get( 0 ).code() );
        assertEquals( 0, history.totalVersions() );

        ResourceReadResult content = service.getFileHistoryContent( TEST_PROJECT_NAME, "Missing.java", "1" );
        assertEquals( ResourceReadResult.ReadStatus.FAILED, content.status() );
        assertEquals( DiagnosticCode.RESOURCE_NOT_FOUND, content.diagnostics().get( 0 ).code() );

        DiffResponse diff = service.compareWithHistory( TEST_PROJECT_NAME, "A.java", "not-a-number" );
        assertEquals( DiagnosticCode.INVALID_RANGE, diff.diagnostics().get( 0 ).code() );
        assertFalse( diff.identical(),
                "a comparison that could not be made is not a comparison that found no differences" );

        EditResult restore = service.restoreFileVersion( TEST_PROJECT_NAME, "A.java", "999" );
        assertEquals( EditResult.EditStatus.REJECTED, restore.status() );
        assertFalse( restore.diagnostics().isEmpty() );
    }

    @Test
    public void aDiffSerializesExactlyTheFieldsItAdvertises()
    {
        DiffResponse response = DiffResponse.of( "P", "src/A.java",
                "2026-07-27 10:00:00", ResourceVersion.UNKNOWN,
                "current", ResourceVersion.UNKNOWN,
                UnifiedDiffs.compare( "a\nb\n", "a\nc\n", 3 ) );

        @SuppressWarnings( "unchecked" )
        Map<String, Object> advertised = (Map<String, Object>) McpOutputSchemas
                .forType( DiffResponse.class ).get( "properties" );

        assertEquals( advertised.keySet(), McpJson.toMap( response ).keySet() );
    }

    // ---- the diff itself ---------------------------------------------------

    @Test
    public void countsLinesFromTheDiffRatherThanFromItsRendering()
    {
        // One line replaced by two: the rendering also contains ---/+++ headers and
        // context lines, so counting prefixes in the text would not give this.
        UnifiedDiffs.Unified diff = UnifiedDiffs.compare( "one\ntwo\nthree\n", "one\n2a\n2b\nthree\n", 3 );

        assertEquals( 1, diff.removedLines() );
        assertEquals( 2, diff.addedLines() );
        assertFalse( diff.isEmpty() );
    }

    @Test
    public void identicalContentIsAFlagNotASentence()
    {
        DiffResponse response = DiffResponse.of( "P", "src/A.java",
                "then", ResourceVersion.UNKNOWN, "current", ResourceVersion.UNKNOWN,
                UnifiedDiffs.compare( SOURCE, SOURCE, 3 ) );

        assertTrue( response.identical() );
        assertEquals( "", response.unifiedDiff(), "no differences must not be prose in the diff field" );
        assertEquals( 0, response.addedLines() );
        assertEquals( 0, response.removedLines() );
    }

    // ---- the cache ---------------------------------------------------------

    @Test
    public void theCacheStoresExactlyWhatItWasGiven()
    {
        // Guards the removal of the banner-plus-line-numbers formatting: cached
        // content is the file's content, so a caller can diff or edit against it.
        ResourceCache cache = new ResourceCache( Activator.getDefault().getLog() );
        CachedResource cached = cache.put(
                ResourceDescriptor.fromWorkspaceFile( file, "test" ), SOURCE );

        assertNotNull( cached );
        assertEquals( SOURCE, cached.content() );
    }

    @Test
    public void cachedContentIsReturnedOnTheShapeOfARead()
    {
        ResourceCache cache = new ResourceCache( Activator.getDefault().getLog() );
        ResourceReadResult read = cache
                .put( ResourceDescriptor.fromWorkspaceFile( file, "test" ), SOURCE )
                .toReadResult();

        assertEquals( ResourceReadResult.ReadStatus.OK, read.status() );
        assertEquals( SOURCE, read.content() );
        assertEquals( TEST_PROJECT_NAME, read.projectName() );
        assertEquals( "A.java", read.filePath() );
        assertEquals( SourceOrigin.WORKSPACE_SOURCE, read.origin() );
        assertTrue( read.isEditable() );
        assertEquals( file.getModificationStamp(), read.version().modificationStamp(),
                "the stamp must be the one an edit quotes as expectedModificationStamp" );
        assertEquals( 1, read.returnedRange().startLine() );
        assertTrue( read.version().inSyncWithFileSystem() );
        assertTrue( read.diagnostics().isEmpty() );
    }

    @Test
    public void aStaleCacheEntryReportsThatItIsStale() throws CoreException
    {
        ResourceCache cache = new ResourceCache( Activator.getDefault().getLog() );
        CachedResource cached = cache.put(
                ResourceDescriptor.fromWorkspaceFile( file, "test" ), SOURCE );

        // The workspace moves on without the cache being told - exactly the case that
        // used to be indistinguishable from a current copy.
        file.setContents( new ByteArrayInputStream( "changed\n".getBytes( StandardCharsets.UTF_8 ) ),
                IResource.FORCE | IResource.KEEP_HISTORY, monitor );

        ResourceReadResult read = cached.toReadResult();

        assertFalse( read.version().inSyncWithFileSystem() );
        assertEquals( DiagnosticCode.RESOURCE_OUT_OF_SYNC, read.diagnostics().get( 0 ).code() );
        assertTrue( read.diagnostics().get( 0 ).retryable(), "the caller fixes this by re-reading" );
    }

    @Test
    public void aReadRecoveredFromStructuredContentKeepsItsVersion()
    {
        ResourceCache cache = new ResourceCache( Activator.getDefault().getLog() );
        ResourceReadResult read = cache
                .put( ResourceDescriptor.fromWorkspaceFile( file, "test" ), SOURCE )
                .toReadResult();

        ResourceReadResult recovered = ResourceReadResult.fromStructuredContent( McpJson.toMap( read ) );

        assertNotNull( recovered, "the chat caches reads it recognises by shape" );
        assertEquals( read.version().modificationStamp(), recovered.version().modificationStamp() );
        assertEquals( read.content(), recovered.content() );
    }
}
