package com.linewell.dataelement.dataassets.runtime;

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

    public DataAssetRuntimeAdapter(
            EsCommonService esCommonService,
            IDaAssetTService assetService,
            IDataPropTService propService,
            IDaAssetCatalogItemTService catalogItemService,
            ObjectMapper objectMapper) {
        this.esCommonService = esCommonService;
        this.assetService = assetService;
        this.propService = propService;
        this.catalogItemService = catalogItemService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> queryById(DataAssetQueryByIdRequest request) {
        String tid = request == null ? null : request.getTid();
        if (tid == null || tid.isBlank()) {
            throw new BizException(-1, "tid不能为空");
        }
        Map<String, Object> document = esCommonService.getDocument(INDEX_NAME, tid);
        if (document != null) {
            return document;
        }

        DaAssetT asset = assetService.getOne(new LambdaQueryWrapper<DaAssetT>()
                .eq(DaAssetT::getTid, tid)
                .eq(DaAssetT::getIsDel, 0), false);
        Map<String, Object> result = asset == null
                ? new LinkedHashMap<>()
                : objectMapper.convertValue(asset, LinkedHashMap.class);
        for (DataPropT prop : activeProps(tid)) {
            if (prop.getPropName() != null) {
                result.put(prop.getPropName(), prop.getPropValue());
            }
        }
        if (result.isEmpty()) {
            return result;
        }
        result.put("tid", tid);
        saveDocument(tid, result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Object propSaveOrUpdate(DataAssetPropSaveRequest request) {
        String tid = request == null ? null : request.getTid();
        if (tid == null || tid.isBlank()) {
            throw new BizException(400, "tid不能为空");
        }
        Map<String, Object> props = request.getProps() == null ? Map.of() : request.getProps();
        upsertProps(tid, props);
        Map<String, Object> document = queryById(queryRequest(tid));
        document.putAll(props);
        saveDocument(tid, document);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public Object propBatchSaveOrUpdate(DataAssetPropBatchSaveRequest request) {
        if (request == null || request.getTids() == null || request.getTids().isEmpty()) {
            throw new BizException(400, "tids不能为空");
        }
        for (String tid : request.getTids()) {
            DataAssetPropSaveRequest item = new DataAssetPropSaveRequest();
            item.setTid(tid);
            item.setProps(request.getProps());
            propSaveOrUpdate(item);
        }
        return true;
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
                .eq(DataPropT::getParentId, tid)
                .eq(DataPropT::getIsDel, 0));
    }

    private void upsertProps(String tid, Map<String, Object> props) {
        Map<String, DataPropT> existing = new HashMap<>();
        activeProps(tid).forEach(prop -> existing.put(prop.getPropName(), prop));
        LocalDateTime now = LocalDateTime.now();
        List<DataPropT> inserts = new ArrayList<>();
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            String name = realPropName(entry.getKey());
            String value = entry.getValue() == null ? null : String.valueOf(entry.getValue());
            DataPropT prop = existing.get(name);
            if (prop == null) {
                prop = new DataPropT();
                prop.setTid(simpleId());
                prop.setParentId(tid);
                prop.setPropName(name);
                prop.setPropType(propType(entry.getKey()));
                prop.setPropValue(value);
                prop.setDataType("catalog");
                prop.setIsDel(0);
                prop.setCreatedTime(now);
                prop.setUpdatedTime(now);
                inserts.add(prop);
            } else if (!Objects.equals(prop.getPropValue(), value)
                    || !Objects.equals(prop.getPropType(), propType(entry.getKey()))) {
                prop.setPropValue(value);
                prop.setPropType(propType(entry.getKey()));
                prop.setUpdatedTime(now);
                propService.updateById(prop);
            }
        }
        if (!inserts.isEmpty()) {
            propService.saveBatch(inserts);
        }
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
        try {
            esCommonService.saveOrUpdate(INDEX_NAME, tid, objectMapper.writeValueAsString(document), false);
        } catch (JsonProcessingException e) {
            throw new BizException(500, "资产索引序列化失败");
        }
    }

    private DataAssetQueryByIdRequest queryRequest(String tid) {
        DataAssetQueryByIdRequest request = new DataAssetQueryByIdRequest();
        request.setTid(tid);
        return request;
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
