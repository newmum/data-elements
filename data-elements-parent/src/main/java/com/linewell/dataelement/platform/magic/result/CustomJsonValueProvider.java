package com.linewell.dataelement.platform.magic.result;

import jakarta.annotation.Resource;
import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.platform.tenant.api.PlatformSessionMagicModule;
import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.core.interceptor.ResultProvider;
import com.linewell.dataelement.model.common.CommonResponse;
import com.linewell.dataelement.platform.web.security.WhiteListProp;

@Component
@Slf4j
public class CustomJsonValueProvider implements ResultProvider {

    @Resource
    private WhiteListProp whiteListProp;

    /**
     * Magic API 的成功/异常响应统一由该委托生成，当前类继续负责登录白名单校验。
     */
    private final MagicApiResultProvider resultDelegate = new MagicApiResultProvider();

    /**
     * 定义返回结果，默认返回JsonBean
     */
    @Override
    public Object buildResult(RequestEntity requestEntity, int code, String message, Object data) {
        Boolean checkLogin = Boolean.valueOf(SpringUtil.getProperty("data-trading-parent.config.checkToken"));
        if (checkLogin) {
            String requestUri = requestEntity.getRequest().getRequestURI();

            boolean isWhiteListed = whiteListProp.isWhiteListed(requestUri);
            boolean loggedIn = requestUri.startsWith("/idaas/")
                    ? PlatformSessionMagicModule.LOGIC.isLogin() : StpUtil.isLogin();
            if (!isWhiteListed && !loggedIn) {
                //前端自动跳登入页
                return CommonResponse.fail(100120, "您访问的内容需要登录账号后才能查看", message);
            }
        }
        return resultDelegate.buildResult(requestEntity, code, message, data);
    }

    @Override
    public Object buildException(RequestEntity requestEntity, Throwable throwable) {
        return resultDelegate.buildException(requestEntity, throwable);
    }
}
