package com.linewell.dataelement.nifi;

import com.linewell.dataelement.mapping.core.ValueTypes;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.apache.nifi.serialization.record.RecordFieldType;
import org.apache.nifi.serialization.record.util.DataTypeUtils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NifiTemporalValuesTest {
    @Test void nestedJsonArraysUseTheNativeRecordArrayCarrierWithoutLosingScalarTypes() {
        var converted=(Map<?,?>)NifiTemporalValues.writerValue(Map.of("values",Arrays.asList("12",12,true,null)),ZoneId.of("Asia/Shanghai"));
        assertArrayEquals(new Object[]{"12",12,true,null},(Object[])converted.get("values"));
    }
    @Test void numericLookingTextIsNotEligibleForNiFisNumericUnionCoercion() {
        Object carrier=NifiTemporalValues.unionSafeValue("12");assertInstanceOf(CharSequence.class,carrier);assertFalse(carrier instanceof String);assertEquals("12",carrier.toString());
        assertThrows(RuntimeException.class,()->DataTypeUtils.toInteger(carrier,"fixture"));
    }
    @Test void historicalDatetimeUsesTheSameAtZoneResolutionAsDateFormatUtil() {
        ZoneId zone=ZoneId.of("Asia/Shanghai");
        for(LocalDateTime value:List.of(LocalDateTime.of(1947,4,15,0,0),LocalDateTime.of(1989,4,16,2,30),LocalDateTime.of(1991,9,15,1,30))) {
            assertEquals(value.atZone(zone).toInstant(),((Timestamp)NifiTemporalValues.writerValue(value,zone)).toInstant());
            assertEquals(value.toLocalDate(),((java.sql.Date)NifiTemporalValues.writerValue(value.toLocalDate(),zone)).toLocalDate());
        }
    }
    @Test void actualNiFiCodecPreservesTransportInstantAndChainedBusinessTimeAcrossJvmZones() {
        TimeZone previous=TimeZone.getDefault();var zone=ZoneId.of("Asia/Shanghai");
        try {
            for(String id:List.of("UTC","Asia/Shanghai","America/New_York")) {
                TimeZone.setDefault(TimeZone.getTimeZone(id));
                var local=LocalDateTime.of(2024,5,10,14,27,49,123456789);
                var expected=local.atZone(zone).toInstant();
                var carrier=(Timestamp)NifiTemporalValues.writerValue(local,zone);assertEquals(expected,carrier.toInstant());
                String format="yyyy-MM-dd HH:mm:ss.SSSSSSSSS";
                String wire=DataTypeUtils.toString(carrier,format);
                var restored=(Timestamp)DataTypeUtils.convertType(wire,RecordFieldType.TIMESTAMP.getDataType(),Optional.empty(),Optional.empty(),Optional.of(format),"d");
                assertEquals(expected,restored.toInstant(),id);assertEquals(local,ValueTypes.dateTime(restored,zone),id);
                assertEquals(expected,((Timestamp)NifiTemporalValues.writerValue(ValueTypes.dateTime(restored,zone),zone)).toInstant(),id);
                assertEquals("2024-05-10",DataTypeUtils.toString(NifiTemporalValues.writerValue(local.toLocalDate(),zone),"yyyy-MM-dd"),id);
                assertEquals("14:27:49",DataTypeUtils.toString(NifiTemporalValues.writerValue(local.toLocalTime(),zone),"HH:mm:ss"),id);
                assertEquals(expected,((Timestamp)NifiTemporalValues.writerValue(expected,zone)).toInstant(),id);
            }
        }finally{TimeZone.setDefault(previous);}
    }
}
