package com.linewell.dataelement.feature.identity.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.linewell.dataelement.feature.identity.domain.IdentityDirectoryEntry;
import com.linewell.dataelement.feature.identity.infrastructure.persistence.entity.IdentityUserOrganizationRelationEntity;
import com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper.IdentityDirectoryMapper;
import com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper.IdentityUserOrganizationRelationMapper;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class IdentityDirectoryServiceImpl implements IdentityDirectoryService {

    private final IdentityDirectoryMapper mapper;
    private final IdentityUserOrganizationRelationMapper organizationRelationMapper;
    private final TenantIdentityQueryService controlIdentityService;

    public IdentityDirectoryServiceImpl(
            IdentityDirectoryMapper mapper,
            IdentityUserOrganizationRelationMapper organizationRelationMapper,
            TenantIdentityQueryService controlIdentityService
    ) {
        this.mapper = mapper;
        this.organizationRelationMapper = organizationRelationMapper;
        this.controlIdentityService = controlIdentityService;
    }

    @Override
    public IPage<IdentityDirectoryEntry> pageUsers(
            long pageNum,
            long pageSize,
            String orgId,
            String code,
            String name
    ) {
        String tenantId = TenantContext.requireTenantId();
        List<String> userIds = orgId == null || orgId.isBlank()
                ? null
                : organizationRelationMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<IdentityUserOrganizationRelationEntity>()
                                .eq(IdentityUserOrganizationRelationEntity::getTenantId, tenantId)
                                .eq(IdentityUserOrganizationRelationEntity::getOrgId, orgId)
                ).stream().map(IdentityUserOrganizationRelationEntity::getUserId).toList();
        if (userIds != null && userIds.isEmpty()) {
            return Page.of(Math.max(pageNum, 1L), Math.min(Math.max(pageSize, 1L), 200L), 0L);
        }
        Map<String, Object> identityPage = controlIdentityService.page(
                tenantId, pageNum, pageSize, userIds, code, name
        );
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) identityPage.get("list");
        List<IdentityDirectoryEntry> records = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            IdentityDirectoryEntry entry = new IdentityDirectoryEntry();
            entry.setId(text(row.get("id")));
            entry.setCode(text(row.get("userName")));
            entry.setName(text(row.get("realName")));
            entry.setPhone(text(row.get("phone")));
            entry.setStatus(number(row.get("status")));
            records.add(entry);
        }
        Page<IdentityDirectoryEntry> result = Page.of(
                Math.max(pageNum, 1L), Math.min(Math.max(pageSize, 1L), 200L),
                ((Number) identityPage.get("total")).longValue()
        );
        result.setRecords(records);
        return result;
    }

    @Override
    public IPage<IdentityDirectoryEntry> pageRoles(long pageNum, long pageSize, String code, String name) {
        return mapper.selectRolePage(page(pageNum, pageSize), TenantContext.requireTenantId(), code, name);
    }

    @Override
    public IPage<IdentityDirectoryEntry> pageOrganizations(long pageNum, long pageSize, String name) {
        return mapper.selectOrganizationPage(page(pageNum, pageSize), TenantContext.requireTenantId(), name);
    }

    @Override
    public List<IdentityDirectoryEntry> listOrganizations() {
        return mapper.selectAllOrganizations(TenantContext.requireTenantId());
    }

    @Override
    public List<IdentityDirectoryEntry> findUsers(List<String> ids) {
        if (empty(ids)) {
            return Collections.emptyList();
        }
        // The directory adapter caps each page at 200. Read every requested ID in
        // bounded batches; a single page silently dropped the remainder.
        List<String> requested = ids.stream().distinct().toList();
        if (requested.size() > 2_000) {
            throw new IllegalArgumentException("一次最多查询2000名用户");
        }
        String tenantId = TenantContext.requireTenantId();
        Map<String, IdentityDirectoryEntry> entries = new LinkedHashMap<>();
        for (int start = 0; start < requested.size(); start += 200) {
            List<String> batch = requested.subList(start, Math.min(start + 200, requested.size()));
            Map<String, Object> page = controlIdentityService.page(tenantId, 1, 200, batch, null, null);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) page.get("list");
            for (Map<String, Object> row : rows) {
                String id = text(row.get("id"));
                if (!batch.contains(id)) {
                    throw new IllegalStateException("目录接口返回了未请求的用户");
                }
                IdentityDirectoryEntry entry = new IdentityDirectoryEntry();
                entry.setId(id);
                entry.setCode(text(row.get("userName")));
                entry.setName(text(row.get("realName")));
                entry.setPhone(text(row.get("phone")));
                entry.setStatus(number(row.get("status")));
                entries.put(id, entry);
            }
        }
        return ids.stream().map(entries::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public List<IdentityDirectoryEntry> findRoles(List<String> ids) {
        return empty(ids) ? Collections.emptyList() : mapper.selectRoles(ids, TenantContext.requireTenantId());
    }

    @Override
    public List<IdentityDirectoryEntry> findOrganizations(List<String> ids) {
        return empty(ids) ? Collections.emptyList() : mapper.selectOrganizations(ids, TenantContext.requireTenantId());
    }

    private Page<IdentityDirectoryEntry> page(long pageNum, long pageSize) {
        return Page.of(Math.max(pageNum, 1L), Math.min(Math.max(pageSize, 1L), 200L));
    }

    private boolean empty(List<String> ids) {
        return ids == null || ids.isEmpty();
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer number(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }
}
