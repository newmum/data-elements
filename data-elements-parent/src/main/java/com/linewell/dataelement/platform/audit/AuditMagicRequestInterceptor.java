package com.linewell.dataelement.platform.audit;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.core.interceptor.RequestInterceptor;
/** Capture API metadata before business transactions prepare audit rows. */
@Component
public class AuditMagicRequestInterceptor implements RequestInterceptor {
    @Override public Object preHandle(RequestEntity entity){
        var request=AuditIdentityResolver.request();if(request!=null&&entity!=null&&entity.getApiInfo()!=null){request.setAttribute("audit.functionName",entity.getApiInfo().getName());request.setAttribute("audit.functionId",entity.getApiInfo().getId());}return null;
    }
}
