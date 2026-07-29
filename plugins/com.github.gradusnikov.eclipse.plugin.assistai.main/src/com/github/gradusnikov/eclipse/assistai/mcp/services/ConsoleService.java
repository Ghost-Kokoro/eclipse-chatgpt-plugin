
package com.github.gradusnikov.eclipse.assistai.mcp.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.runtime.ILog;
import org.eclipse.e4.core.di.annotations.Creatable;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleConstants;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.IConsoleView;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.TextConsole;

import com.github.gradusnikov.eclipse.assistai.mcp.results.ConsoleOutputResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.Diagnostic;
import com.github.gradusnikov.eclipse.assistai.mcp.results.DiagnosticCode;
import com.github.gradusnikov.eclipse.assistai.resources.ContentRange;

import jakarta.inject.Inject;

/**
 * Reads and writes the Eclipse consoles.
 */
@Creatable
public class ConsoleService 
{
    /** What a caller gets when it asks for the tail of a console without saying how much. */
    private static final int DEFAULT_MAX_LINES = 100;

    @Inject
    ILog logger;
    @Inject
    UISynchronize sync;

    /**
     * The recent output of one console, of all of them, or of the one the user is
     * looking at.
     * <p>
     * A console is read from its end, because that is where a build or a test run has
     * just written. {@code truncated} therefore means "there are earlier lines
     * {@code maxLines} left out", and raising {@code maxLines} is the only way to
     * reach them - a console has no line-range read the way a file does.
     *
     * @param consoleName a substring of the console's name; null or blank selects
     *            either all consoles or the active one
     * @param maxLines lines to take from the end of each console; null or below 1
     *            means {@value #DEFAULT_MAX_LINES}
     * @param includeAllConsoles whether to read every console rather than the active
     *            one. Ignored when {@code consoleName} names one
     */
    public ConsoleOutputResponse getConsoleOutput( String consoleName, Integer maxLines, boolean includeAllConsoles )
    {
        int lineLimit = ( maxLines == null || maxLines < 1 ) ? DEFAULT_MAX_LINES : maxLines;

        AtomicReference<ConsoleOutputResponse> result = new AtomicReference<>();
        sync.syncExec( () -> result.set( readConsoles( consoleName, lineLimit, includeAllConsoles ) ) );
        return result.get();
    }

    /**
     * Runs on the UI thread. Every outcome is a response: "there are no consoles" and
     * "no console matches that name" are states of the workbench, and a caller must
     * not have to tell them apart from a genuine failure by catching an exception.
     */
    private ConsoleOutputResponse readConsoles( String consoleName, int maxLines, boolean includeAllConsoles )
    {
        IConsoleManager consoleManager = ConsolePlugin.getDefault().getConsoleManager();
        IConsole[] consoles = consoleManager.getConsoles();

        if ( consoles.length == 0 )
        {
            return ConsoleOutputResponse.failed( 0, Diagnostic.fatal( DiagnosticCode.RESOURCE_NOT_FOUND,
                    "No consoles are open in the workbench." ) );
        }

        List<IConsole> targets = selectConsoles( consoles, consoleName, includeAllConsoles );
        if ( targets.isEmpty() )
        {
            return ConsoleOutputResponse.failed( consoles.length, Diagnostic.fatal(
                    DiagnosticCode.RESOURCE_NOT_FOUND,
                    consoleName == null || consoleName.isBlank()
                            ? "No console could be selected."
                            : "No console has a name containing '" + consoleName + "'. Open consoles: "
                                    + String.join( ", ", namesOf( consoles ) ) + "." ) );
        }

        List<ConsoleOutputResponse.ConsoleOutput> outputs = new ArrayList<>();
        for ( IConsole console : targets )
        {
            outputs.add( read( console, maxLines ) );
        }
        return ConsoleOutputResponse.of( consoles.length, outputs );
    }

    private List<IConsole> selectConsoles( IConsole[] consoles, String consoleName, boolean includeAllConsoles )
    {
        if ( consoleName != null && !consoleName.isBlank() )
        {
            return Arrays.stream( consoles ).filter( c -> c.getName().contains( consoleName ) ).toList();
        }
        if ( includeAllConsoles )
        {
            return List.of( consoles );
        }

        // The console the user is actually looking at, falling back to the first one
        // registered - which is what the console view itself shows on a fresh workbench.
        IConsole active = Optional.ofNullable( PlatformUI.getWorkbench().getActiveWorkbenchWindow() )
                .map( IWorkbenchWindow::getActivePage )
                .map( page -> page.findView( IConsoleConstants.ID_CONSOLE_VIEW ) )
                .filter( IConsoleView.class::isInstance )
                .map( view -> ( (IConsoleView) view ).getConsole() )
                .orElse( consoles[0] );

        return active == null ? List.of( consoles[0] ) : List.of( active );
    }

