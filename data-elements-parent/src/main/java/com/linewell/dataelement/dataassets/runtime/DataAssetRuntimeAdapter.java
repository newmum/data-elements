package com.linewell.dataelement.dataassets.runtime;

import com.fasterxml.jackson.core.type.TypeReference;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.model.dataasset.CatalogItemSaveOrUpdateRequest;
import com.linewell.dataelement.model.dataasset.CatalogItemValueRequest;
import com.linewell.dataelement.model.dataasset.DataAssetPropBatchSaveRequest;
import com.linewell.dataelement.model.dataasset.DataAssetPropSaveRequest;
import com.linewell.dataelement.model.dataasset.DataAssetQueryByIdRequest;
import com.linewell.dataelement.dataassets.base.entity.DaAssetCatalogItemT;
import com.linewell.dataelement.dataassets.base.entity.DaAssetT;
import com.linewell.dataelement.dataassets.base.entity.DataPropT;
import com.linewell.dataelement.dataassets.base.service.IDaAssetCatalogItemTService;
import com.linewell.dataelement.dataassets.base.service.IDaAssetTService;
import com.linewell.dataelement.dataassets.base.service.IDataPropTService;
import com.linewell.dataelement.elasticsearch.EsCommonService;
import com.linewell.dataelement.model.common.BizException;
import com.linewell.dataelement.platform.magic.StructuredTableReadMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.LinkedHashSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.linewell.dataelement.platform.persistence.id.NumericId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence bridge for Java runtime callbacks.
 *
 * <p>Page queries and ordinary asset operations live in Magic API. This adapter is intentionally
 * limited to callbacks that originate inside Warm-Flow or the NiFi runtime and therefore cannot
 * enter through a Magic HTTP route.</p>
 */
@Component
public class DataAssetRuntimeAdapter {

    private static final String INDEX_NAME = "dataassets";

    private final EsCommonService esCommonService;
    private final IDaAssetTService assetService;
    private final IDataPropTService propService;
    private final IDaAssetCatalogItemTService catalogItemService;
    private final ObjectMapper objectMapper;
    private final StructuredTableReadMagicModule tableRead;
    private static final int MAX_CALLBACK_IDS = 100;
    private static final Set<String> COUNTER_NAMES = Set.of("viewCount", "view_count", "marketViewCount", "market_view_count");
    private static final Set<String> IDENTITY_NAMES = Set.of("tid", "id", "tenantId", "tenant_id", "assetType", "asset_type");

    public DataAssetRuntimeAdapter(
            EsCommonService esCommonService,
            IDaAssetTService assetService,
            IDataPropTService propService,
            IDaAssetCatalogItemTService catalogItemService,
            ObjectMapper objectMapper,
            StructuredTableReadMagicModule tableRead) {
        this.esCommonService = esCommonService;
        this.assetService = assetService;
        this.propService = propService;
        this.catalogItemService = catalogItemService;
        this.objectMapper = objectMapper;
        this.tableRead = tableRead;
    }

