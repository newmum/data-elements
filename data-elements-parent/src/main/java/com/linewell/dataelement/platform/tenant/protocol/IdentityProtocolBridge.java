package com.linewell.dataelement.platform.tenant.protocol;

import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.magicapi.core.service.MagicAPIService;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;

/** Framework-to-Magic trust boundary. No HTTP parameter can set the trusted marker. */
@Component
@MagicModule("identityProtocol")
public class IdentityProtocolBridge {
    private final ObjectProvider<MagicAPIService> services;
    private static final ThreadLocal<Boolean> TRUSTED = ThreadLocal.withInitial(() -> false);
    public IdentityProtocolBridge(ObjectProvider<MagicAPIService> services) { this.services=services; }
    public boolean isTrusted() { return TRUSTED.get(); }
    Map<String,Object> call(String function,Map<String,Object> input) {
        if (!java.util.Set.of("protocol-client","protocol-store","protocol-access").contains(function)) throw new IllegalArgumentException("未知协议适配器");
        boolean previous=TRUSTED.get();
        var priorBox=cn.dev33.satoken.context.SaTokenContextForThreadLocalStaff.getModelBoxOrNull();
        var attributes=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if(priorBox==null && attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet && servlet.getResponse()!=null)cn.dev33.satoken.servlet.util.SaTokenContextJakartaServletUtil.setContext(servlet.getRequest(),servlet.getResponse());
        try(var scope=TenantContext.control()) {
            TRUSTED.set(true);
            return services.getObject().invoke("/idaas/"+function,Map.of("payload",input));
        } finally {
            if(previous) TRUSTED.set(true);else TRUSTED.remove();
            // Magic's programmatic invocation cleans its own servlet wrapper.
            // Restore the enclosing HTTP context, including nested invocations.
            if(priorBox==null)cn.dev33.satoken.context.SaTokenContextForThreadLocalStaff.clearModelBox();
            else cn.dev33.satoken.context.SaTokenContextForThreadLocalStaff.setModelBox(priorBox.request,priorBox.response,priorBox.storage);
        }
    }
    public String hashClientSecret(String value) {
        if(value==null || !value.matches("[A-Za-z0-9_~.-]{32,72}")) throw new IllegalArgumentException("客户端密钥须为32至72个独立随机ASCII字符");
        return "{bcrypt}"+new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode(value);
    }
}
