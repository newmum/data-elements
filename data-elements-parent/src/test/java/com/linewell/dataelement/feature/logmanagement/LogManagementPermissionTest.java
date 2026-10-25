package com.linewell.dataelement.feature.logmanagement;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LogManagementPermissionTest {
    @Test void lifecyclePermissionCannotBeReusedToReadEventDetailsOrStatistics(){
        var access=mock(LogAccessService.class);var query=mock(LogQueryService.class);
        var module=new LogManagementMagicModule(access,query,mock(LogComponentService.class),mock(LogLifecycleService.class),mock(LogArchiveService.class));
        for(String kind:new String[]{"exports","archives","policies"})for(String operation:new String[]{"page","detail","statistics","summary","items"})assertThrows(SecurityException.class,()->module.query(Map.of("requiredPermission","audit:export"),kind,operation,Map.of()));
        verifyNoInteractions(access,query);
    }
}
