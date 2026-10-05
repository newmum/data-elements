package com.linewell.dataelement.feature.metadatagovernance.api;

import com.linewell.dataelement.feature.metadatagovernance.application.LineageAnalysisService;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/** Read-only lineage analysis entry points; tenant context comes from the authenticated session. */
@Component
@MagicModule("lineageAnalysis")
public class LineageAnalysisMagicModule {
    private final LineageAnalysisService service;

    public LineageAnalysisMagicModule(LineageAnalysisService service) { this.service = service; }

    @Comment("搜索当前租户可分析的数据表、字段和模型")
    public Map<String, Object> objects(Map<String, Object> body) { return service.objects(safe(body)); }

    @Comment("按全链、归因或影响模式分析设计或部署依赖")
    public Map<String, Object> analyze(Map<String, Object> body) {
        if ("DEPLOYED".equalsIgnoreCase(String.valueOf(safe(body).get("scope")))) service.reconcileCurrentDeployments();
        return service.analyze(safe(body));
    }

    @Comment("查看一条依赖的对象、规则和依据")
    public Map<String, Object> evidence(Map<String, Object> body) {
        if ("DEPLOYED".equalsIgnoreCase(String.valueOf(safe(body).get("scope")))) service.reconcileCurrentDeployments();
        return service.evidence(safe(body));
    }

    @Comment("读取当前租户的血缘覆盖情况")
    public Map<String, Object> coverage() { service.reconcileCurrentDeployments(); return service.coverage(); }

    private static Map<String, Object> safe(Map<String, Object> body) { return body == null ? Map.of() : body; }
}
