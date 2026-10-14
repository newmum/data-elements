package com.linewell.dataelement.integration.nifi.canvas.controller;

import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService;
import com.linewell.dataelement.integration.nifi.canvas.mapping.UnifiedMappingService;
import com.linewell.dataelement.mapping.core.MappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.linewell.dataelement.feature.dataaccess.infrastructure.jdbc.RegisteredJdbcDataSourceResolver;
import com.linewell.dataelement.feature.dataquality.infrastructure.persistence.mapper.DataQualityMapper;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/nifi/api/field-mapping")
@Api(tags = "FieldMappingController NiFi 字段映射")
public class FieldMappingController {
    private final FieldMappingService service;
    private final RegisteredJdbcDataSourceResolver sources;
    private final DataQualityMapper metadata;
    private final UnifiedMappingService unified;

    public FieldMappingController(FieldMappingService service) {
        this(service,null,null);
    }
    public FieldMappingController(FieldMappingService service,RegisteredJdbcDataSourceResolver sources,DataQualityMapper metadata){this(service,sources,metadata,null);}
    @Autowired public FieldMappingController(FieldMappingService service,RegisteredJdbcDataSourceResolver sources,DataQualityMapper metadata,UnifiedMappingService unified){this.service=service;this.sources=sources;this.metadata=metadata;this.unified=unified;}

    @GetMapping("/capabilities")
    public Map<String,Object> capabilities(){return Map.of("engineVersion",com.linewell.dataelement.mapping.core.MappingEngine.engineVersion(),"planVersion","1.0","functions",unified.capabilities(),"maxPreviewRows",100,"runtimeType","com.linewell.dataelement.nifi.HaitongTransformRecord");}

    @ExceptionHandler(MappingException.class)
    public ResponseEntity<Map<String,Object>> mappingError(MappingException exception){return ResponseEntity.unprocessableEntity().body(Map.of("error",exception.getMessage(),"code",exception.code()));}

    @PostMapping("/lookup-template")
    public Map<String,Object> lookupTemplate(@RequestBody Map<String,Object> request) {
        String tenant=TenantContext.requireTenantId(),table=String.valueOf(request.getOrDefault("tableId","")),source=String.valueOf(request.getOrDefault("datasourceId",""));
        var endpoint=sources.resolve(tenant,source,table);
        String key=String.valueOf(request.getOrDefault("keyColumn","")),value=String.valueOf(request.getOrDefault("valueColumn",""));
        var columns=metadata.selectColumnOptions(tenant,table).stream().map(r->String.valueOf(r.get("column_name"))).toList();
        if(!columns.contains(key)||!columns.contains(value))throw new IllegalArgumentException("字典查询字段不属于选定的已登记表");
        String quote=endpoint.jdbcUrl().startsWith("jdbc:mysql:")||endpoint.jdbcUrl().startsWith("jdbc:mariadb:")?"`":"\"";
        java.util.function.Function<String,String> identifier=name->{if(!name.matches("[\\p{L}_][\\p{L}\\p{N}_$#]{0,127}"))throw new IllegalArgumentException("数据库字段标识无效");return quote+name+quote;};
        String qualified=java.util.Arrays.stream(endpoint.tableName().split("\\.")).map(identifier).collect(java.util.stream.Collectors.joining("."));
        // Only registered IDs and generated parameterized SQL leave this endpoint, never credentials.
        return Map.of("sql","SELECT "+identifier.apply(value)+" FROM "+qualified+" WHERE "+identifier.apply(key)+" = ?","resultColumn",value,"onMissing","NULL","dataSource",Map.of("datasourceId",source));
    }

    @PostMapping("/migrate-legacy")
    @Operation(summary = "迁移旧映射(migrate)")
    public JsonNode migrate(@RequestBody JsonNode body) {
        JsonNode spec = body.has("spec") ? body.get("spec") : body;
        return service.normalizeSpec(spec);
    }

