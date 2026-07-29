package com.github.gradusnikov.eclipse.assistai.mcp.servers;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.eclipse.e4.core.di.annotations.Creatable;

import com.github.gradusnikov.eclipse.assistai.mcp.annotations.McpServer;
import com.github.gradusnikov.eclipse.assistai.mcp.annotations.Tool;
import com.github.gradusnikov.eclipse.assistai.mcp.annotations.ToolParam;

@Creatable
@McpServer(name = "time")
public class TimeMcpServer
{
    @Tool(name = "currentTime", description = "Returns the current date and time in the following format: yyyy-MM-dd HH:mm:ss", type = "object")
    public String getCurrentTime()
    {
        ZonedDateTime now = ZonedDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
        return now.format(formatter);
    }
    
    @Tool(name = "convertTimeZone", 
          description = "Converts time from one time zone to another. Returns a converted time in the yyyy-MM-dd HH:mm:ss z format.", 
          type = "object")
    public String convertTimeZone(@ToolParam(name="time", description = "Date/time in the format yyyy-MM-dd HH:mm:ss", required = true) String timeString, 
            @ToolParam(name="sourceZone", description = "Source time zone id such as, such as Europe/Paris or CST. Default: system time zone", required = false) String sourceZone, 
            @ToolParam(name="targetZone", description = "Target time zone id, such as Europer/Paris or CST. Default: UTC", required = false) String targetZone)
    {
        try
        {
            // Resolve nulls to defaults. These two are declared required = false so the
            // defaults the descriptions above promise can actually be taken: the tool
            // layer rejects a call missing a required parameter before this body runs, so
            // omitting them used to be an error rather than a default.
            String resolvedSource = Optional.ofNullable( sourceZone ).orElse( ZoneId.systemDefault().getId() );
            String resolvedTarget = Optional.ofNullable( targetZone ).orElse( "UTC" );

            // Get source and target time zones from parameters
            var sourceZoneId = ZoneId.of( resolvedSource );
            var targetZoneId = ZoneId.of( resolvedTarget );

            // Parse first, always. Returning the input unparsed when the two zones match
            // meant a malformed time came back looking converted - the one case where the
            // caller most needs to be told it is malformed, since nothing downstream will
            // notice either.
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
            ZonedDateTime zoneTime = ZonedDateTime.from( formatter.parse( timeString + " " + sourceZoneId.getId() ) );

            if ( sourceZoneId.equals( targetZoneId ) )
            {
                return timeString;
            }

            // Convert to the target time zone
            ZonedDateTime convertedTime = zoneTime.withZoneSameInstant( targetZoneId );

            // Format the result
            return convertedTime.format( formatter );
        }
        catch (Exception e)
        {
            return "Error converting time zone: " + e.getMessage();
        }
    }
}
