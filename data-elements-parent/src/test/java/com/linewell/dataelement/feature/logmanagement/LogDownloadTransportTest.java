package com.linewell.dataelement.feature.logmanagement;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LogDownloadTransportTest {
    @Test void binaryExportsHaveExplicitNonJsonMediaTypes() {
        var access=mock(LogAccessService.class);var lifecycle=mock(LogLifecycleService.class);
        var scope=new LogScope("validation-tenant","validation-user","validation-app",null,null,"ALL",List.of(),"exports");
        when(access.current("exports")).thenReturn(scope);
        var controller=new LogController(access,null,null,lifecycle,null);
        for(String extension:List.of("csv","jsonl")) {
            when(lifecycle.download(scope,"validation-job")).thenReturn(Path.of("validation."+extension));
            var response=controller.download("validation-job");
            assertEquals(extension.equals("csv")?"text/csv;charset=UTF-8":"application/x-ndjson;charset=UTF-8",response.getHeaders().getContentType().toString());
            assertEquals("no-store",response.getHeaders().getCacheControl());
        }
    }
}
