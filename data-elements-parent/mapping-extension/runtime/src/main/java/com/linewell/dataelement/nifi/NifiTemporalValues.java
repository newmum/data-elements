package com.linewell.dataelement.nifi;

import java.sql.Timestamp;
import java.time.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** NiFi/JDBC transport carries absolute instants; unzoned business values use the plan zone. */
final class NifiTemporalValues {
    private NifiTemporalValues() {}
    /** Strip NiFi Record wrappers before the transport-independent engine sees containers. */
    static Object inputValue(Object value) { return inputValue(value,0); }
    private static Object inputValue(Object value,int depth) {
        if(depth>32)throw new com.linewell.dataelement.mapping.core.MappingException("BUDGET_EXCEEDED","输入记录嵌套超过32层");
        if(value instanceof org.apache.nifi.serialization.record.Record record)value=record.toMap();
        if(value instanceof java.util.Map<?,?> map) {
            var result=new java.util.LinkedHashMap<String,Object>();
            for(var item:map.entrySet())result.put(item.getKey().toString(),inputValue(item.getValue(),depth+1));
            return result;
        }
        if(value instanceof Byte[])return value;
        if(value instanceof Object[] items){Object[] result=new Object[items.length];for(int i=0;i<items.length;i++)result[i]=inputValue(items[i],depth+1);return result;}
        if(value instanceof java.util.Collection<?> items){var result=new java.util.ArrayList<Object>();for(Object item:items)result.add(inputValue(item,depth+1));return result;}
        return value;
    }
    static Object writerValue(Object value,ZoneId zone) {
        return writerValue(value, zone, true);
    }

    static Object writerValue(Object value, ZoneId zone, boolean instantText) {
        if(value instanceof LocalDate date)return java.sql.Date.valueOf(date);
        if(value instanceof LocalTime time)return java.sql.Time.valueOf(time);
        // Match LocalDateTime.atZone used by DateFormatUtil: earlier overlap offset,
        // forward adjustment across a gap. Preserve nanoseconds in the JDBC carrier.
        if(value instanceof LocalDateTime date)return timestamp(date.atZone(zone).toInstant(), instantText);
        if(value instanceof Instant instant)return timestamp(instant, instantText);
        if(value instanceof Map<?,?> map){
            var converted=new LinkedHashMap<String,Object>();
            map.forEach((key,item)->converted.put(key.toString(),writerValue(item,zone,instantText)));
            return converted;
        }
        if(value instanceof Collection<?> items)return items.stream().map(item->writerValue(item,zone,instantText)).toArray();
        if(value instanceof Object[] items)return Arrays.stream(items).map(item->writerValue(item,zone,instantText)).toArray();
        return value;
    }

    // Unconfigured JSON returns text. A legacy reader may have already turned
    // wall-clock text into Timestamp.valueOf. Its Timestamp is indistinguishable
    // from a JDBC/Avro instant unless the input codec declares that contract.
    static boolean hasUnclassifiedTimestamp(Object input) {
        if (input instanceof Timestamp) return true;
        if (input instanceof Map<?, ?> fields) return fields.values().stream().anyMatch(NifiTemporalValues::hasUnclassifiedTimestamp);
        if (input instanceof Collection<?> items) return items.stream().anyMatch(NifiTemporalValues::hasUnclassifiedTimestamp);
        return input instanceof Object[] items && !(input instanceof Byte[])
                && Arrays.stream(items).anyMatch(NifiTemporalValues::hasUnclassifiedTimestamp);
    }

    private static Timestamp timestamp(Instant instant, boolean instantText) {
        return instantText ? new InstantTimestamp(instant) : Timestamp.from(instant);
    }

    private static final class InstantTimestamp extends Timestamp {
        private InstantTimestamp(Instant instant) {
            super(instant.toEpochMilli());
            setNanos(instant.getNano());
        }

        @Override
        public LocalDateTime toLocalDateTime() {
            // NiFi 2.9 formats Timestamp through this view. Preserve its JVM
            // output zone, using the same zone rules as its newer instant writer.
            return LocalDateTime.ofInstant(toInstant(), ZoneId.systemDefault());
        }
    }
    /** Preserve a numeric-looking STRING inside a mixed Avro union. NiFi otherwise infers a number. */
    static Object unionSafeValue(Object value){
        if(value instanceof String text)return new StringBuilder(text);
        if(value instanceof java.util.Map<?,?>map){var converted=new java.util.LinkedHashMap<String,Object>();map.forEach((key,item)->converted.put(key.toString(),unionSafeValue(item)));return converted;}
        if(value instanceof Object[]items)return java.util.Arrays.stream(items).map(NifiTemporalValues::unionSafeValue).toArray();
        return value;
    }
}
