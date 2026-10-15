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
