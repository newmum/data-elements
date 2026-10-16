package com.linewell.dataelement.nifi;

import java.time.*;

/** NiFi/JDBC transport carries absolute instants; unzoned business values use the plan zone. */
final class NifiTemporalValues {
    private NifiTemporalValues() {}
    static Object writerValue(Object value,ZoneId zone) {
        if(value instanceof LocalDate date)return java.sql.Date.valueOf(date);
        if(value instanceof LocalTime time)return java.sql.Time.valueOf(time);
        // Match LocalDateTime.atZone used by DateFormatUtil: earlier overlap offset,
        // forward adjustment across a gap. Preserve nanoseconds in the JDBC carrier.
        if(value instanceof LocalDateTime date)return java.sql.Timestamp.from(date.atZone(zone).toInstant());
        if(value instanceof Instant instant)return java.sql.Timestamp.from(instant);
        if(value instanceof java.util.Map<?,?>map){var converted=new java.util.LinkedHashMap<String,Object>();map.forEach((key,item)->converted.put(key.toString(),writerValue(item,zone)));return converted;}
        if(value instanceof java.util.Collection<?>items)return items.stream().map(item->writerValue(item,zone)).toArray();
        if(value instanceof Object[]items)return java.util.Arrays.stream(items).map(item->writerValue(item,zone)).toArray();
        return value;
    }
    /** Preserve a numeric-looking STRING inside a mixed Avro union. NiFi otherwise infers a number. */
    static Object unionSafeValue(Object value){
        if(value instanceof String text)return new StringBuilder(text);
        if(value instanceof java.util.Map<?,?>map){var converted=new java.util.LinkedHashMap<String,Object>();map.forEach((key,item)->converted.put(key.toString(),unionSafeValue(item)));return converted;}
        if(value instanceof Object[]items)return java.util.Arrays.stream(items).map(NifiTemporalValues::unionSafeValue).toArray();
        return value;
    }
}
