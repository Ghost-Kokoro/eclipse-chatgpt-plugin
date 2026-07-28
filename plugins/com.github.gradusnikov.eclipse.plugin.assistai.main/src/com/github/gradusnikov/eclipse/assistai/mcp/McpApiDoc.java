package com.github.gradusnikov.eclipse.assistai.mcp;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.gradusnikov.eclipse.assistai.mcp.annotations.McpServer;
import com.github.gradusnikov.eclipse.assistai.mcp.annotations.Tool;
import com.github.gradusnikov.eclipse.assistai.mcp.annotations.ToolParam;

/**
 * Generates the MCP API reference from the annotations themselves.
 * <p>
 * The document exists because the alternative - a hand-written one - was wrong within
 * a week of every change to a tool. This reads the same {@link McpServer},
 * {@link Tool} and {@link ToolParam} declarations the server builds its schema from,
 * over the same server list the runtime registers, so a tool cannot be added, renamed
 * or reshaped without the document following. A test regenerates it and fails when the
 * committed file differs, which is what makes that guarantee real rather than
 * aspirational.
 * <p>
 * Result shapes are rendered from the record components rather than from the generated
 * JSON Schema: the schema is what a machine consumes and is far more verbose than a
 * person reading a reference needs.
 */
public final class McpApiDoc
{
    /** Regenerate with the script in {@code tools/}, not by editing the output. */
    private static final String HEADER = """
            # MCP API reference

            The tools this plugin exposes over MCP, and the shape of what each returns.

            **This file is generated.** It is produced from the `@McpServer`, `@Tool` and
            `@ToolParam` annotations by `McpApiDoc`, over the server list in
            `McpServerBuiltins`, and `McpApiDocPDETest` fails when it is out of date. Do not
            edit it by hand - change the annotations and regenerate:

            ```
            tools/generate-mcp-api.sh
            ```

            Every tool argument is passed as a string, whatever the parameter means; a
            required parameter is marked `*`. Tools marked *long* run asynchronously and
            return an operation id to poll with `getOperationStatus`.
            """;

    private McpApiDoc()
    {
    }

    public static String generate()
    {
        StringBuilder out = new StringBuilder( HEADER );
        Set<Class<?>> shapes = new LinkedHashSet<>();

        List<Class<?>> servers = Arrays.stream( McpServerBuiltins.BUILT_IN_MCP_SERVERS )
                .sorted( Comparator.comparing( McpApiDoc::serverName ) )
                .toList();

        out.append( "\n## Servers\n\n| Server | Tools |\n|---|---|\n" );
        for ( Class<?> server : servers )
        {
            out.append( "| [" ).append( serverName( server ) ).append( "](#" )
               .append( anchor( serverName( server ) ) ).append( ") | " )
               .append( tools( server ).size() ).append( " |\n" );
        }

        for ( Class<?> server : servers )
        {
            out.append( "\n## " ).append( serverName( server ) ).append( "\n" );
            for ( Method tool : tools( server ) )
            {
                appendTool( out, tool, shapes );
            }
        }

        appendShapes( out, shapes );
        return out.toString();
    }

    private static void appendTool( StringBuilder out, Method method, Set<Class<?>> shapes )
    {
        Tool tool = method.getAnnotation( Tool.class );

        out.append( "\n### `" ).append( tool.name() ).append( "`" );
        if ( tool.longExecution() )
        {
            out.append( " *(long)*" );
        }
        out.append( "\n\n" ).append( tool.description().trim() ).append( "\n" );

        List<Parameter> parameters = Arrays.stream( method.getParameters() )
                .filter( p -> p.isAnnotationPresent( ToolParam.class ) )
                .toList();
        if ( !parameters.isEmpty() )
        {
            out.append( "\n| Parameter | | Description |\n|---|---|---|\n" );
            for ( Parameter parameter : parameters )
            {
                ToolParam param = parameter.getAnnotation( ToolParam.class );
                out.append( "| `" ).append( param.name() ).append( "` | " )
                   .append( param.required() ? "\\*" : "" ).append( " | " )
                   .append( param.description().trim().replace( "|", "\\|" ) ).append( " |\n" );
            }
        }

        out.append( "\n**Returns** " ).append( typeLink( method.getGenericReturnType(), shapes ) ).append( "\n" );
    }

