package com.linewell.dataelement.feature.delivery.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linewell.dataelement.dataassets.base.entity.DaAssetApiRela;
import com.linewell.dataelement.dataassets.base.entity.DataApplyFormT;
import com.linewell.dataelement.dataassets.base.entity.DataPropT;
import com.linewell.dataelement.dataassets.base.service.IDaAssetApiRelaService;
import com.linewell.dataelement.dataassets.base.service.IDataApplyFormTService;
import com.linewell.dataelement.dataassets.base.service.IDataPropTService;
import com.linewell.dataelement.feature.delivery.infrastructure.persistence.entity.DataApiAuthorizationEntity;
import com.linewell.dataelement.feature.delivery.infrastructure.persistence.entity.DataDistributionTaskEntity;
import com.linewell.dataelement.feature.delivery.infrastructure.persistence.mapper.DataApiAuthorizationMapper;
import com.linewell.dataelement.feature.delivery.infrastructure.persistence.mapper.DataDistributionTaskMapper;
import com.linewell.dataelement.platform.persistence.id.NumericId;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.GatewayUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ResourceDeliveryService {

    private final IDataApplyFormTService applyFormService;
    private final IDaAssetApiRelaService assetApiRelaService;
    private final IDataPropTService dataPropService;
    private final DataDistributionTaskMapper distributionTaskMapper;
    private final DataApiAuthorizationMapper authorizationMapper;
    private final GatewayUtils gatewayUtils;

    public ResourceDeliveryService(
            IDataApplyFormTService applyFormService,
            IDaAssetApiRelaService assetApiRelaService,
            IDataPropTService dataPropService,
            DataDistributionTaskMapper distributionTaskMapper,
            DataApiAuthorizationMapper authorizationMapper,
            GatewayUtils gatewayUtils
    ) {
        this.applyFormService = applyFormService;
        this.assetApiRelaService = assetApiRelaService;
        this.dataPropService = dataPropService;
        this.distributionTaskMapper = distributionTaskMapper;
        this.authorizationMapper = authorizationMapper;
        this.gatewayUtils = gatewayUtils;
    }

    public void completeApprovedApplication(String applyFormId) {
        DataApplyFormT form = applyFormService.getById(applyFormId);
        if (form == null || !"catalogSubscribe".equals(form.getType())) {
            return;
        }
        List<String> catalogIds = splitIds(form.getCatalogIds());
        if (catalogIds.isEmpty()) {
            return;
        }

        List<DaAssetApiRela> apiRelas = assetApiRelaService.lambdaQuery()
                .in(DaAssetApiRela::getCatalogId, catalogIds)
                .eq(DaAssetApiRela::getIsDel, 0)
                .list();
        Map<String, List<DaAssetApiRela>> relasByCatalog = apiRelas.stream()
                .collect(Collectors.groupingBy(DaAssetApiRela::getCatalogId));

        Map<String, DataDistributionTaskEntity> tasksByCatalog = new HashMap<>();
        for (DataDistributionTaskEntity task : distributionTaskMapper.selectList(
                new LambdaQueryWrapper<DataDistributionTaskEntity>()
                        .eq(DataDistributionTaskEntity::getApplyFormId, form.getTid())
                        .eq(DataDistributionTaskEntity::getIsDel, 0)
                        .in(DataDistributionTaskEntity::getCatalogId, catalogIds))) {
            tasksByCatalog.putIfAbsent(task.getCatalogId(), task);
        }
        Map<String, DataApiAuthorizationEntity> authorizationByCatalogApi = new HashMap<>();
        for (DataApiAuthorizationEntity auth : authorizationMapper.selectList(
                new LambdaQueryWrapper<DataApiAuthorizationEntity>()
                        .eq(DataApiAuthorizationEntity::getApplyFormId, form.getTid())
                        .in(DataApiAuthorizationEntity::getCatalogId, catalogIds)
                        .eq(DataApiAuthorizationEntity::getIsDel, 0))) {
            if (authorizationByCatalogApi.putIfAbsent(authorizationKey(auth.getCatalogId(), auth.getApiId()), auth) != null)
                throw new IllegalStateException("同一申请的目录接口授权记录重复");
        }
        Map<String, Map<String, String>> propsByCatalog = new HashMap<>();
        for (DataPropT prop : dataPropService.lambdaQuery()
                .in(DataPropT::getParentId, catalogIds)
                .eq(DataPropT::getIsDel, 0)
                .in(DataPropT::getPropName, List.of("catalogName", "sourceTableId", "sourceTableName"))
                .list()) {
            propsByCatalog.computeIfAbsent(prop.getParentId(), ignored -> new HashMap<>())
                    .put(prop.getPropName(), StringUtils.defaultString(prop.getPropValue()));
        }

        List<DataApiAuthorizationEntity> pendingAuthorizations = new ArrayList<>();
        for (String catalogId : catalogIds) {
            List<DaAssetApiRela> relas = relasByCatalog.getOrDefault(catalogId, List.of());
            if (relas.isEmpty()) {
                if (!tasksByCatalog.containsKey(catalogId))
                    ensureDistributionTask(form, catalogId, propsByCatalog.getOrDefault(catalogId, Map.of()));
                continue;
            }
            for (DaAssetApiRela rela : relas) {
                String key = authorizationKey(catalogId, rela.getApiId());
                DataApiAuthorizationEntity existing = authorizationByCatalogApi.get(key);
                if (existing == null) {
                    existing = ensureAuthorization(form, catalogId, rela.getApiId());
                    authorizationByCatalogApi.put(key, existing);
                }
                pendingAuthorizations.add(existing);
            }
        }
        synchronizeGateway(form, pendingAuthorizations);
    }

    private void ensureDistributionTask(DataApplyFormT form, String catalogId, Map<String, String> props) {
        LocalDateTime now = LocalDateTime.now();
        DataDistributionTaskEntity task = new DataDistributionTaskEntity();
        task.setTid(NumericId.nextId());
        task.setApplyFormId(form.getTid());
        task.setCatalogId(catalogId);
        task.setCatalogName(props.getOrDefault("catalogName", form.getApplyName()));
        task.setSourceTableId(props.get("sourceTableId"));
        task.setSourceTableName(props.get("sourceTableName"));
        task.setTaskCode("DIST-" + form.getTid());
        task.setTaskName(task.getCatalogName() + "数据分发任务");
        task.setTaskStatus("GENERATED");
        task.setProgress(0);
        task.setExecutionEngine("NIFI_YUNTI");
        task.setExecutionMode("PENDING_INTEGRATION");
        task.setStatusMessage("申请审批通过，任务已生成，等待分发执行引擎接管");
        task.setTenantId(TenantContext.requireTenantId());
        task.setCreatedBy(form.getApplyUserId());
        task.setUpdatedBy(form.getApplyUserId());
        task.setCreatedTime(now);
        task.setUpdatedTime(now);
        task.setIsDel(0);
        distributionTaskMapper.insert(task);
    }

    private static String authorizationKey(String catalogId, String apiId) {
        return catalogId + "\u0000" + apiId;
    }

    private DataApiAuthorizationEntity ensureAuthorization(DataApplyFormT form, String catalogId, String apiId) {
        LocalDateTime now = LocalDateTime.now();
        DataApiAuthorizationEntity auth = new DataApiAuthorizationEntity();
        auth.setTid(NumericId.nextId());
        auth.setApplyFormId(form.getTid());
        auth.setCatalogId(catalogId);
        auth.setApiId(apiId);
        auth.setApplyUserId(form.getApplyUserId());
        auth.setAuthorizationStatus("AUTHORIZED");
        auth.setGatewaySyncStatus("PENDING");
        auth.setAuthorizationScope("API_INVOKE");
        auth.setGatewayMessage("平台审批已授权，等待同步网关");
        auth.setAuthorizedTime(now);
        auth.setExpireTime(now.plusYears(1));
        auth.setTenantId(TenantContext.requireTenantId());
        auth.setCreatedBy(form.getApplyUserId());
        auth.setUpdatedBy(form.getApplyUserId());
        auth.setCreatedTime(now);
        auth.setUpdatedTime(now);
        auth.setIsDel(0);
        authorizationMapper.insert(auth);
        return auth;
    }

    private void synchronizeGateway(
            DataApplyFormT form,
            List<DataApiAuthorizationEntity> authorizations
    ) {
        if (authorizations.isEmpty()) {
            return;
        }
        String applicationId = null;
        String syncStatus = "FAILED";
        String message;
        try {
            applicationId = gatewayUtils.getOrCreateAppId(form.getApplyUserId());
            if (StringUtils.isBlank(applicationId)) {
                throw new IllegalStateException("未获取到网关应用");
            }
            Set<String> apiIds = authorizations.stream()
                    .map(DataApiAuthorizationEntity::getApiId)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toSet());
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("applicationIds", List.of(applicationId));
            request.put("serviceIds", apiIds);
            HashMap<String, Object> response = gatewayUtils.callGatewayApi("V2CreateAuthorization", request);
            String error = String.valueOf(response.getOrDefault("error_message", ""));
            if (StringUtils.isNotBlank(error)) {
                throw new IllegalStateException(error);
            }
            syncStatus = "SUCCESS";
            message = "平台授权已同步至服务网关";
        } catch (Exception exception) {
            message = "平台授权已生效，网关同步待重试：" + exception.getMessage();
            log.warn("Gateway authorization synchronization failed, applyFormId={}", form.getTid(), exception);
        }

        LocalDateTime now = LocalDateTime.now();
        for (DataApiAuthorizationEntity auth : authorizations) {
            auth.setApplicationId(applicationId);
            auth.setGatewaySyncStatus(syncStatus);
            auth.setGatewayMessage(message);
            auth.setUpdatedBy(form.getApplyUserId());
            auth.setUpdatedTime(now);
            authorizationMapper.updateById(auth);
        }
    }

    private List<String> splitIds(String ids) {
        if (StringUtils.isBlank(ids)) {
            return List.of();
        }
        return List.of(ids.split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
    }
}
