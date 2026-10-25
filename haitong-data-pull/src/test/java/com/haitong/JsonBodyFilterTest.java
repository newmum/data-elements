package com.haitong;

import com.haitong.config.JsonBodyFilter;
import com.haitong.config.RelayProperties;
import com.haitong.core.Json;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class JsonBodyFilterTest {
    @Test void aNewDynamicRouteCanReadTheSameExactBoundedJsonTwice() throws Exception {
        byte[] body="{ \"name\" : \"附件验证\", \"records\" : [] }".getBytes(StandardCharsets.UTF_8);
        var request=new MockHttpServletRequest("POST","/validation/attachments/person");
        request.setContentType("application/json"); request.setContent(body);
        new JsonBodyFilter(new RelayProperties(),new Json()).doFilter(request,new MockHttpServletResponse(),(incoming,response)->{
            assertArrayEquals(body,incoming.getInputStream().readAllBytes());
            assertArrayEquals(body,incoming.getInputStream().readAllBytes());
            assertArrayEquals(body,(byte[])incoming.getAttribute(JsonBodyFilter.RAW));
        });
    }

    @Test void editorResourceSavesKeepTheirOwnRequestHandling() throws Exception {
        var request=new MockHttpServletRequest("POST","/api/web/resource/file/api/save");
        request.setContentType("application/json");request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        new JsonBodyFilter(new RelayProperties(),new Json()).doFilter(request,new MockHttpServletResponse(),(incoming,response)->{
            assertSame(request,incoming); assertNull(incoming.getAttribute(JsonBodyFilter.RAW));
        });
    }
}
