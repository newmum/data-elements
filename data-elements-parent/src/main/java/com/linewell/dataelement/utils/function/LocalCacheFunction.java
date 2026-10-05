package com.linewell.dataelement.utils.function;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.linewell.dataelement.dataassets.base.entity.DataPropT;
import com.linewell.dataelement.dataassets.base.service.IDataPropTService;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity.DataAccessAggTaskT;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.IDataAccessAggTaskTService;
import com.linewell.dataelement.feature.identity.application.IdentityOrganizationLookupService;
import com.linewell.dataelement.feature.identity.domain.IdentityOrganization;
import com.linewell.dataelement.utils.LocalCache;
import java.util.List;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

@Component
@MagicModule("localCache")
public class LocalCacheFunction {

    private final IDataPropTService dataPropTService;
    private final IDataAccessAggTaskTService dataAccessAggTaskTService;
    private final IdentityOrganizationLookupService organizationLookupService;

    public LocalCacheFunction(
        IDataPropTService dataPropTService,
        IDataAccessAggTaskTService dataAccessAggTaskTService,
        IdentityOrganizationLookupService organizationLookupService
    ) {
        this.dataPropTService = dataPropTService;
        this.dataAccessAggTaskTService = dataAccessAggTaskTService;
        this.organizationLookupService = organizationLookupService;
    }

    @Comment("获取本地应用数据库表名称缓存")
    public String get(String tid, String assetType) {
        if (tid == null || tid.isBlank()) {
            return null;
        }

        String value = resolveValue(tid, assetType);
        if (value != null && !value.isBlank()) {
            LocalCache.put(tid, value);
            return value;
        }
        return LocalCache.get(tid);
    }

    private String resolveValue(String tid, String assetType) {
        if (assetType == null) {
            return null;
        }
        return switch (assetType) {
            case "app" -> getPropValue(tid, "appName");
            case "db" -> getPropValue(tid, "dbName");
            case "table" -> getPropValue(tid, "tableName");
            case "catalog" -> getPropValue(tid, "catalogName");
            case "org" -> getOrgPath(tid);
            case "orgPath" -> getOrgSerialNumber(tid);
            case "accessTask" -> getAccessTaskName(tid);
            default -> null;
        };
    }

    private String getPropValue(String tid, String propName) {
        Page<DataPropT> page = dataPropTService.page(
            new Page<>(1, 1),
            new LambdaQueryWrapper<DataPropT>()
                .eq(DataPropT::getParentId, tid)
                .eq(DataPropT::getPropName, propName)
                .eq(DataPropT::getIsDel, 0)
        );
        if (page == null) {
            return null;
        }
        List<DataPropT> props = page.getRecords();
        if (props == null || props.isEmpty()) {
            return null;
        }
        return props.get(0).getPropValue();
    }

    private String getOrgPath(String tid) {
        IdentityOrganization org = organizationLookupService.getById(tid);
        return org == null ? null : org.getOrgPath();
    }

    private String getOrgSerialNumber(String tid) {
        String id = tid;
        int index = tid.indexOf(':');
        if (index > -1) {
            id = tid.substring(0, index);
        }
        IdentityOrganization org = organizationLookupService.getById(id);
        return org == null ? null : org.getSerialNumber();
    }

    private String getAccessTaskName(String tid) {
        Page<DataAccessAggTaskT> page = dataAccessAggTaskTService.page(
            new Page<>(1, 1),
            new LambdaQueryWrapper<DataAccessAggTaskT>()
                .eq(DataAccessAggTaskT::getTid, tid)
                .eq(DataAccessAggTaskT::getIsDel, 0)
        );
        if (page == null) {
            return null;
        }
        List<DataAccessAggTaskT> tasks = page.getRecords();
        DataAccessAggTaskT task = (tasks == null || tasks.isEmpty()) ? null : tasks.get(0);
        return task == null ? null : task.getTaskName();
    }
}
