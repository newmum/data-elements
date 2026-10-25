package com.linewell.dataelement.platform.audit;

import java.time.*;

/** DATETIME/TIMESTAMP columns hold a UTC wall clock, independent of JVM/JDBC default timezone. */
public final class LogTimes {
    private LogTimes() { }
    public static LocalDateTime utc(Instant time) {return LocalDateTime.ofInstant(time,ZoneOffset.UTC);}
    public static Instant instant(Object value) {
        if(value instanceof LocalDateTime date)return date.toInstant(ZoneOffset.UTC);
        if(value instanceof java.sql.Timestamp date)return date.toLocalDateTime().toInstant(ZoneOffset.UTC);
        if(value instanceof Instant date)return date;
        if(value instanceof java.util.Date date)return date.toInstant();
        try {return Instant.parse(String.valueOf(value));}catch(Exception error){return LocalDateTime.parse(String.valueOf(value).replace(' ','T')).toInstant(ZoneOffset.UTC);}
    }
}
