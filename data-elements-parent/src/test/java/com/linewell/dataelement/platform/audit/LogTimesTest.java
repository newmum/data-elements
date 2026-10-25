package com.linewell.dataelement.platform.audit;

import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LogTimesTest {
    @Test void utcWallClockDoesNotDependOnJvmDefaultTimezone() {
        Instant time=Instant.parse("2026-10-25T03:04:05Z");
        assertEquals(LocalDateTime.parse("2026-10-25T03:04:05"),LogTimes.utc(time));
        assertEquals(time,LogTimes.instant(java.sql.Timestamp.valueOf("2026-10-25 03:04:05")));
        assertEquals(time,LogTimes.instant(LocalDateTime.parse("2026-10-25T03:04:05")));
    }
}
