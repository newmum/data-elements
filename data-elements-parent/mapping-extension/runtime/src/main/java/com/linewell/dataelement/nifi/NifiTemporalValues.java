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
        return value;
    }
}
