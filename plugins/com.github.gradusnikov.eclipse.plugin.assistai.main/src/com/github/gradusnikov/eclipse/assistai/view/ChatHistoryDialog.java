package com.github.gradusnikov.eclipse.assistai.view;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.github.gradusnikov.eclipse.assistai.chat.ChatHistoryRepository.ChatHistorySession;
import com.github.gradusnikov.eclipse.assistai.chat.ChatHistoryRepository.PersistedMessage;

/**
 * A dialog that lets the user browse, preview, load or delete past chat
 * sessions.
 *
 * <p>Layout: a horizontal {@link SashForm} with a session list on the left and
 * a read-only text preview on the right.  Three custom buttons sit in the
 * button bar: <em>Load</em>, <em>Delete</em> and <em>Close</em>.</p>
 */
public class ChatHistoryDialog extends Dialog
{
    // Button ids
    private static final int LOAD_ID   = IDialogConstants.CLIENT_ID + 1;
    private static final int DELETE_ID = IDialogConstants.CLIENT_ID + 2;

    private static final DateTimeFormatter STORED_FMT  =
            DateTimeFormatter.ofPattern( "yyyyMMdd-HHmmss" );
    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern( "yyyy-MM-dd  HH:mm:ss" );

    private static final int PREVIEW_MAX_CHARS = 2000;

    private final List<ChatHistorySession> sessions;

    private TableViewer tableViewer;
    private Text        previewText;

    /** The session the user confirmed with Load, or {@code null}. */
    private ChatHistorySession selectedSession;

    /**
     * @param parentShell the shell to attach this dialog to
     * @param sessions    all sessions to display (most-recent first)
     */
    public ChatHistoryDialog( Shell parentShell, List<ChatHistorySession> sessions )
    {
        super( parentShell );
        this.sessions = sessions;
        setShellStyle( getShellStyle() | SWT.RESIZE );
    }

    @Override
    protected void configureShell( Shell shell )
    {
        super.configureShell( shell );
        shell.setText( "Chat History" );
        shell.setMinimumSize( 700, 450 );
    }

    // -------------------------------------------------------------------------
    // UI construction
    // -------------------------------------------------------------------------

    @Override
    protected Control createDialogArea( Composite parent )
    {
        Composite area = (Composite) super.createDialogArea( parent );
        GridLayout layout = new GridLayout( 1, false );
        layout.marginWidth  = 8;
        layout.marginHeight = 8;
        area.setLayout( layout );

        SashForm sash = new SashForm( area, SWT.HORIZONTAL );
        sash.setLayoutData( new GridData( SWT.FILL, SWT.FILL, true, true ) );

        createSessionList( sash );
        createPreviewPanel( sash );

        sash.setWeights( new int[]{ 40, 60 } );

        // Populate with data and select the first row
        tableViewer.setInput( sessions );
        if ( !sessions.isEmpty() )
        {
            tableViewer.getTable().select( 0 );
            updatePreview( sessions.get( 0 ) );
        }
        updateButtons();

        return area;
    }

    /** Builds the session table on the left side of the sash. */
    private void createSessionList( Composite parent )
    {
        Composite listPanel = new Composite( parent, SWT.NONE );
        TableColumnLayout colLayout = new TableColumnLayout();
        listPanel.setLayout( colLayout );

        tableViewer = new TableViewer( listPanel,
                SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE | SWT.V_SCROLL );
        tableViewer.getTable().setHeaderVisible( true );
        tableViewer.getTable().setLinesVisible( true );

        // Column: date
        TableViewerColumn dateCol = new TableViewerColumn( tableViewer, SWT.NONE );
        dateCol.getColumn().setText( "Date" );
        dateCol.setLabelProvider( new ColumnLabelProvider()
        {
            @Override
            public String getText( Object element )
            {
                if ( element instanceof ChatHistorySession s )
                {
                    return formatTimestamp( s.createdAt() );
                }
                return "";
            }
        } );
        colLayout.setColumnData( dateCol.getColumn(), new ColumnWeightData( 35, 130, true ) );

        // Column: title
        TableViewerColumn titleCol = new TableViewerColumn( tableViewer, SWT.NONE );
        titleCol.getColumn().setText( "Session" );
        titleCol.setLabelProvider( new ColumnLabelProvider()
        {
            @Override
            public String getText( Object element )
            {
                if ( element instanceof ChatHistorySession s )
                {
                    return s.title();
                }
                return "";
            }
        } );
        colLayout.setColumnData( titleCol.getColumn(), new ColumnWeightData( 50, 150, true ) );

        // Column: message count
        TableViewerColumn countCol = new TableViewerColumn( tableViewer, SWT.RIGHT );
        countCol.getColumn().setText( "Msgs" );
        countCol.setLabelProvider( new ColumnLabelProvider()
        {
            @Override
            public String getText( Object element )
            {
                if ( element instanceof ChatHistorySession s )
                {
                    return String.valueOf( s.messages() == null ? 0 : s.messages().size() );
                }
                return "0";
            }
        } );
        colLayout.setColumnData( countCol.getColumn(), new ColumnWeightData( 15, 50, true ) );

        tableViewer.setContentProvider( ArrayContentProvider.getInstance() );

        // Update preview and button state on selection change
        tableViewer.addSelectionChangedListener( event -> {
            ChatHistorySession s = getSelectedSession();
            updatePreview( s );
            updateButtons();
        } );

        // Double-click loads the session immediately
        tableViewer.addDoubleClickListener( event -> {
            if ( getSelectedSession() != null )
            {
                buttonPressed( LOAD_ID );
            }
        } );
    }

