package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

public record MethodSearchResponse(
    String pattern,
    int totalMatches,
    List<MethodMatch> methods,
    boolean truncated,
    String summaryText
)
{
    public record MethodMatch(
        String methodName,
        String declaringType,
        String packageName,
        String projectName,
        String returnType,
        List<String> parameterTypes
    )
    {
    }

    public static MethodSearchResponse of( String pattern, List<MethodMatch> methods, int limit )
    {
        boolean truncated = limit > 0 && methods.size() > limit;
        List<MethodMatch> shown = truncated ? methods.subList( 0, limit ) : methods;
        return new MethodSearchResponse( pattern, methods.size(), shown, truncated, summarize( methods.size(), truncated, shown.size() ) );
    }

    private static String summarize( int total, boolean truncated, int shown )
    {
        if ( total == 0 )
        {
            return "No methods found.";
        }
        String summary = total + ( total == 1 ? " method found." : " methods found." );
        return truncated ? summary + " Showing the first " + shown + "." : summary;
    }
}
