package com.linewell.dataelement.platform.tenant.protocol;

import cn.dev33.satoken.context.SaTokenContextForThreadLocalStaff;
import cn.dev33.satoken.servlet.util.SaTokenContextJakartaServletUtil;
import com.linewell.dataelement.magic.runtime.route.RegisteredMagicFunctionInvoker;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.Map;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.ssssssss.magicapi.core.annotation.MagicModule;

/** Framework-to-Magic trust boundary. No HTTP parameter can set the trusted marker. */
@Component
@MagicModule("identityProtocol")
public class IdentityProtocolBridge {
    private static final String CLIENT = "f919bae96dc45a398e97ad8bfbdf2dfc";
    private static final String ACCESS = "b8e9f119f73a5c5d80fef296a2d55079";
    private static final String STORE = "3abcf6298afb5031ab99f4c9897f4809";
    private static final ThreadLocal<Boolean> TRUSTED = ThreadLocal.withInitial(() -> false);
    private final RegisteredMagicFunctionInvoker functions;

    public IdentityProtocolBridge(@Lazy RegisteredMagicFunctionInvoker functions) {
        this.functions = functions;
    }

    public boolean isTrusted() {
        return TRUSTED.get();
    }

    Map<String, Object> client(Map<String, Object> input) {
        return invoke(CLIENT, input);
    }

    Map<String, Object> access(Map<String, Object> input) {
        return invoke(ACCESS, input);
    }

    Map<String, Object> store(Map<String, Object> input) {
        return invoke(STORE, input);
    }

    private Map<String, Object> invoke(String resourceId, Map<String, Object> input) {
        boolean previous = TRUSTED.get();
        var priorBox = SaTokenContextForThreadLocalStaff.getModelBoxOrNull();
        var attributes = RequestContextHolder.getRequestAttributes();
        try (var scope = TenantContext.control()) {
            if (priorBox == null && attributes instanceof ServletRequestAttributes servlet && servlet.getResponse() != null) {
                SaTokenContextJakartaServletUtil.setContext(servlet.getRequest(), servlet.getResponse());
            }
            TRUSTED.set(true);
            return functions.invoke(resourceId, Map.of("payload", input));
        } finally {
            if (previous) {
                TRUSTED.set(true);
            } else {
                TRUSTED.remove();
            }
            if (priorBox == null) {
                SaTokenContextForThreadLocalStaff.clearModelBox();
            } else {
                SaTokenContextForThreadLocalStaff.setModelBox(priorBox.request, priorBox.response, priorBox.storage);
            }
        }
    }
    public String hashClientSecret(String value) {
        if(value==null || !value.matches("[A-Za-z0-9_~.-]{32,72}")) throw new IllegalArgumentException("客户端密钥须为32至72个独立随机ASCII字符");
        return "{bcrypt}"+new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode(value);
    }
}
