package com.haitong.api;

import com.haitong.config.*;
import com.haitong.core.Json;
import com.haitong.security.PushCredentialStore;
import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PushAccessPathTest {
    @Test void contextPrefixAloneIsValidButUnsafeRawPathsRemainRejected()throws Exception{
        var settings=new RelayProperties();settings.getSecurity().setPushValidationEnabled(false);
        var filter=new AuthenticationFilter(mock(Secrets.class),settings,new Json(),mock(PushCredentialStore.class));
        var valid=new MockHttpServletRequest("POST","/data/api/v1/data-push");valid.setContextPath("/data");
        var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);filter.doFilter(valid,response,chain);
        verify(chain).doFilter(valid,response);assertEquals(PushAccess.OWNER,AuthenticationFilter.identity(valid).id());
        for(String suffix:List.of("%20","%0A","%2F",";a=1","//x","/../x","/./x","\\x")){
            var bad=new MockHttpServletRequest("POST","/data/api/v1/data-push"+suffix);bad.setContextPath("/data");
            var rejected=new MockHttpServletResponse();var denied=mock(FilterChain.class);filter.doFilter(bad,rejected,denied);
            assertEquals(400,rejected.getStatus(),suffix);assertTrue(rejected.getContentAsString().contains("INVALID_PATH"));verifyNoInteractions(denied);
        }
    }
    @Test void contextIsNeverInferredFromClientHeadersOrPartialNames(){
        var request=new MockHttpServletRequest("POST","/data/api/v1/data-push");
        request.addHeader("X-Forwarded-Prefix","/data");assertEquals("/data/api/v1/data-push",PushAccess.path(request));
        request.setContextPath("/dat");assertEquals("/data/api/v1/data-push",PushAccess.path(request));
        request.setContextPath("/data");assertEquals("/api/v1/data-push",PushAccess.path(request));
    }
}
