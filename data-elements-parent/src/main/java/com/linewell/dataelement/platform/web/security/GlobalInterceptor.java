package com.linewell.dataelement.platform.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.platform.tenant.api.PlatformSessionMagicModule;
import com.linewell.dataelement.model.common.BizException;
import com.linewell.dataelement.model.common.StatusCodeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class GlobalInterceptor implements  HandlerInterceptor {


    @Autowired
    private WhiteListProp whiteListProp;

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //magic-api 由magic-api响应拦截处理
        // 检查是否在白名单中
        String requestUri = request.getRequestURI();
        boolean isWhiteListed = whiteListProp.isWhiteListed(requestUri);
        boolean loggedIn = requestUri.startsWith("/idaas/")
                ? PlatformSessionMagicModule.LOGIC.isLogin() : StpUtil.isLogin();
        if(!isWhiteListed && !loggedIn){
            throw new BizException(StatusCodeEnum.BASELINE_NOLOGIN,"未登录或登录已过期");
           // return false;
        }
        return true;
    }

}
