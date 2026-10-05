package com.linewell.dataelement.platform.tenant.api;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.dev33.satoken.session.SaSession;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;

/** Separate session namespace. Account checks and permission policy live in Magic. */
@Component
@MagicModule("platformSession")
public class PlatformSessionMagicModule {
    public static final StpLogic LOGIC = new StpLogic("idaas-platform");
    public void login(String id) {
        LOGIC.login(id, new SaLoginParameter().setDeviceType("idaas").setIsShare(false));
    }
    public void login(String id, long timeoutSeconds) {
        LOGIC.login(id, new SaLoginParameter().setDeviceType("idaas").setIsShare(false).setTimeout(timeoutSeconds));
    }
    public void revoke(String id) { LOGIC.kickout(id); }
    public boolean isLogin() { return LOGIC.isLogin(); }
    public String id() { LOGIC.checkLogin(); return LOGIC.getLoginIdAsString(); }
    public String token() { return LOGIC.getTokenValue(); }
    public SaSession session() { return LOGIC.getTokenSession(); }
    public void logout() { LOGIC.logout(); }
}
