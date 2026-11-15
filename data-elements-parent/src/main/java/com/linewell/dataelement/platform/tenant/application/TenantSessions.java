package com.linewell.dataelement.platform.tenant.application;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaValue2Box;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Local account session attributes belong to one token, never a shared control-account session. */
public final class TenantSessions {
    private static final int TOKEN_LIMIT = 100_000;
    private static final int REDIS_BATCH_SIZE = 500;

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
        return logic.searchTokenValue("", 0, TOKEN_LIMIT, true).stream()
                .filter(key->key.startsWith(prefix))
                .map(key->key.substring(prefix.length())).toList();
    }

    /** Resolve online local users from the same Sa-Token store as interactive sign-in. */
    public static Set<String> onlineLocalUserIds(StpLogic logic, String tenantId) {
        var result = new LinkedHashSet<String>();
        var store = logic.getSaTokenDao();
        if (store instanceof SaTokenDaoForRedisTemplate redis) {
            String prefix = logic.splicingKeyTokenValue("");
            var batch = new ArrayList<String>(REDIS_BATCH_SIZE);
            int seen = 0;
            try (var keys = redis.stringRedisTemplate.scan(ScanOptions.scanOptions()
                    .match(prefix + "*").count(REDIS_BATCH_SIZE).build())) {
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (++seen > TOKEN_LIMIT) {
                        throw new IllegalStateException("在线会话超过当前租户会话读取的容量");
                    }
                    batch.add(key.substring(prefix.length()));
                    if (batch.size() == REDIS_BATCH_SIZE) {
                        addRedisUsers(logic, redis.stringRedisTemplate, tenantId, batch, result);
                        batch.clear();
                    }
                }
            }
            if (!batch.isEmpty()) addRedisUsers(logic, redis.stringRedisTemplate, tenantId, batch, result);
        } else if (store instanceof SaTokenDaoDefaultImpl) {
            // The embedded store resolves accesses in process through Sa-Token.
            String prefix = logic.splicingKeyTokenValue("");
            var keys = logic.searchTokenValue("", 0, TOKEN_LIMIT + 1, true);
            if (keys.size() > TOKEN_LIMIT) {
                throw new IllegalStateException("在线会话超过当前租户会话读取的容量");
            }
            for (String key : keys) {
                if (!key.startsWith(prefix)) continue;
                String token = key.substring(prefix.length());
                Object loginId = logic.getLoginIdByToken(token);
                if (loginId != null && belongsToTenant(logic.getTokenSessionByToken(token, false), tenantId)) {
                    result.add(loginId.toString());
                }
            }
        } else {
            throw new IllegalStateException("当前会话存储不支持批量读取");
        }
        return result;
    }

    private static void addRedisUsers(StpLogic logic, StringRedisTemplate redis, String tenantId,
            List<String> tokens, Set<String> result) {
        boolean checkIdle = logic.isOpenCheckActiveTimeout();
        List<String> keys = new ArrayList<>(tokens.size() * (checkIdle ? 3 : 2));
        tokens.forEach(token -> keys.add(logic.splicingKeyTokenValue(token)));
        tokens.forEach(token -> keys.add(logic.splicingKeyTokenSession(token)));
        if (checkIdle) tokens.forEach(token -> keys.add(logic.splicingKeyLastActiveTime(token)));
        List<String> values = redis.opsForValue().multiGet(keys);
        if (values == null || values.size() != keys.size()) {
            throw new IllegalStateException("无法读取在线会话存储");
        }
        var serializer = SaManager.getSaSerializerTemplate();
        long now = System.currentTimeMillis();
        for (int index = 0; index < tokens.size(); index++) {
            String loginId = values.get(index);
            String session = values.get(tokens.size() + index);
            if (!logic.isValidLoginId(loginId) || session == null
                    || (checkIdle && !isActive(logic, values.get(2 * tokens.size() + index), now))) {
                continue;
            }
            if (belongsToTenant(serializer.stringToObject(session, SaSession.class), tenantId)) result.add(loginId);
        }
    }

    private static boolean isActive(StpLogic logic, String activeValue, long now) {
        if (activeValue == null) return false;
        var activity = new SaValue2Box(activeValue);
        var config = logic.getConfigOrGlobal();
        long limit = config.getDynamicActiveTimeout()
                ? activity.getValue2AsLong(config.getActiveTimeout()) : config.getActiveTimeout();
        return limit == -1 || (now - activity.getValue1AsLong()) / 1000 < limit;
    }

    private static boolean belongsToTenant(SaSession session, String tenantId) {
        return session != null && tenantId.equals(session.get("tenantId"))
                && "tenant-local-v1".equals(session.get("authRealm"));
    }
}
