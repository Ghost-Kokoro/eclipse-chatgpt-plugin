package com.github.gradusnikov.eclipse.assistai.chat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IPath;
import org.eclipse.e4.core.di.annotations.Creatable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.gradusnikov.eclipse.assistai.Activator;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Persists and retrieves chat history sessions.
 *
 * <p>Each session is stored as a single JSON file under the plugin's state
 * location ({@code .metadata/.plugins/<bundle-id>/history/}).  File names
 * embed an ISO-like timestamp so a plain directory listing already comes back
 * in chronological order.</p>
 */
@Creatable
@Singleton
public class ChatHistoryRepository
{
    private static final String    HISTORY_DIR      = "history";
    private static final String    FILE_SUFFIX      = ".json";
    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern( "yyyyMMdd-HHmmss" );
    private static final int       TITLE_MAX_LEN    = 60;

    @Inject
    private ILog logger;

    /** The id of the session that is currently open in the view. */
    private String currentSessionId;

    private Path historyDir;

    private final ObjectMapper mapper;

    public ChatHistoryRepository()
    {
        mapper = new ObjectMapper();
        mapper.enable( SerializationFeature.INDENT_OUTPUT );
    }

    @PostConstruct
    public void init()
    {
        try
        {
            IPath statePath = Activator.getDefault().getStateLocation();
            historyDir = statePath.toFile().toPath().resolve( HISTORY_DIR );
            Files.createDirectories( historyDir );
        }
        catch ( Exception e )
        {
            logger.error( "Failed to initialise chat history directory", e );
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Persists the current conversation.  If the session was previously saved
     * the existing file is overwritten; otherwise a new file is created.
     *
     * @param messages the live conversation messages
     */
    public void saveCurrentSession( List<ChatMessage> messages )
    {
        if ( messages == null || messages.isEmpty() )
        {
            return;
        }
        if ( currentSessionId == null )
        {
            currentSessionId = UUID.randomUUID().toString();
        }
        saveSession( currentSessionId, messages );
    }

    /** Forgets the current session id so the next save creates a fresh file. */
    public void startNewSession()
    {
        currentSessionId = null;
    }

    /**
     * Returns all saved sessions, most-recent first.
     *
     * @return an unmodifiable list of {@link ChatHistorySession} objects
     */
    public List<ChatHistorySession> listSessions()
    {
        if ( historyDir == null )
        {
            return Collections.emptyList();
        }
        File dir = historyDir.toFile();
        File[] files = dir.listFiles( f -> f.isFile() && f.getName().endsWith( FILE_SUFFIX ) );
        if ( files == null || files.length == 0 )
        {
            return Collections.emptyList();
        }

        // Sort descending by file name (timestamp prefix guarantees correct order)
        Arrays.sort( files, ( a, b ) -> b.getName().compareTo( a.getName() ) );

        List<ChatHistorySession> sessions = new ArrayList<>();
        for ( File f : files )
        {
            try
            {
                ChatHistorySession session = mapper.readValue( f, ChatHistorySession.class );
                sessions.add( session );
            }
            catch ( IOException e )
            {
                logger.error( "Failed to read history file: " + f.getName(), e );
            }
        }
        return Collections.unmodifiableList( sessions );
    }

    /**
     * Deletes the history file for the given session id.
     *
     * @param sessionId the id of the session to delete
     */
    public void deleteSession( String sessionId )
    {
        if ( historyDir == null || sessionId == null )
        {
            return;
        }
        File dir = historyDir.toFile();
        File[] files = dir.listFiles( f -> f.isFile() && f.getName().endsWith( FILE_SUFFIX ) );
        if ( files == null )
        {
            return;
        }
        for ( File f : files )
        {
            try
            {
                ChatHistorySession session = mapper.readValue( f, ChatHistorySession.class );
                if ( sessionId.equals( session.id() ) )
                {
                    f.delete();
                    if ( sessionId.equals( currentSessionId ) )
                    {
                        currentSessionId = null;
                    }
                    return;
                }
            }
            catch ( IOException e )
            {
                logger.error( "Failed to read history file while deleting: " + f.getName(), e );
            }
        }
    }

    /**
     * Marks the given session as the currently-open one so subsequent
     * auto-saves update that file rather than creating a new one.
     *
     * @param sessionId the session to resume
     */
    public void setCurrentSession( String sessionId )
    {
        this.currentSessionId = sessionId;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void saveSession( String sessionId, List<ChatMessage> messages )
    {
        if ( historyDir == null )
        {
            return;
        }
        try
        {
            // Build DTO
            List<PersistedMessage> persisted = new ArrayList<>();
            for ( ChatMessage msg : messages )
            {
                if ( msg.isEmpty() )
                {
                    continue;
                }
                List<PersistedAttachment> attachments = new ArrayList<>();
                for ( Attachment att : msg.getAttachments() )
                {
                    if ( att instanceof Attachment.FileContentAttachment fca )
                    {
                        attachments.add( new PersistedAttachment(
                                "file",
                                fca.getFileName(),
                                fca.getLineNumberStart(),
                                fca.getLineNumberEnd(),
                                fca.getSelectedContent() ) );
                    }
                    // ImageAttachment is not persisted (binary data too large)
                }
                persisted.add( new PersistedMessage(
                        msg.getId(),
                        msg.getRole(),
                        msg.getName(),
                        msg.getContent(),
                        attachments ) );
            }

            String title       = buildTitle( messages );
            String createdAt   = findOrCreateTimestamp( sessionId );
            ChatHistorySession session = new ChatHistorySession( sessionId, createdAt, title, persisted );

            String json   = mapper.writeValueAsString( session );
            Path   target = resolveFilePath( sessionId, createdAt );
            Files.writeString( target, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING );
        }
        catch ( IOException e )
        {
            logger.error( "Failed to save chat history session", e );
        }
    }

    /** Derives the file path from the session id and timestamp. */
    private Path resolveFilePath( String sessionId, String createdAt )
    {
        // File name: <timestamp>_<sessionId>.json
        String filename = createdAt + "_" + sessionId + FILE_SUFFIX;
        return historyDir.resolve( filename );
    }

    /**
     * Returns the timestamp for an existing session file, or creates a fresh
     * one when the session is new.
     */
    private String findOrCreateTimestamp( String sessionId )
    {
        if ( historyDir == null )
        {
            return LocalDateTime.now().format( TIMESTAMP_FMT );
        }
        File dir = historyDir.toFile();
        File[] files = dir.listFiles( f -> f.isFile() && f.getName().contains( sessionId ) );
        if ( files != null )
        {
            for ( File f : files )
            {
                // Extract timestamp prefix from file name
                String name = f.getName();
                int sep = name.indexOf( '_' );
                if ( sep > 0 )
                {
                    return name.substring( 0, sep );
                }
            }
        }
        return LocalDateTime.now().format( TIMESTAMP_FMT );
    }

    /** Builds a human-readable session title from the first user message. */
    private String buildTitle( List<ChatMessage> messages )
    {
        return messages.stream()
                       .filter( m -> "user".equals( m.getRole() ) )
                       .map( ChatMessage::getContent )
                       .filter( c -> c != null && !c.isBlank() )
                       .findFirst()
                       .map( c -> c.lines().findFirst().orElse( c ) )
                       .map( c -> c.length() > TITLE_MAX_LEN ? c.substring( 0, TITLE_MAX_LEN ) + "…" : c )
                       .orElse( "(empty)" );
    }

    // -------------------------------------------------------------------------
    // Data model (DTOs)
    // -------------------------------------------------------------------------

    /**
     * A complete serialised chat session.
     *
     * @param id        stable UUID for this session
     * @param createdAt timestamp string used as file-name prefix
     * @param title     human-readable summary (first user message, truncated)
     * @param messages  ordered list of messages
     */
    @JsonIgnoreProperties( ignoreUnknown = true )
    public record ChatHistorySession(
            String id,
            String createdAt,
            String title,
            List<PersistedMessage> messages )
    {
    }

    /**
     * A single message within a persisted session.
     *
     * @param id      original message UUID
     * @param role    "user" | "assistant" | "system" | "tool"
     * @param name    optional name field
     * @param content plain-text / markdown content
     * @param attachments file attachments (images are not persisted)
     */
    @JsonIgnoreProperties( ignoreUnknown = true )
    public record PersistedMessage(
            String id,
            String role,
            String name,
            String content,
            List<PersistedAttachment> attachments )
    {
    }

    /**
     * A persisted file-content attachment.
     *
     * @param type            always "file"
     * @param filePath        workspace-relative path
     * @param lineNumberStart start line (1-based, 0 if unknown)
     * @param lineNumberEnd   end line (1-based, 0 if unknown)
     * @param selectedContent the captured text
     */
    @JsonIgnoreProperties( ignoreUnknown = true )
    public record PersistedAttachment(
            String type,
            String filePath,
            int lineNumberStart,
            int lineNumberEnd,
            String selectedContent )
    {
    }
}
