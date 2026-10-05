package com.linewell.dataelement.utils.function;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.linewell.dataelement.dataassets.base.service.IDataPropTService;
import com.linewell.dataelement.feature.identity.application.IdentityOrganizationLookupService;
import com.linewell.dataelement.feature.identity.domain.IdentityOrganization;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity.DataAccessAggTaskT;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.IDataAccessAggTaskTService;
import com.linewell.dataelement.utils.LocalCache;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class LocalCacheFunctionTest {

    private final IDataPropTService dataPropTService = Mockito.mock(IDataPropTService.class);
    private final IDataAccessAggTaskTService dataAccessAggTaskTService = Mockito.mock(IDataAccessAggTaskTService.class);
    private final IdentityOrganizationLookupService organizationLookupService = Mockito.mock(IdentityOrganizationLookupService.class);
    private final LocalCacheFunction function = new LocalCacheFunction(
        dataPropTService,
        dataAccessAggTaskTService,
        organizationLookupService
    );

    @AfterEach
    void tearDown() {
        LocalCache.invalidate("cache-hit");
        LocalCache.invalidate("tid-org");
        LocalCache.invalidate("tid-task");
    }

    @Test
    void returnsCachedValueImmediately() {
        when(dataPropTService.page(ArgumentMatchers.any(Page.class), ArgumentMatchers.any(LambdaQueryWrapper.class))).thenReturn(null);
        LocalCache.put("cache-hit", "AppName");
        assertEquals("AppName", function.get("cache-hit", "app"));
    }

    @Test
    void resolvesOrgPathFromIdentityOrganization() {
        IdentityOrganization org = new IdentityOrganization();
        org.setOrgPath("000600010002");
        when(organizationLookupService.getById("tid-org")).thenReturn(org);
        assertEquals("000600010002", function.get("tid-org", "org"));
    }

    @Test
    void resolvesAccessTaskName() {
        DataAccessAggTaskT task = new DataAccessAggTaskT();
        task.setTaskName("任务A");
        Page<DataAccessAggTaskT> page = new Page<>(1, 1);
        page.setRecords(Collections.singletonList(task));
        when(dataAccessAggTaskTService.page(ArgumentMatchers.any(Page.class), ArgumentMatchers.any(LambdaQueryWrapper.class))).thenReturn(page);
        assertEquals("任务A", function.get("tid-task", "accessTask"));
    }
}
