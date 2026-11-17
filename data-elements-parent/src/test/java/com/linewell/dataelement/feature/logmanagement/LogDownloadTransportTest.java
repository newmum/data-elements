package com.linewell.dataelement.feature.logmanagement;

import com.linewell.dataelement.platform.web.download.LogDownloadController;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.ssssssss.magicapi.core.service.MagicAPIService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LogDownloadTransportTest {
    @Test void binaryExportsHaveExplicitNonJsonMediaTypes() {
        var access=mock(LogAccessService.class);var lifecycle=mock(LogLifecycleService.class);
        var scope=new LogScope("validation-tenant","validation-user","validation-app",null,null,"ALL",List.of(),"exports");
        when(access.current("exports")).thenReturn(scope);
        var magic = mock(MagicAPIService.class);
        var context = Map.<String, Object>of("requiredPermission", "audit:export", "requiredDomain", "public");
        when(magic.invoke("/idaas/log-guard", Map.of("capability", "exports", "domain", "public"))).thenReturn(context);
        var platformScope = new LogScope(null, "validation-operator", "idaas", "public", null, "ALL", List.of(), "exports");
        when(access.platform(context, "exports")).thenReturn(platformScope);
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("magicAPIService", magic);
        var controller = new LogDownloadController(beans.getBeanProvider(MagicAPIService.class), access, lifecycle);
        for(String extension:List.of("csv","jsonl")) {
            String name = "validation." + extension;
            when(lifecycle.download(scope,"validation-job")).thenReturn(Path.of(name));
            when(lifecycle.download(platformScope,"validation-job")).thenReturn(Path.of(name));
            for (var response : List.of(controller.industry("validation-job"), controller.platform("validation-job", "public"))) {
                assertEquals(extension.equals("csv")?"text/csv;charset=UTF-8":"application/x-ndjson;charset=UTF-8",response.getHeaders().getContentType().toString());
                assertEquals("no-store",response.getHeaders().getCacheControl());
                assertEquals("attachment; filename=\"" + name + "\"", response.getHeaders().getFirst("Content-Disposition"));
                assertEquals(name, response.getBody().getFilename());
            }
        }
    }
}
