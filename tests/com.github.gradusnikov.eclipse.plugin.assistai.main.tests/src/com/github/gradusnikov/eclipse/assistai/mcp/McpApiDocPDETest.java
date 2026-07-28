package com.github.gradusnikov.eclipse.assistai.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * Keeps `docs/mcp-api.md` honest.
 * <p>
 * A generated document that nothing checks is a hand-written document with extra steps:
 * it is correct on the day it is produced and wrong the first time someone changes a
 * tool without re-running the generator. This regenerates and compares, so the drift
 * fails the build instead of being discovered by a reader.
 * <p>
 * Run with {@code -DupdateMcpApiDoc=true} - which is what {@code tools/generate-mcp-api.sh}
 * does - to write the file instead of checking it.
 */
public class McpApiDocPDETest
{
    private static final String UPDATE_PROPERTY = "updateMcpApiDoc";

    /**
     * Locates the document from the test bundle's working directory.
     * <p>
     * Walks up looking for the repository layout rather than assuming a fixed depth,
     * because the working directory differs between the PDE harness and Tycho.
     */
    private static Path apiDoc()
    {
        Path candidate = Paths.get( "" ).toAbsolutePath();
        for ( int up = 0; up < 8 && candidate != null; up++, candidate = candidate.getParent() )
        {
            Path doc = candidate.resolve(
                    "plugins/com.github.gradusnikov.eclipse.plugin.assistai.main/docs/mcp-api.md" );
            if ( Files.exists( doc.getParent() ) )
            {
                return doc;
            }
        }
        throw new IllegalStateException( "Could not locate the docs directory from "
                + Paths.get( "" ).toAbsolutePath() );
    }

    @Test
    public void theCommittedReferenceMatchesTheAnnotations() throws IOException
    {
        String generated = McpApiDoc.generate();
        Path doc = apiDoc();

        if ( Boolean.getBoolean( UPDATE_PROPERTY ) )
        {
            Files.writeString( doc, generated, StandardCharsets.UTF_8 );
            return;
        }

        assertTrue( Files.exists( doc ), doc + " is missing; run tools/generate-mcp-api.sh" );
        assertEquals( Files.readString( doc, StandardCharsets.UTF_8 ), generated,
                "docs/mcp-api.md no longer matches the annotations. "
                        + "Run tools/generate-mcp-api.sh and commit the result." );
    }

    @Test
    public void everyToolIsDocumented()
    {
        String generated = McpApiDoc.generate();

        // A tool that reaches a client but not the reference is the failure this whole
        // arrangement exists to prevent, so it is asserted directly rather than left
        // implicit in the comparison above.
        for ( Class<?> server : McpServerBuiltins.BUILT_IN_MCP_SERVERS )
        {
            for ( var method : server.getDeclaredMethods() )
            {
                var tool = method.getAnnotation(
                        com.github.gradusnikov.eclipse.assistai.mcp.annotations.Tool.class );
                if ( tool != null )
                {
                    assertTrue( generated.contains( "### `" + tool.name() + "`" ),
                            tool.name() + " is exposed but does not appear in the reference" );
                }
            }
        }
    }
}
