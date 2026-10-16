package com.linewell.dataelement.mapping.core;

import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TemporalChineseTimeTest {
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    @Test void mixedChineseAndNumericDateTimesAreDetectedPerValueWithNanoseconds() {
        for(String value:new String[]{"2024年5月10日14时27分49.123456789秒","2024年05月10日 14时27分49.123456789秒","2024/05/10 14:27:49.123456789"})
            assertEquals(LocalDateTime.of(2024,5,10,14,27,49,123456789),TemporalParser.auto(value,"DATETIME",ZONE));
        assertEquals(LocalTime.of(14,27,49),TemporalParser.auto("14时27分49秒","TIME",ZONE));
        assertEquals(LocalTime.of(14,27),TemporalParser.auto("14时27分","TIME",ZONE));
    }
    @Test void invalidChineseDatesAndTimesAreNeverRolledForward() {
        for(String value:new String[]{"2024年2月30日14时27分49秒","2024年5月10日25时27分49秒","14时61分49秒"})
            assertEquals("DATE_INVALID",assertThrows(MappingException.class,()->TemporalParser.auto(value,"DATETIME",ZONE)).code());
    }
}