    /**
     * The tail of one console's document.
     * <p>
     * Lines come from {@link IDocument}'s own line tracker rather than from splitting
     * the text on {@code '\n'}, so a console whose output carries CRLF reports the
     * same line numbers as one that does not.
     */
    private ConsoleOutputResponse.ConsoleOutput read( IConsole console, int maxLines )
    {
        IDocument document = console instanceof TextConsole textConsole ? textConsole.getDocument() : null;
        if ( document == null )
        {
            // Not a text console - a memory view, an image console. It exists and it
            // holds no lines, which is different from failing to read it.
            return new ConsoleOutputResponse.ConsoleOutput(
                    console.getName(), new ContentRange( 1, 1, 1, 1 ), 0, false, "" );
        }

        try
        {
            int totalLines = document.getNumberOfLines();
            int firstLine = Math.max( 0, totalLines - maxLines );
            int offset = document.getLineOffset( firstLine );

            return new ConsoleOutputResponse.ConsoleOutput(
                    console.getName(),
                    ContentRange.ofLines( document, firstLine + 1, totalLines ),
                    totalLines,
                    firstLine > 0,
                    document.get( offset, document.getLength() - offset ) );
        }
        catch ( BadLocationException e )
        {
            // The console was written to between measuring it and reading it.
            logger.error( "Could not read console '" + console.getName() + "'", e );
            return new ConsoleOutputResponse.ConsoleOutput(
                    console.getName(), new ContentRange( 1, 1, 1, 1 ), 0, false, "" );
        }
    }

    private static List<String> namesOf( IConsole[] consoles )
    {
        return Arrays.stream( consoles ).map( IConsole::getName ).toList();
    }

    /**
     * Prints a message to a specified console.
     * 
     * @param consoleName The name of the console to print to
     * @param message The message to print
     */
    public void println(String consoleName, String message)
    {
        if (consoleName == null || consoleName.trim().isEmpty()) {
            throw new IllegalArgumentException("Console name cannot be null or empty");
        }
        
        if (message == null || message.isBlank() ) {
            return;
        }
        sync.syncExec(() -> {
            try {
                // Get or create the console
                MessageConsole console = findOrCreateConsole(consoleName);
                
                // Write to the console using MessageConsole's output stream
                console.newMessageStream().println(message);
            } catch (Exception e) {
                logger.log(org.eclipse.core.runtime.Status.error("Error writing to console: " + e.getMessage(), e));
            }
        });
    }

    /**
     * Clears the content of a specified console.
     * 
     * @param consoleName The name of the console to clear
     */
    public void clear(String consoleName)
    {
        if (consoleName == null || consoleName.trim().isEmpty()) {
            throw new IllegalArgumentException("Console name cannot be null or empty");
        }
        
        sync.syncExec(() -> {
            try {
                // Get or create the console
                MessageConsole console = findOrCreateConsole(consoleName);
                
                // Clear the console by removing it and creating a new one
                IConsoleManager consoleManager = ConsolePlugin.getDefault().getConsoleManager();
                consoleManager.removeConsoles(new IConsole[] { console });
                
                // Create a new console with the same name
                MessageConsole newConsole = new MessageConsole(consoleName, null);
                consoleManager.addConsoles(new IConsole[] { newConsole });
                
                // Show the console view
                try {
                    IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                    if (window != null && window.getActivePage() != null) {
                        IConsoleView view = (IConsoleView) window.getActivePage().showView(IConsoleConstants.ID_CONSOLE_VIEW);
                        view.display(newConsole);
                    }
                } catch (Exception e) {
                    logger.log(org.eclipse.core.runtime.Status.error("Failed to show console view", e));
                }
                
                logger.log(new org.eclipse.core.runtime.Status(org.eclipse.core.runtime.IStatus.INFO, "com.github.gradusnikov.eclipse.plugin.assistai.main", "Console '" + consoleName + "' cleared successfully."));
            } catch (Exception e) {
                logger.log(org.eclipse.core.runtime.Status.error("Error clearing console: " + e.getMessage(), e));
            }
        });
    }
    /**
     * Finds an existing console or creates a new one if it doesn't exist.
     * 
     * @param name The name of the console to find or create
     * @return The found or created MessageConsole
     * @throws PartInitException 
     */
    private MessageConsole findOrCreateConsole(String name) throws PartInitException {
        // Get the console manager
        ConsolePlugin plugin = ConsolePlugin.getDefault();
        IConsoleManager conMan = plugin.getConsoleManager();
        
        // Try to find the console
        IConsole[] existing = conMan.getConsoles();
        for (IConsole console : existing) {
            if (name.equals(console.getName()) && console instanceof MessageConsole messageConsole) {
                return messageConsole;
            }
        }
        
        // No console found, create a new one
        MessageConsole newConsole = new MessageConsole(name, null);
        conMan.addConsoles(new IConsole[] { newConsole });
        
        // Show the console view
        var page = Optional.ofNullable( PlatformUI.getWorkbench() )
                 .map( IWorkbench::getActiveWorkbenchWindow )
                 .map( IWorkbenchWindow::getActivePage )
                 .orElseThrow( () -> new PartInitException("No active page available") );
            IConsoleView view = (IConsoleView) page.showView(IConsoleConstants.ID_CONSOLE_VIEW);
            view.display(newConsole);
        
        return newConsole;
    }
}