    private static void appendShapes( StringBuilder out, Set<Class<?>> shapes )
    {
        if ( shapes.isEmpty() )
        {
            return;
        }
        out.append( "\n## Result shapes\n" );

        // The set grows while it is walked - a record's components pull in their own
        // record and enum types - so it is drained rather than iterated.
        List<Class<?>> written = new ArrayList<>();
        while ( written.size() < shapes.size() )
        {
            List<Class<?>> pending = shapes.stream().filter( c -> !written.contains( c ) ).toList();
            for ( Class<?> shape : pending )
            {
                written.add( shape );
                appendShape( out, shape, shapes );
            }
        }
    }

    private static void appendShape( StringBuilder out, Class<?> shape, Set<Class<?>> shapes )
    {
        // An explicit anchor rather than one derived from the heading: a nested type
        // renders as Outer.Inner, and every Markdown dialect slugs that punctuation
        // differently, so deriving it produced links that resolved nowhere.
        out.append( "\n<a id=\"" ).append( shapeAnchor( shape ) ).append( "\"></a>\n" );
        out.append( "### `" ).append( simpleName( shape ) ).append( "`\n\n" );

        if ( shape.isEnum() )
        {
            out.append( Arrays.stream( shape.getEnumConstants() )
                    .map( c -> "`" + c + "`" ).collect( Collectors.joining( " \\| " ) ) ).append( "\n" );
            return;
        }

        out.append( "| Field | Type |\n|---|---|\n" );
        for ( RecordComponent component : shape.getRecordComponents() )
        {
            out.append( "| `" ).append( component.getName() ).append( "` | " )
               .append( typeLink( component.getGenericType(), shapes ) ).append( " |\n" );
        }
    }

    /**
     * Renders a type, registering any record or enum it mentions so the appendix
     * describes it exactly once.
     */
    private static String typeLink( Type type, Set<Class<?>> shapes )
    {
        if ( type instanceof ParameterizedType parameterized )
        {
            Type[] arguments = parameterized.getActualTypeArguments();
            String raw = simpleName( (Class<?>) parameterized.getRawType() );
            if ( arguments.length == 1 && List.class.equals( parameterized.getRawType() ) )
            {
                return typeLink( arguments[0], shapes ) + "[]";
            }
            return raw + Arrays.stream( arguments ).map( a -> typeLink( a, shapes ) )
                    .collect( Collectors.joining( ", ", "&lt;", "&gt;" ) );
        }

        Class<?> clazz = (Class<?>) type;
        if ( clazz.isRecord() || clazz.isEnum() )
        {
            shapes.add( clazz );
            return "[`" + simpleName( clazz ) + "`](#" + shapeAnchor( clazz ) + ")";
        }
        return "`" + simpleName( clazz ) + "`";
    }

    /** Nested types read better as {@code Outer.Inner} than as {@code Inner} alone. */
    private static String simpleName( Class<?> clazz )
    {
        return clazz.getEnclosingClass() == null
                ? clazz.getSimpleName()
                : clazz.getEnclosingClass().getSimpleName() + "." + clazz.getSimpleName();
    }

    private static String anchor( String heading )
    {
        return heading.toLowerCase().replace( ".", "" ).replace( " ", "-" );
    }

    /** Matches the explicit anchor emitted before each shape heading. */
    private static String shapeAnchor( Class<?> shape )
    {
        return "shape-" + simpleName( shape ).replace( ".", "-" );
    }

    private static String serverName( Class<?> server )
    {
        return server.getAnnotation( McpServer.class ).name();
    }

    private static List<Method> tools( Class<?> server )
    {
        return Arrays.stream( server.getDeclaredMethods() )
                .filter( m -> m.isAnnotationPresent( Tool.class ) )
                .sorted( Comparator.comparing( m -> m.getAnnotation( Tool.class ).name() ) )
                .toList();
    }
}
