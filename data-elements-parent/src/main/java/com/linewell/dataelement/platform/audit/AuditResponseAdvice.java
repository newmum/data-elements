package com.linewell.dataelement.platform.audit;

import com.linewell.dataelement.model.common.CommonResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Observes Java response envelopes as well as Magic's explicit result adapter. Never changes the payload. */
@ControllerAdvice
public class AuditResponseAdvice implements ResponseBodyAdvice<Object> {
    @Override public boolean supports(MethodParameter method,Class<? extends HttpMessageConverter<?>> converter){return true;}
    @Override public Object beforeBodyWrite(Object body,MethodParameter method,MediaType type,Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest input,ServerHttpResponse output){
        Object code=null,success=null;
        if(body instanceof CommonResponse response)code=response.getCode();
        else if(body instanceof java.util.Map<?,?> map){code=map.get("code");success=map.get("success");}
        var request=AuditIdentityResolver.request();
        if(request!=null&&code instanceof Number n){
            boolean ok=!Boolean.FALSE.equals(success)&&java.util.Set.of(0,1,200).contains(n.intValue());
            request.setAttribute(AuditWebInterceptor.BUSINESS_CODE,String.valueOf(code));
            request.setAttribute(AuditWebInterceptor.BUSINESS_RESULT,ok?"SUCCESS":n.intValue()==403?"DENIED":"FAILED");
        }
        return body;
    }
}
