package com.linewell.dataelement.feature.metadatagovernance.api;

import com.linewell.dataelement.feature.metadatagovernance.application.MetadataGovernanceService;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/** Magic facade. Tenant identity is derived only from the authenticated server session. */
@Component
@MagicModule("metadataGovernance")
public class MetadataGovernanceMagicModule {
    private final MetadataGovernanceService service;
    public MetadataGovernanceMagicModule(MetadataGovernanceService service) { this.service = service; }
    @Comment("读取当前租户已采集元数据目录") public Map<String,Object> catalog() { return service.catalog(); }
    @Comment("基于已采集结构发现表关系") public Map<String,Object> discover(Map<String,Object> body) { return service.discover(safe(body)); }
    @Comment("验证逻辑关系字段映射、条件和租户归属，返回可持久化的结构定义") public Map<String,Object> prepareDefinition(Map<String,Object> body) { return service.prepareDefinition(safe(body)); }
    @Comment("读取业务键并验证逻辑关系，返回有范围的汇总") public Map<String,Object> validateRelation(Map<String,Object> body) { return service.validateRelationData(safe(body)); }
    @Comment("只读预检查并生成物理外键 SQL") public Map<String,Object> previewForeignKey(Map<String,Object> body) { return service.previewPhysicalForeignKey(safe(body)); }
    @Comment("明确确认后执行已预览的物理外键") public Map<String,Object> createForeignKey(Map<String,Object> body) { return service.createPhysicalForeignKey(safe(body)); }
    private Map<String,Object> safe(Map<String,Object> body) { return body == null ? Map.of() : body; }
}
