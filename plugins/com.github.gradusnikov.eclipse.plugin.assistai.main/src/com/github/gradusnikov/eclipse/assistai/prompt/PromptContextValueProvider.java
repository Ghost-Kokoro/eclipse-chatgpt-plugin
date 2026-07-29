package com.github.gradusnikov.eclipse.assistai.prompt;

import java.util.Objects;
import java.util.concurrent.Callable;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IPath;
import org.eclipse.e4.core.di.annotations.Creatable;

import com.github.gradusnikov.eclipse.assistai.mcp.McpJson;
import com.github.gradusnikov.eclipse.assistai.mcp.services.CodeAnalysisService;
import com.github.gradusnikov.eclipse.assistai.mcp.services.ConsoleService;
import com.github.gradusnikov.eclipse.assistai.mcp.services.EditorService;
import com.github.gradusnikov.eclipse.assistai.mcp.services.GitService;
import com.github.gradusnikov.eclipse.assistai.mcp.results.ConsoleOutputResponse;
import com.github.gradusnikov.eclipse.assistai.resources.CachedResource;
import com.github.gradusnikov.eclipse.assistai.resources.ResourceCache;
import com.github.gradusnikov.eclipse.assistai.resources.ResourceDescriptor;
import com.github.gradusnikov.eclipse.assistai.resources.ResourceReadResult;
import com.github.gradusnikov.eclipse.assistai.tools.UISynchronizeCallable;

import jakarta.inject.Inject;

@Creatable
public class PromptContextValueProvider 
{
    
    private static final String GIT_DIFF = "gitDiff";
    private static final String CONSOLE_OUTPUT = "consoleOutput";
    private static final String ERRORS = "errors";
    private static final String SELECTED_CONTENT = "selectedContent";
    private static final String CURRENT_FILE_CONTENT = "currentFileContent";
    private static final String CURRENT_FILE_PATH = "currentFilePath";
    private static final String CURRENT_FILE_NAME = "currentFileName";
    private static final String CURRENT_PROJECT_NAME = "currentProjectName";
    
    // Completion-specific context keys
    private static final String FILE_EXTENSION = "fileExtension";
    private static final String CODE_BEFORE_CURSOR = "codeBeforeCursor";
    private static final String CODE_AFTER_CURSOR = "codeAfterCursor";
    private static final String CURSOR_LINE = "cursorLine";
    private static final String CURSOR_COLUMN = "cursorColumn";
    
    @Inject
    ILog logger;
    @Inject
    UISynchronizeCallable uiSync;
	@Inject
	private CodeAnalysisService codeAnalysisService;
	@Inject
	private EditorService editorService;
	@Inject
	private ConsoleService consoleService;
	@Inject
	private GitService gitService;
	@Inject
	private ResourceCache resourceCache;
	
	public String getContextValue( String key )
	{
	    Objects.requireNonNull( key );
	    
	    
	    return switch (  key ) {
            case CURRENT_PROJECT_NAME -> safeGetString( () -> editorService.getCurrentlyOpenedFile().map( IFile::getProject ).map(IProject::getName).orElse( "" ) );
	        case CURRENT_FILE_PATH -> safeGetString( () -> editorService.getCurrentlyOpenedFile().map( IFile::getProjectRelativePath ).map(IPath::toString).orElse( "" ) );
            case CURRENT_FILE_NAME -> safeGetString( () -> editorService.getCurrentlyOpenedFile().map( IFile::getName ).orElse( "" ) );
	        case CURRENT_FILE_CONTENT -> safeGetString( this::currentFileContent );
	        // The selected text itself, which is what ${selectedContent} promises. It
	        // used to be a banner, a "Selection from line: … to: …" line and a second
	        // copy of the same lines re-read from disk and numbered - the renderer
	        // register item 19 deleted twice already.
	        case SELECTED_CONTENT -> safeGetString(() -> editorService.readEditorSelection().content() );
	        case ERRORS -> safeGetString( () -> McpJson.toJson( codeAnalysisService.getCompilationErrors( getContextValue(CURRENT_PROJECT_NAME), "ERROR", -1 ) ) );
	        case CONSOLE_OUTPUT -> safeGetString( this::consoleOutput ) ;
            case GIT_DIFF -> safeGetString( () -> gitService.getCurrentDiff() ) ;
            case FILE_EXTENSION -> safeGetString( () -> editorService.getCurrentlyOpenedFile().map( IFile::getFileExtension ).orElse( "" ) );
            case CODE_BEFORE_CURSOR -> safeGetString( () -> editorService.getCodeBeforeCursor() );
            case CODE_AFTER_CURSOR -> safeGetString( () -> editorService.getCodeAfterCursor() );
            case CURSOR_LINE -> safeGetString( () -> editorService.getCursorLine() );
            case CURSOR_COLUMN -> safeGetString( () -> editorService.getCursorColumn() );
	        default -> {
                logger.warn("Unknown context key: " + key);
                yield "";
	        }
	    };
	}
	
    /**
     * The open editor's file, cached and referenced rather than inlined.
     * <p>
     * Exact content goes into the cache: the &lt;resources&gt; element already carries
     * the uri, name and version that the old banner repeated, and a second shape for
     * the same file is exactly what register item 19 removed.
     */
    private String currentFileContent()
    {
        var file = editorService.getCurrentlyOpenedFile();
        if ( file.isEmpty() )
        {
            return "";
        }
        ResourceReadResult read = editorService.readCurrentlyOpenedFile();
        if ( !read.isCacheable() )
        {
            return read.content();
        }
        return cacheReference(
                ResourceDescriptor.fromWorkspaceFile( file.get(), "getCurrentlyOpenedFile" ), read.content() );
    }

    /**
     * Every console, each cached under its own console URI.
     * <p>
     * One cache entry per console rather than one blob of all of them: the cache is
     * keyed by URI, and concatenating several consoles under one key would be a second
     * rendering of the payload - the thing this refactoring exists to remove.
     */
    private String consoleOutput()
    {
        ConsoleOutputResponse response = consoleService.getConsoleOutput( null, 100, true );
        StringBuilder references = new StringBuilder();
        for ( ConsoleOutputResponse.ConsoleOutput console : response.consoles() )
        {
            if ( console.isEmpty() )
            {
                continue;
            }
            references.append( cacheReference(
                    ResourceDescriptor.forConsole( console.consoleName(), "getConsoleOutput" ),
                    console.text() ) ).append( "\n" );
        }
        return references.toString();
    }

    /** Puts content in the cache and yields the reference that stands in for it. */
    private String cacheReference( ResourceDescriptor descriptor, String content )
    {
        CachedResource cached = resourceCache.put( descriptor, content );
        if ( cached == null )
        {
            return content;
        }
        return String.format(
                "[Resource cached: %s (version %d, ~%d tokens)]\n"
                        + "Content available in <resources> block at top of context.",
                cached.descriptor().uri(),
                cached.version(),
                cached.estimateTokens() );
    }

	
    /**
     * Safely executes a supplier function and handles any exceptions.
     * 
     * @param supplier The function that provides the context value
     * @return The context value, or empty string if an error occurs
     */
	private String safeGetString( Callable<String> supplier )
	{
	    try
	    {
	        return uiSync.syncCall( supplier );
	    }
	    catch ( Exception e )
	    {
	        logger.error( e.getMessage(), e );
	        return "";
	    }
	}
	
	
	
	
}