    public Map<String, Object> queryById(DataAssetQueryByIdRequest request) {
        String tid = validId(request == null ? null : request.getTid());
        String tenant = TenantContext.requireTenantId();
        // 当前目录主表先判定归属和删除状态；旧索引不能把失效目录恢复成有效记录。
        Map<String, Map<String, Object>> catalogs = catalogRows(List.of(tid), true);
        Map<String, Object> primary = catalogs.get(tid);
        if (primary != null) {
            Object deleted = primary.get("isDel");
            if (!(deleted instanceof Number marker) || marker.intValue() != 0) return Map.of();
            return catalogDocument(primary, properties(List.of(tid), null), Map.of());
        }
        Map<String, Object> document = esCommonService.getDocument(INDEX_NAME, tid);
        if (document != null) {
            Object type = document.get("assetType") == null ? document.get("asset_type") : document.get("assetType");
            Object owner = document.get("tenantId") == null ? document.get("tenant_id") : document.get("tenantId");
            if ("catalog".equals(stringValue(type)) || owner != null && !tenant.equals(stringValue(owner))) {
                return Map.of();
            }
            return document;
        }
        // 非目录兼容入口仍供 PipelineController 使用，不改写其数据库视图数量等同名业务字段。
        DaAssetT asset = assetService.getOne(new LambdaQueryWrapper<DaAssetT>()
                .eq(DaAssetT::getTenantId, tenant).eq(DaAssetT::getTid, tid).eq(DaAssetT::getIsDel, 0), false);
        if (asset == null || "catalog".equals(asset.getAssetType())) return Map.of();
        Map<String, Object> result = objectMapper.convertValue(asset, new TypeReference<LinkedHashMap<String, Object>>() {});
        for (DataPropT prop : activeProps(tid)) {
            if (prop.getPropName() != null) result.put(prop.getPropName(), prop.getPropValue());
        }
        result.put("tid", tid);
        result.put("tenantId", tenant);
        result.put("assetType", asset.getAssetType());
        saveDocument(tid, result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Object propSaveOrUpdate(DataAssetPropSaveRequest request) {
        if (request == null) throw new BizException(400, "属性保存参数不能为空");
        saveProperties(List.of(validId(request.getTid())), request.getProps(), false);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public Object propBatchSaveOrUpdate(DataAssetPropBatchSaveRequest request) {
        if (request == null || request.getTids() == null || request.getTids().isEmpty()
                || request.getTids().size() > MAX_CALLBACK_IDS) {
            throw new BizException(400, "目录属性回调单次须包含1至100个目录");
        }
        var ids = new LinkedHashSet<String>();
        for (String id : request.getTids()) ids.add(validId(id));
        saveProperties(new ArrayList<>(ids), request.getProps(), true);
        return true;
    }

    private void saveProperties(List<String> ids, Map<String, Object> supplied, boolean bulk) {
        String tenant = TenantContext.requireTenantId();
        Map<String, Patch> patches = patches(supplied);
        // 全批主表归属预检在任何属性写入前完成，不逐 ID 查询或用 ES 孤儿替代目录。
        Map<String, Map<String, Object>> catalogs = catalogRows(ids, false);
        if (catalogs.size() != ids.size()) throw new BizException(404, "目录不存在、已删除或不属于当前租户");
        List<DataPropT> oldProps = properties(ids, patches.keySet());
        Map<String, Map<String, DataPropT>> byOwner = new LinkedHashMap<>();
        for (DataPropT prop : oldProps) byOwner.computeIfAbsent(prop.getParentId(), ignored -> new LinkedHashMap<>())
                .putIfAbsent(prop.getPropName(), prop);
        LocalDateTime now = LocalDateTime.now();
        List<DataPropT> inserts = new ArrayList<>();
        List<DataPropT> updates = new ArrayList<>();
        for (String id : ids) {
            Map<String, DataPropT> existing = byOwner.getOrDefault(id, Map.of());
            for (Patch patch : patches.values()) {
                DataPropT prop = existing.get(patch.name());
                if (prop == null) {
                    prop = new DataPropT().setTid(simpleId()).setTenantId(tenant).setParentId(id)
                            .setPropName(patch.name()).setPropType(patch.type()).setPropValue(patch.value())
                            .setDataType("catalog").setIsDel(0).setCreatedTime(now).setUpdatedTime(now);
                    inserts.add(prop);
                } else if (!Objects.equals(prop.getPropValue(), patch.value()) || !Objects.equals(prop.getPropType(), patch.type())) {
                    prop.setPropValue(patch.value()).setPropType(patch.type()).setUpdatedTime(now);
                    updates.add(prop);
                }
            }
        }
        if (!inserts.isEmpty() && !propService.saveBatch(inserts)) throw new BizException(500, "目录属性批量新增未确认");
        if (!updates.isEmpty() && !propService.updateBatchById(updates)) throw new BizException(500, "目录属性批量更新未确认");
        List<Map<String, Object>> documents = new ArrayList<>();
        List<Object> counters = new ArrayList<>();
        for (String id : ids) {
            Map<String, Object> document = catalogDocument(catalogs.get(id), List.of(), patches);
            documents.add(document);
            counters.add(document.get("viewCount"));
        }
        // 回调只合并已验证主表的属性，保留既有发布字段；一个批次只发一次有界 bulk。
        if (bulk) esCommonService.bulkMergeCatalogProjections(INDEX_NAME, ids, documents, counters);
        else saveDocument(ids.getFirst(), documents.getFirst());
    }

    private String validId(String value) {
        if (value == null || !value.trim().matches("[A-Za-z0-9_-]{1,32}")) throw new BizException(400, "目录标识无效");
        return value.trim();
    }

    private record Patch(String name, String value, String type) {}

    private Map<String, Patch> patches(Map<String, Object> input) {
        if (input == null) return Map.of();
        if (input.size() > 100) throw new BizException(400, "单次属性最多100项");
        Map<String, Patch> result = new LinkedHashMap<>();
        for (var entry : input.entrySet()) {
            String name = realPropName(entry.getKey());
            if (name == null || name.isBlank() || name.length() > 128) throw new BizException(400, "属性名称无效");
            if (COUNTER_NAMES.contains(name) || IDENTITY_NAMES.contains(name)) continue;
            if (result.putIfAbsent(name, new Patch(name, stringValue(entry.getValue()), propType(entry.getKey()))) != null)
                throw new BizException(400, "属性名称规范化后重复");
        }
        return result;
    }

    private Map<String, Map<String, Object>> catalogRows(List<String> ids, boolean includeDeleted) {
        String tenant = TenantContext.requireTenantId();
        var query = tableRead.from("da_catalog_t", "c", tenant);
        var id = tableRead.column("c", "tid", "TEXT");
        query.where(tableRead.in(id, ids));
        if (!includeDeleted) query.where(tableRead.equal(tableRead.column("c", "is_del", "NUMBER"), 0));
        Map<String, StructuredTableReadMagicModule.Expression> columns = new LinkedHashMap<>();
        for (String column : List.of("tid", "tenant_id", "revision", "created_by", "created_time", "updated_by", "updated_time",
                "is_del", "reg_time", "asset_status", "flow_status", "catalog_name", "catalog_name_en", "asset_desc",
                "app_id", "app_name", "db_id", "db_name", "source_table_id", "source_table_name", "source_db_id", "source_db_name",
                "target_table_id", "target_table_name", "target_db_id", "target_db_type", "org_id", "manage_unit", "business_type",
                "data_source_type", "timestamp_field", "dictionary_table_flag", "resource_revision_no", "market_view_count")) {
            String kind = Set.of("is_del", "asset_status", "flow_status", "resource_revision_no", "market_view_count").contains(column)
                    ? "NUMBER" : column.endsWith("_time") ? "TIME" : column.equals("asset_desc") ? "LOB" : "TEXT";
            columns.put(camel(column), tableRead.column("c", column, kind));
        }
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : query.rows(columns, id, "asc", ids.size())) result.put(stringValue(row.get("tid")), row);
        return result;
    }

    private List<DataPropT> properties(List<String> ids, Set<String> names) {
        if (names != null && names.isEmpty()) return List.of();
        var query = tableRead.from("da_prop_t", "p", TenantContext.requireTenantId());
        var owner = tableRead.column("p", "parent_id", "TEXT");
        var name = tableRead.column("p", "prop_name", "TEXT");
        var id = tableRead.column("p", "tid", "TEXT");
        query.where(tableRead.in(owner, ids));
        query.where(tableRead.equal(tableRead.column("p", "is_del", "NUMBER"), 0));
        query.where(tableRead.equal(tableRead.column("p", "data_type", "TEXT"), "catalog"));
        if (names != null) query.where(tableRead.in(name, new ArrayList<>(names)));
        var latest = query.ranked("latest", Map.of("tid", id, "parent_id", owner, "prop_name", name,
                "prop_type", tableRead.column("p", "prop_type", "TEXT"), "prop_value", tableRead.column("p", "prop_value", "LOB")),
                List.of(owner, name), List.of(tableRead.column("p", "updated_time", "TIME"), id), List.of("desc", "desc"));
        // 每个目录/属性只读最近一条，输入最多100目录和100属性，最多10000个结果，不逐项补查。
        var rows = latest.grouped(Map.of("tid", tableRead.column("latest", "tid", "TEXT"),
                "parentId", tableRead.column("latest", "parent_id", "TEXT"),
                "propName", tableRead.column("latest", "prop_name", "TEXT"),
                "propType", tableRead.column("latest", "prop_type", "TEXT"),
                "propValue", tableRead.column("latest", "prop_value", "LOB")), List.of());
        List<DataPropT> result = new ArrayList<>();
        for (var row : rows) result.add(new DataPropT().setTid(stringValue(row.get("tid")))
                .setTenantId(TenantContext.requireTenantId()).setParentId(stringValue(row.get("parentId")))
                .setPropName(stringValue(row.get("propName"))).setPropType(stringValue(row.get("propType")))
                .setPropValue(stringValue(row.get("propValue"))).setDataType("catalog").setIsDel(0));
        return result;
    }

    private Map<String, Object> catalogDocument(Map<String, Object> primary, List<DataPropT> properties, Map<String, Patch> patches) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (var prop : properties) if (prop.getPropName() != null && !COUNTER_NAMES.contains(prop.getPropName())
                && !IDENTITY_NAMES.contains(prop.getPropName()))
            result.put(prop.getPropName(), prop.getPropValue());
        for (var patch : patches.values()) result.put(patch.name(), patch.value());
        primary.forEach((key, value) -> { if (value != null) result.put(key, value); });
        result.remove("marketViewCount");
        result.put("tid", primary.get("tid"));
        result.put("id", primary.get("tid"));
        result.put("tenantId", TenantContext.requireTenantId());
        result.put("assetType", "catalog");
        result.put("viewCount", primary.get("marketViewCount") == null ? 0L : primary.get("marketViewCount"));
        return result;
    }

    private String camel(String value) {
        StringBuilder result = new StringBuilder();
        boolean upper = false;
        for (char character : value.toCharArray()) {
            if (character == '_') upper = true;
            else { result.append(upper ? Character.toUpperCase(character) : character); upper = false; }
        }
        return result.toString();
    }

    @Transactional(rollbackFor = Exception.class)
    public Object catalogItemSaveOrUpdate(CatalogItemSaveOrUpdateRequest request) {
        String catalogId = request == null ? null : request.getCatalogId();
        if (catalogId == null || catalogId.isBlank()) {
            throw new BizException(500, "catalogId不能为空");
        }
        List<CatalogItemValueRequest> input = request.getCatalogItems();
        if (input == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        List<DaAssetCatalogItemT> oldItems = catalogItemService.list(
                new LambdaQueryWrapper<DaAssetCatalogItemT>()
                        .eq(DaAssetCatalogItemT::getCatalogId, catalogId)
                        .eq(DaAssetCatalogItemT::getIsDel, 0));
        Map<String, DaAssetCatalogItemT> remaining = new HashMap<>();
        oldItems.forEach(item -> remaining.put(item.getTid(), item));
        List<DaAssetCatalogItemT> inserts = new ArrayList<>();
        List<DaAssetCatalogItemT> updates = new ArrayList<>();

        for (int index = 0; index < input.size(); index++) {
            CatalogItemValueRequest value = input.get(index);
            if (value == null) {
                continue;
            }
            String itemId = value.getTid() == null || value.getTid().isBlank() ? value.getId() : value.getTid();
            DaAssetCatalogItemT item = itemId == null || itemId.isBlank() ? null : remaining.remove(itemId);
            if (item == null && itemId != null && !itemId.isBlank()) {
                continue;
            }
            if (item == null) {
                item = new DaAssetCatalogItemT();
                item.setTid(simpleId());
                item.setCatalogId(catalogId);
                item.setIsDel(0);
                item.setCreatedTime(now);
                copyCatalogItem(item, value, index);
                item.setUpdatedTime(now);
                inserts.add(item);
            } else if (catalogItemChanged(item, value, index)) {
                copyCatalogItem(item, value, index);
                item.setUpdatedTime(now);
                updates.add(item);
            }
        }
        if (!inserts.isEmpty()) {
            catalogItemService.saveBatch(inserts);
        }
        if (!updates.isEmpty()) {
            catalogItemService.updateBatchById(updates);
        }
        if (!remaining.isEmpty()) {
            catalogItemService.update(new LambdaUpdateWrapper<DaAssetCatalogItemT>()
                    .eq(DaAssetCatalogItemT::getCatalogId, catalogId)
                    .in(DaAssetCatalogItemT::getTid, remaining.keySet())
                    .set(DaAssetCatalogItemT::getIsDel, 1)
                    .set(DaAssetCatalogItemT::getUpdatedTime, now));
        }
        return null;
    }

    private List<DataPropT> activeProps(String tid) {
        return propService.list(new LambdaQueryWrapper<DataPropT>()
                .eq(DataPropT::getTenantId, TenantContext.requireTenantId())
                .eq(DataPropT::getParentId, tid)
                .eq(DataPropT::getIsDel, 0));
    }

    private void copyCatalogItem(DaAssetCatalogItemT target, CatalogItemValueRequest source, int sortNo) {
        target.setSortNo(sortNo);
        target.setColName(source.getColName());
        target.setColEn(source.getColEn());
        target.setColType(source.getColType());
        target.setColLength(source.getColLength());
        target.setIsPk(source.getIsPk());
        target.setIsNullable(source.getIsNullable());
        target.setDataStandardId(source.getDataStandardId());
        target.setQualityRule(source.getQualityRule());
        target.setEnableCodeTable(source.getEnableCodeTable());
        target.setCodeTableId(source.getCodeTableId());
        target.setSourceTableColumnId(source.getSourceTableColumnId());
        target.setTargetTableColumnId(source.getTargetTableColumnId());
    }

    private boolean catalogItemChanged(DaAssetCatalogItemT old, CatalogItemValueRequest item, int sortNo) {
        return !Objects.equals(old.getSortNo(), sortNo)
                || !Objects.equals(old.getColName(), item.getColName())
                || !Objects.equals(old.getColEn(), item.getColEn())
                || !Objects.equals(old.getColType(), item.getColType())
                || !Objects.equals(old.getColLength(), item.getColLength())
                || !Objects.equals(old.getIsPk(), item.getIsPk())
                || !Objects.equals(old.getIsNullable(), item.getIsNullable())
                || !Objects.equals(old.getDataStandardId(), item.getDataStandardId())
                || !Objects.equals(old.getQualityRule(), item.getQualityRule())
                || !Objects.equals(old.getEnableCodeTable(), item.getEnableCodeTable())
                || !Objects.equals(old.getCodeTableId(), item.getCodeTableId())
                || !Objects.equals(old.getSourceTableColumnId(), item.getSourceTableColumnId())
                || !Objects.equals(old.getTargetTableColumnId(), item.getTargetTableColumnId());
    }

    private void saveDocument(String tid, Map<String, Object> document) {
        if ("catalog".equals(stringValue(document.get("assetType")))) {
            esCommonService.mergeCatalogProjection(INDEX_NAME, tid, document, document.get("viewCount"));
            return;
        }
        try {
            esCommonService.saveOrUpdate(INDEX_NAME, tid, objectMapper.writeValueAsString(document), false);
        } catch (JsonProcessingException e) {
            throw new BizException(500, "资产索引序列化失败");
        }
    }

    private String propType(String key) {
        if (key == null) {
            return "0";
        }
        if (key.endsWith("_date")) {
            return "4";
        }
        if (key.endsWith("_num")) {
            return "1";
        }
        if (key.endsWith("_file")) {
            return "5";
        }
        if (key.endsWith("_dict")) {
            return "3";
        }
        if (key.endsWith("_range")) {
            return "6";
        }
        if (key.endsWith("_time")) {
            return "7";
        }
        return "0";
    }

    private String realPropName(String key) {
        if (key == null) {
            return null;
        }
        for (String suffix : Set.of("_date", "_num", "_file", "_dict", "_range", "_time")) {
            if (key.endsWith(suffix)) {
                return key.substring(0, key.length() - suffix.length());
            }
        }
        return key;
    }

    private String simpleId() {
        return NumericId.nextId();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
