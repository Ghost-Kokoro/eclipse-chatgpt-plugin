package com.github.gradusnikov.eclipse.assistai.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.gradusnikov.eclipse.assistai.resources.EditResult;

/**
 * Plain JUnit tests for output-schema generation.
 * <p>
 * These guard the promise a tool makes to its clients: if the schema stops matching
 * the record it was generated from, a client that trusted it starts reading fields
 * that are not there. Pure reflection, so no Eclipse runtime is needed.
 */
public class McpOutputSchemasTest
{
    enum Colour
    {
        RED, GREEN
    }

    record Leaf( String name, int count )
    {
    }

    record Branch( Leaf leaf, List<Leaf> leaves, Colour colour, boolean flag, double ratio, long stamp )
    {
    }

    record SelfReferencing( String name, SelfReferencing child )
    {
    }

    @SuppressWarnings( "unchecked" )
    private static Map<String, Object> properties( Map<String, Object> schema )
    {
        return (Map<String, Object>) schema.get( "properties" );
    }

    @SuppressWarnings( "unchecked" )
    private static Map<String, Object> property( Map<String, Object> schema, String name )
    {
        return (Map<String, Object>) properties( schema ).get( name );
    }

    // ---- types that carry no structure -----------------------------------

    @Test
    public void describesNothingForVoid()
    {
        assertNull( McpOutputSchemas.forType( Void.class ) );
        assertNull( McpOutputSchemas.forType( void.class ) );
    }

    @Test
    public void describesNothingForPlainText()
    {
        // A tool returning prose must not advertise a schema promising structure.
        assertNull( McpOutputSchemas.forType( String.class ) );
        assertNull( McpOutputSchemas.forType( Object.class ) );
        assertNull( McpOutputSchemas.forType( null ) );
    }

    // ---- records ---------------------------------------------------------

    @Test
    public void describesARecordAsAnObject()
    {
        Map<String, Object> schema = McpOutputSchemas.forType( Leaf.class );

        assertEquals( "object", schema.get( "type" ) );
        assertEquals( "string", property( schema, "name" ).get( "type" ) );
        assertEquals( "integer", property( schema, "count" ).get( "type" ) );
    }

    @Test
    public void keepsRecordComponentOrder()
    {
        Map<String, Object> schema = McpOutputSchemas.forType( Leaf.class );

        assertEquals( List.of( "name", "count" ), List.copyOf( properties( schema ).keySet() ) );
    }

    @Test
    public void describesNestedRecords()
    {
        Map<String, Object> schema = McpOutputSchemas.forType( Branch.class );
        Map<String, Object> leaf = property( schema, "leaf" );

        assertEquals( "object", leaf.get( "type" ) );
        assertEquals( "string", ( (Map<String, Object>) properties( leaf ).get( "name" ) ).get( "type" ) );
    }

    @Test
    public void describesGenericListsWithItemTypes()
    {
        Map<String, Object> leaves = property( McpOutputSchemas.forType( Branch.class ), "leaves" );

        assertEquals( "array", leaves.get( "type" ) );
        Map<String, Object> items = (Map<String, Object>) leaves.get( "items" );
        assertEquals( "object", items.get( "type" ) );
        assertTrue( ( (Map<String, Object>) items.get( "properties" ) ).containsKey( "count" ) );
    }

    @Test
    public void describesEnumsWithTheirConstants()
    {
        Map<String, Object> colour = property( McpOutputSchemas.forType( Branch.class ), "colour" );

        assertEquals( "string", colour.get( "type" ) );
        assertEquals( List.of( "RED", "GREEN" ), colour.get( "enum" ) );
    }

    @Test
    public void mapsJavaPrimitivesToJsonTypes()
    {
        Map<String, Object> schema = McpOutputSchemas.forType( Branch.class );

        assertEquals( "boolean", property( schema, "flag" ).get( "type" ) );
        assertEquals( "number", property( schema, "ratio" ).get( "type" ) );
        assertEquals( "integer", property( schema, "stamp" ).get( "type" ) );
    }

    @Test
    public void terminatesOnASelfReferencingRecord()
    {
        Map<String, Object> schema = McpOutputSchemas.forType( SelfReferencing.class );

        assertEquals( "object", schema.get( "type" ) );
        assertEquals( "object", property( schema, "child" ).get( "type" ) );
    }

    // ---- the shape actually shipped --------------------------------------

    @Test
    public void describesTheEditResultToolsAdvertise()
    {
        Map<String, Object> schema = McpOutputSchemas.forType( EditResult.class );

        assertNotNull( schema );
        assertEquals( "object", schema.get( "type" ) );

        Map<String, Object> fields = properties( schema );
        assertTrue( fields.containsKey( "status" ), fields.keySet().toString() );
        assertTrue( fields.containsKey( "versionBefore" ) );
        assertTrue( fields.containsKey( "versionAfter" ) );
        assertTrue( fields.containsKey( "unifiedDiff" ) );
        assertTrue( fields.containsKey( "undoHistoryTimestamp" ) );
        assertTrue( fields.containsKey( "diagnostics" ) );
    }

    @Test
    public void advertisesTheEditStatusValuesAClientBranchesOn()
    {
        Map<String, Object> status = property( McpOutputSchemas.forType( EditResult.class ), "status" );

        List<String> values = (List<String>) status.get( "enum" );
        assertTrue( values.contains( "APPLIED" ), values.toString() );
        assertTrue( values.contains( "REJECTED" ), values.toString() );
        assertTrue( values.contains( "PREVIEW" ), values.toString() );
    }

    @Test
    public void advertisesTheModificationStampUsedForConcurrency()
    {
        Map<String, Object> versionAfter =
                property( McpOutputSchemas.forType( EditResult.class ), "versionAfter" );

        assertEquals( "integer",
                ( (Map<String, Object>) properties( versionAfter ).get( "modificationStamp" ) ).get( "type" ) );
    }

    @Test
    public void advertisesDiagnosticsAsAnArrayOfCodedObjects()
    {
        Map<String, Object> diagnostics =
                property( McpOutputSchemas.forType( EditResult.class ), "diagnostics" );

        assertEquals( "array", diagnostics.get( "type" ) );
        Map<String, Object> item = (Map<String, Object>) diagnostics.get( "items" );
        Map<String, Object> code = (Map<String, Object>) properties( item ).get( "code" );
        assertTrue( ( (List<String>) code.get( "enum" ) ).contains( "VERSION_CONFLICT" ) );
    }

    @Test
    public void doesNotClaimFieldsAreRequired()
    {
        // Optional sections are routinely null; declaring them required would make a
        // validating client reject responses that are perfectly correct.
        assertFalse( McpOutputSchemas.forType( EditResult.class ).containsKey( "required" ) );
    }
}
