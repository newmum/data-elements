package com.linewell.dataelement.nifi;

import java.time.*;
import com.linewell.dataelement.mapping.core.ValueTypes;

/** NiFi/JDBC transport carries absolute instants; unzoned business values use the plan zone. */
final class NifiTemporalValues {
    private NifiTemporalValues() {}
    static Object writerValue(Object value,ZoneId zone) {
        if(value instanceof LocalDate date)return java.sql.Date.valueOf(date);
        if(value instanceof LocalTime time)return java.sql.Time.valueOf(time);
        if(value instanceof LocalDateTime date)return java.sql.Timestamp.from(ValueTypes.instant(date,zone));
        if(value instanceof Instant instant)return java.sql.Timestamp.from(instant);
        return value;
    }
}
