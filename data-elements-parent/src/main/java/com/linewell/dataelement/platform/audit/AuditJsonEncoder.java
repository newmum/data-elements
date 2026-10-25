package com.linewell.dataelement.platform.audit;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/** Structured runtime output without SQL bind values or credential key/value pairs. */
public class AuditJsonEncoder extends EncoderBase<ILoggingEvent> {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Pattern SECRET=Pattern.compile("(?i)(password|passwd|secret|token|authorization|cookie|privateKey|密码|口令)\\s*[:=]\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s,;}]+)");
    @Override public byte[] headerBytes(){return new byte[0];}
    @Override public byte[] footerBytes(){return new byte[0];}
    @Override public byte[] encode(ILoggingEvent event) {
        try {
            String message=event.getFormattedMessage();
            if(message!=null && (message.contains("SQL参数")||message.contains("Parameters:")||message.contains("Parameters :"))) message="SQL_BIND_VALUES_REDACTED";
            message=SECRET.matcher(Objects.toString(message,"")).replaceAll("$1=[REDACTED]");
            var row=new LinkedHashMap<String,Object>();row.put("time",Instant.ofEpochMilli(event.getTimeStamp()).toString());row.put("level",event.getLevel().toString());row.put("logger",event.getLoggerName());row.put("thread",event.getThreadName());row.put("message",AuditSanitizer.text(message,8192));
            var mdc=event.getMDCPropertyMap();for(String key:List.of("tenant_id","actor_id","request_id","trace_id","span_id"))if(mdc.containsKey(key))row.put(key,mdc.get(key));
            if(event.getThrowableProxy()!=null) {
                row.put("exception_type",event.getThrowableProxy().getClassName());var frames=event.getThrowableProxy().getStackTraceElementProxyArray();
                if(frames!=null) row.put("frames",Arrays.stream(frames).limit(40).map(frame->frame.getStackTraceElement().toString()).toList());
            }
            return (JSON.writeValueAsString(row)+"\n").getBytes(StandardCharsets.UTF_8);
        }catch(Exception error){return "{\"level\":\"ERROR\",\"message\":\"RUNTIME_LOG_ENCODING_FAILED\"}\n".getBytes(StandardCharsets.UTF_8);}
    }
}
