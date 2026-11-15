package com.linewell.dataelement.platform.tenant.application;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;
import cn.dev33.satoken.json.SaJsonTemplateForJackson;
import cn.dev33.satoken.serializer.impl.SaSerializerTemplateForJson;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class TenantSessionsTest {
    @Test void convertsOnlyThisLoginTypesStorageKeysToBareTokenValues() {
        var logic=mock(StpLogic.class);
        when(logic.splicingKeyTokenValue("")).thenReturn("token:login:token:");
        when(logic.searchTokenValue("",0,100000,true)).thenReturn(List.of(
                "token:login:token:local-token","token:login:token:legacy-token","token:idaas-platform:token:unrelated"));
        assertEquals(List.of("local-token","legacy-token"),TenantSessions.tokens(logic));
    }

    @Test
    @SuppressWarnings("unchecked")
    void batchesOnlineReadsAndExcludesForeignIdleAndInvalidSessions() {
        var logic = mock(StpLogic.class);
        var redis = mock(StringRedisTemplate.class);
        var values = (ValueOperations<String, String>) mock(ValueOperations.class);
        var cursor = (Cursor<String>) mock(Cursor.class);
        var dao = new SaTokenDaoForRedisTemplate();
        dao.stringRedisTemplate = redis;
        when(logic.getSaTokenDao()).thenReturn(dao);
        when(logic.getConfigOrGlobal()).thenReturn(new SaTokenConfig().setActiveTimeout(60));
        when(logic.isOpenCheckActiveTimeout()).thenReturn(true);
        when(logic.isValidLoginId(nullable(String.class))).thenCallRealMethod();
        when(logic.splicingKeyTokenValue(anyString())).thenAnswer(invocation -> "token:" + invocation.getArgument(0));
        when(logic.splicingKeyTokenSession(anyString())).thenAnswer(invocation -> "session:" + invocation.getArgument(0));
        when(logic.splicingKeyLastActiveTime(anyString())).thenAnswer(invocation -> "active:" + invocation.getArgument(0));
        when(redis.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(redis.opsForValue()).thenReturn(values);

        String tenant = "validation-tenant";
        var keys = new LinkedHashMap<String, String>();
        var json = new SaJsonTemplateForJackson();
        long now = System.currentTimeMillis();
        IntStream.range(0, 504).forEach(i -> {
            String id = String.valueOf(i);
            keys.put("token:" + id, i == 503 ? null : "user-" + id);
            var session = new SaSession("session:" + id).set("tenantId", i == 500 ? "foreign" : tenant)
                    .set("authRealm", i == 501 ? "other" : "tenant-local-v1");
            keys.put("session:" + id, json.objectToJson(session));
            keys.put("active:" + id, String.valueOf(i == 502 ? now - 300_000 : now));
        });
        var tokens = keys.keySet().stream().filter(key -> key.startsWith("token:")).iterator();
        when(cursor.hasNext()).thenAnswer(ignored -> tokens.hasNext());
        when(cursor.next()).thenAnswer(ignored -> tokens.next());
        when(values.multiGet(anyCollection())).thenAnswer(invocation ->
                invocation.<List<String>>getArgument(0).stream().map(keys::get).toList());

        Set<String> online;
        try (var manager = mockStatic(SaManager.class)) {
            manager.when(SaManager::getSaSerializerTemplate).thenReturn(new SaSerializerTemplateForJson());
            manager.when(SaManager::getSaJsonTemplate).thenReturn(json);
            online = TenantSessions.onlineLocalUserIds(logic, tenant);
        }
        assertEquals(500, online.size());
        assertTrue(online.contains("user-0"));
        for (int index = 500; index < 504; index++) assertFalse(online.contains("user-" + index));
        verify(values, times(2)).multiGet(anyCollection());
        verify(values, never()).get(anyString());
        verify(cursor).close();
    }
}