    /** Builds the read-only preview text area on the right side. */
    private void createPreviewPanel( Composite parent )
    {
        Composite panel = new Composite( parent, SWT.NONE );
        panel.setLayout( new GridLayout( 1, false ) );

        previewText = new Text( panel,
                SWT.BORDER | SWT.MULTI | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL );
        previewText.setLayoutData( new GridData( SWT.FILL, SWT.FILL, true, true ) );
        previewText.setBackground( panel.getDisplay().getSystemColor( SWT.COLOR_LIST_BACKGROUND ) );
    }

    // -------------------------------------------------------------------------
    // Button bar
    // -------------------------------------------------------------------------

    @Override
    protected void createButtonsForButtonBar( Composite parent )
    {
        createButton( parent, LOAD_ID,   "Load",   true  );
        createButton( parent, DELETE_ID, "Delete", false );
        createButton( parent, IDialogConstants.CANCEL_ID, IDialogConstants.CLOSE_LABEL, false );
    }

    @Override
    protected void buttonPressed( int buttonId )
    {
        if ( buttonId == LOAD_ID )
        {
            selectedSession = getSelectedSession();
            setReturnCode( OK );
            close();
        }
        else if ( buttonId == DELETE_ID )
        {
            ChatHistorySession s = getSelectedSession();
            if ( s != null && confirmDelete( s ) )
            {
                sessions.remove( s );
                tableViewer.refresh();
                // Select next row if available
                int count = tableViewer.getTable().getItemCount();
                if ( count > 0 )
                {
                    tableViewer.getTable().select( 0 );
                    updatePreview( sessions.isEmpty() ? null : sessions.get( 0 ) );
                }
                else
                {
                    updatePreview( null );
                }
                updateButtons();
                // Propagate deletion to caller via return code trick:
                // we set a special return code so the presenter knows
                // a deletion occurred even if no Load was pressed.
                setReturnCode( IDialogConstants.INTERNAL_ID );
            }
        }
        else
        {
            super.buttonPressed( buttonId );
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Returns the currently highlighted session, or {@code null}. */
    ChatHistorySession getSelectedSession()
    {
        IStructuredSelection sel = tableViewer.getStructuredSelection();
        if ( sel.isEmpty() )
        {
            return null;
        }
        Object first = sel.getFirstElement();
        return first instanceof ChatHistorySession s ? s : null;
    }

    /**
     * Returns the session the user loaded, or {@code null} when the dialog was
     * closed without loading.
     */
    public ChatHistorySession getLoadedSession()
    {
        return selectedSession;
    }

    /**
     * Returns a snapshot of the session list at the time the dialog was closed.
     * The presenter uses this to perform any deletions the user requested.
     */
    public List<ChatHistorySession> getRemainingSessionIds()
    {
        return sessions;
    }

    private void updateButtons()
    {
        boolean hasSelection = getSelectedSession() != null;
        setButtonState( LOAD_ID,   hasSelection );
        setButtonState( DELETE_ID, hasSelection );
    }

    private void setButtonState( int buttonId, boolean enabled )
    {
        var btn = getButton( buttonId );
        if ( btn != null )
        {
            btn.setEnabled( enabled );
        }
    }

    /** Renders a plain-text transcript of the session into the preview area. */
    private void updatePreview( ChatHistorySession session )
    {
        if ( previewText == null || previewText.isDisposed() )
        {
            return;
        }
        if ( session == null || session.messages() == null || session.messages().isEmpty() )
        {
            previewText.setText( "(no messages)" );
            return;
        }

        StringBuilder sb = new StringBuilder();
        int totalChars = 0;

        for ( PersistedMessage msg : session.messages() )
        {
            if ( msg.content() == null || msg.content().isBlank() )
            {
                continue;
            }
            String label = "user".equals( msg.role() ) ? "You" : "Assistant";
            String line  = "── " + label + " ─────────────────────────────────────────────\n";
            sb.append( line );

            String content = msg.content();
            int remaining  = PREVIEW_MAX_CHARS - totalChars;
            if ( remaining <= 0 )
            {
                sb.append( "  [… more messages not shown]\n" );
                break;
            }
            if ( content.length() > remaining )
            {
                sb.append( content, 0, remaining ).append( "…\n" );
                totalChars += remaining;
            }
            else
            {
                sb.append( content ).append( "\n" );
                totalChars += content.length();
            }
            sb.append( "\n" );
        }

        previewText.setText( sb.toString() );
    }

    private boolean confirmDelete( ChatHistorySession session )
    {
        return MessageDialog.openConfirm(
                getShell(),
                "Delete Session",
                "Delete session \"" + session.title() + "\"?\n\nThis cannot be undone." );
    }

    private String formatTimestamp( String raw )
    {
        if ( raw == null )
        {
            return "";
        }
        try
        {
            LocalDateTime dt = LocalDateTime.parse( raw, STORED_FMT );
            return dt.format( DISPLAY_FMT );
        }
        catch ( DateTimeParseException e )
        {
            return raw;
        }
    }
}
