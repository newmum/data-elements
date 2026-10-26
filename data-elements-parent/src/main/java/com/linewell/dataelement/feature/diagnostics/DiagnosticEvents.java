package com.linewell.dataelement.feature.diagnostics;

import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.feature.diagnostics.DiagnosticModels.Actor;
import com.linewell.dataelement.feature.diagnostics.DiagnosticModels.Evidence;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Best-effort capture must never replace or delay an application's original failure with an AI error. */
@Component
public class DiagnosticEvents {
    private final ExecutorService captures;
    private final DiagnosticStore store;
    private final DiagnosticProperties properties;
    @Autowired
    public DiagnosticEvents(DiagnosticStore store, DiagnosticProperties properties) {
        this(store, properties, new java.util.concurrent.ThreadPoolExecutor(1, 2, 30, java.util.concurrent.TimeUnit.SECONDS,
                new java.util.concurrent.ArrayBlockingQueue<>(16), task -> { Thread t = new Thread(task, "diagnostic-event-capture"); t.setDaemon(true); return t; },
                new java.util.concurrent.ThreadPoolExecutor.AbortPolicy()));
    }
    DiagnosticEvents(DiagnosticStore store, DiagnosticProperties properties, ExecutorService captures) {
        this.store = store; this.properties = properties; this.captures = captures;
    }
    public static Actor currentActor() {
        StpUtil.checkLogin();
        if (TenantContext.usesControlDatabase()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "诊断需要行业应用会话");
        return new Actor(TenantContext.requireTenantId(), StpUtil.getLoginIdAsString());
    }
    public void attach(Map<String, Object> error, String method, String requestPath) {
        if (!properties.available() || requestPath == null || requestPath.startsWith("/sym/ai/diagnostics")
                || requestPath.startsWith("/idaas/") || error.containsKey("diagnosticRef")) return;
        String code = String.valueOf(error.getOrDefault("code", ""));
        if (code.contains("TENANT") || code.contains("AUTH") && !code.startsWith("DB-") || code.endsWith("403") || code.endsWith("401") || code.endsWith("100120")) return;
        try {
            Actor actor = currentActor();
            String id = store.id();
            String content = "错误码: " + code + "\n摘要: " + error.getOrDefault("message", "")
                    + "\n异常类型: " + error.getOrDefault("type", "") + "\n跟踪号: " + error.getOrDefault("traceId", "")
                    + "\n请求: " + method + " " + requestPath.split("\\?", 2)[0]
                    + "\n异常链: " + error.getOrDefault("detail", "");
            Evidence snapshot = new Evidence("error", "平台服务端错误（发生时）", Instant.now(), DiagnosticSanitizer.errorText(content));
            java.util.concurrent.Future<?> saved = captures.submit(() -> store.save(actor, "event", id, snapshot));
            try {
                saved.get(150, java.util.concurrent.TimeUnit.MILLISECONDS);
                error.put("diagnosticRef", id);
            } catch (Exception unavailable) { saved.cancel(true); if (unavailable instanceof InterruptedException) Thread.currentThread().interrupt(); }
        } catch (RuntimeException unavailable) { /* login/context/Redis failures leave the original response intact */ }
    }
    @jakarta.annotation.PreDestroy public void close() { captures.shutdownNow(); }
}
