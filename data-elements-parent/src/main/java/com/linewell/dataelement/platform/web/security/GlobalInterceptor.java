package com.linewell.dataelement.platform.web.security;

import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.magic.runtime.security.MagicAccessPolicy;
import com.linewell.dataelement.model.common.BizException;
import com.linewell.dataelement.model.common.StatusCodeEnum;
import com.linewell.dataelement.platform.tenant.api.PlatformSessionMagicModule;
import com.linewell.dataelement.platform.web.error.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.core.interceptor.RequestInterceptor;
import org.ssssssss.magicapi.core.model.ApiInfo;
import org.ssssssss.magicapi.core.service.impl.RequestMagicDynamicRegistry;
import org.ssssssss.magicapi.core.web.RequestHandler;
import org.ssssssss.magicapi.servlet.jakarta.MagicJakartaHttpServletRequest;

@Component
public class GlobalInterceptor implements HandlerInterceptor, RequestInterceptor {
    private static final String VERIFIED_ACCESS = GlobalInterceptor.class.getName() + ".verifiedAccess";

    private final WhiteListProp whiteList;
    private final PlatformSessionMagicModule platformSession;
    private final ObjectProvider<RequestMagicDynamicRegistry> registry;
    private final GlobalExceptionHandler errors;

    public GlobalInterceptor(WhiteListProp whiteList, PlatformSessionMagicModule platformSession,
                             ObjectProvider<RequestMagicDynamicRegistry> registry, GlobalExceptionHandler errors) {
        this.whiteList = whiteList;
        this.platformSession = platformSession;
        this.registry = registry;
        this.errors = errors;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        boolean magic = handler instanceof HandlerMethod method
                && RequestHandler.class.isAssignableFrom(method.getBeanType());
        MagicAccessPolicy access = magic ? declared(registry.getObject().getApiInfoFromRequest(
                new MagicJakartaHttpServletRequest(request, null))) : null;
        authorize(request, access);
        if (magic) {
            request.setAttribute(VERIFIED_ACCESS, new VerifiedAccess(access));
        }
        return true;
    }

    @Override
    public Object preHandle(RequestEntity entity) {
        try {
            if (entity.getRequest() != null && entity.getRequest().getRequest() instanceof HttpServletRequest request) {
                // 热刷新可能在 MVC 通过后变更实际执行的资源，切换访问类型时重新校验。
                MagicAccessPolicy access = declared(entity.getApiInfo());
                MagicAccessPolicy verified = request.getAttribute(VERIFIED_ACCESS) instanceof VerifiedAccess prior
                        ? prior.access() : null;
                if (access != verified) {
                    authorize(request, access);
                }
            }
        } catch (BizException error) {
            // 脚本执行前终止并保留 MVC 的业务错误信封。
            return ResponseEntity.ok(errors.handleBizException(error));
        }
        return null;
    }

    private static MagicAccessPolicy declared(ApiInfo api) {
        try {
            return MagicAccessPolicy.declared(api);
        } catch (IllegalArgumentException error) {
            throw new BizException(StatusCodeEnum.FORBIDDEN, "接口访问策略配置无效");
        }
    }

    private void authorize(HttpServletRequest request, MagicAccessPolicy access) {
        String path = request.getRequestURI();
        boolean allowed = access == null
                ? whiteList.isWhiteListed(path) || (path.startsWith("/idaas/") ? platformSession.isLogin() : StpUtil.isLogin())
                : switch (access) {
                    case PUBLIC -> true;
                    case TENANT_SESSION -> StpUtil.isLogin();
                    case IDAAS_PLATFORM_SESSION -> platformSession.isLogin();
                };
        if (!allowed) {
            throw new BizException(StatusCodeEnum.BASELINE_NOLOGIN, "未登录或登录已过期");
        }
    }

    private record VerifiedAccess(MagicAccessPolicy access) { }
}