    @PostMapping("/validate")
    @Operation(summary = "校验映射(validate)")
    public FieldMappingService.ValidationResponse validate(@RequestBody ValidateRequest request) {
        if(unified!=null){
            com.linewell.dataelement.mapping.core.MappingEngine.Plan plan;
            try{plan=unified.compile(request.spec()).plan();}
            catch(MappingException exception){return new FieldMappingService.ValidationResponse(false,List.of(new FieldMappingService.MappingIssue("spec",null,exception.getMessage(),"ERROR",exception.code(),List.of())),List.of(),new FieldMappingService.SchemaCompare(request.sourceSchema()==null?List.of():request.sourceSchema(),request.targetSchema()==null?List.of():request.targetSchema()));}
            // The engine owns conditional/target-reference validation. Keep schema diagnostics only.
            return unified.validateSchemas(plan,request.sourceSchema(),request.targetSchema());
        }
        return service.validate(request.spec(), request.sourceSchema(), request.targetSchema());
    }

    @PostMapping("/preview")
    @Operation(summary = "预览映射(preview)")
    public FieldMappingService.PreviewResponse preview(@RequestBody PreviewRequest request) {
        if(unified!=null)return unified.preview(request.spec(),request.sampleRows());
        return service.preview(request.spec(), request.sourceConfig(), request.sampleRows());
    }

    @PostMapping("/recommend")
    @Operation(summary = "推荐映射(recommend)")
    public FieldMappingService.RecommendResponse recommend(@RequestBody RecommendRequest request) {
        return service.recommend(request.sourceSchema(), request.targetSchema());
    }

    @PostMapping("/compile")
    @Operation(summary = "编译映射(compile)")
    public Map<String, Object> compile(@RequestBody CompileRequest request) {
        if(unified!=null)return unified.compileResponse(request.spec());
        FieldMappingService.CompiledMapping plan = service.compilePlan(request.spec());
        boolean hasLookups = !plan.lookups().isEmpty();
        List<Map<String, Object>> lookups = publicLookups(plan.lookups());
        return Map.of(
                "mode", hasLookups ? "QUERY_RECORD_WITH_LOOKUPS" : "QUERY_RECORD",
                "query", plan.baseQuery(),
                "finalQuery", plan.finalQuery(),
                "lookups", lookups,
                "nifiComponentConfig", hasLookups
                        ? Map.of(
                        "baseQuery", plan.baseQuery(),
                        "finalQuery", plan.finalQuery(),
                        "lookups", lookups)
                        : Map.of("success", plan.baseQuery()));
    }

    /**
     * 编译结果仅用于画布调试，不能向浏览器回传字典连接密码或 JDBC 地址。
     * NiFi 部署仍使用原始编译计划中的 dataSource。
     */
    private List<Map<String, Object>> publicLookups(List<FieldMappingService.LookupPlan> plans) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (FieldMappingService.LookupPlan plan : plans) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("targetField", plan.targetField());
            item.put("resultColumn", plan.resultColumn());
            item.put("parameterizedSql", plan.parameterizedSql());
            item.put("parameterFields", plan.parameterFields());
            item.put("parameterRecordFields", plan.parameterRecordFields());
            item.put("onMissing", plan.onMissing());
            if (plan.recordLookup() != null) {
                item.put("mode", "RECORD_TRANSLATION");
                item.put("multiValue", true);
                item.put("multiValueSeparator", plan.recordLookup().separator());
                item.put("values", plan.recordLookup().values());
                item.put("query", plan.recordLookup().query());
            }
            Map<String, Object> source = plan.dataSource();
            if (source != null && !source.isEmpty()) {
                Map<String, Object> summary = new LinkedHashMap<>();
                copyIfPresent(source, summary, "datasourceId");
                copyIfPresent(source, summary, "dbType");
                copyIfPresent(source, summary, "database");
                copyIfPresent(source, summary, "defaultSchema");
                item.put("dataSource", summary);
            }
            result.add(item);
        }
        return result;
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value != null) target.put(key, value);
    }

    public record ValidateRequest(JsonNode spec, List<FieldMappingService.FieldMeta> sourceSchema, List<FieldMappingService.FieldMeta> targetSchema) {}
    public record PreviewRequest(JsonNode spec, List<FieldMappingService.FieldMeta> sourceSchema, List<FieldMappingService.FieldMeta> targetSchema, Map<String, Object> sourceConfig, List<Map<String, Object>> sampleRows) {}
    public record RecommendRequest(List<FieldMappingService.FieldMeta> sourceSchema, List<FieldMappingService.FieldMeta> targetSchema, Map<String, Object> options) {}
    public record CompileRequest(JsonNode spec, List<FieldMappingService.FieldMeta> sourceSchema, List<FieldMappingService.FieldMeta> targetSchema, Map<String, Object> sourceConfig) {}
}
