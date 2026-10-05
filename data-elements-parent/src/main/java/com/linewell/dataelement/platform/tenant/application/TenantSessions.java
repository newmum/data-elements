package com.linewell.dataelement.platform.tenant.application;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.StpLogic;
import java.util.List;

/** Local account session attributes belong to one token, never a shared control-account session. */
public final class TenantSessions {
    private TenantSessions() { }
    public static boolean isLocal() {
        return StpUtil.isLogin() && "tenant-local-v1".equals(StpUtil.getTokenSession().get("authRealm"));
    }
    public static SaSession session() {
        return isLocal() ? StpUtil.getTokenSession() : StpUtil.getSession();
    }
    /** Sa-Token searchTokenValue returns storage keys, not bare credentials. */
    public static List<String> tokens(StpLogic logic) {
        String prefix=logic.splicingKeyTokenValue("");
        return logic.searchTokenValue("",0,100000,true).stream()
                .filter(key->key.startsWith(prefix))
                .map(key->key.substring(prefix.length())).toList();
    }
}
