package com.linewell.dataelement.integration.workflow;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.linewell.dataelement.feature.identity.application.IdentityDirectoryService;
import com.linewell.dataelement.feature.identity.domain.IdentityDirectoryEntry;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.dromara.warm.flow.ui.dto.HandlerFunDto;
import org.dromara.warm.flow.ui.dto.HandlerQuery;
import org.dromara.warm.flow.ui.dto.TreeFunDto;
import org.dromara.warm.flow.ui.service.HandlerSelectService;
import org.dromara.warm.flow.ui.vo.HandlerFeedBackVo;
import org.dromara.warm.flow.ui.vo.HandlerSelectVo;
import org.springframework.stereotype.Component;

/**
 * Warm-Flow designer participant selector backed by the platform identity directory.
 */
@Component
public class HandlerSelectServiceImpl implements HandlerSelectService {

    private final IdentityDirectoryService directoryService;

    public HandlerSelectServiceImpl(IdentityDirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    @Override
    public List<String> getHandlerType() {
        return Arrays.asList("用户", "角色", "组织");
    }

    @Override
    public HandlerSelectVo getHandlerSelect(HandlerQuery query) {
        return switch (query.getHandlerType()) {
            case "角色" -> getRole(query);
            case "组织", "部门" -> getOrganization(query);
            default -> getUser(query);
        };
    }

    @Override
    public List<HandlerFeedBackVo> handlerFeedback(List<String> storageIds) {
        if (storageIds == null || storageIds.isEmpty()) {
            return List.of();
        }
        List<String> roleIds = new ArrayList<>();
        List<String> orgIds = new ArrayList<>();
        List<String> userIds = new ArrayList<>();
        for (String storageId : storageIds) {
            if (storageId.startsWith("role:")) {
                roleIds.add(storageId.substring("role:".length()));
            } else if (storageId.startsWith("org:")) {
                orgIds.add(storageId.substring("org:".length()));
            } else {
                userIds.add(storageId);
            }
        }

        Map<String, String> names = new HashMap<>();
        directoryService.findRoles(roleIds).forEach(item -> names.put("role:" + item.getId(), item.getName()));
        directoryService.findOrganizations(orgIds).forEach(item -> names.put("org:" + item.getId(), item.getName()));
        directoryService.findUsers(userIds).forEach(item -> names.put(item.getId(), item.getName()));
        return storageIds.stream()
                .map(storageId -> new HandlerFeedBackVo(storageId, names.getOrDefault(storageId, storageId)))
                .toList();
    }

    private HandlerSelectVo getRole(HandlerQuery query) {
        IPage<IdentityDirectoryEntry> page = directoryService.pageRoles(
                query.getPageNum(), query.getPageSize(), query.getHandlerCode(), query.getHandlerName()
        );
        return getHandlerSelectVo(handlerRows(page, role -> "role:" + role.getId()));
    }

    private HandlerSelectVo getOrganization(HandlerQuery query) {
        IPage<IdentityDirectoryEntry> page = directoryService.pageOrganizations(
                query.getPageNum(), query.getPageSize(), query.getHandlerName()
        );
        return getHandlerSelectVo(handlerRows(page, org -> "org:" + org.getId()));
    }

    private HandlerSelectVo getUser(HandlerQuery query) {
        IPage<IdentityDirectoryEntry> page = directoryService.pageUsers(
                query.getPageNum(), query.getPageSize(), query.getGroupId(),
                query.getHandlerCode(), query.getHandlerName()
        );
        var tree = new TreeFunDto<>(directoryService.listOrganizations())
                .setId(IdentityDirectoryEntry::getId)
                .setName(IdentityDirectoryEntry::getName)
                .setParentId(IdentityDirectoryEntry::getParentId);
        return getHandlerSelectVo(handlerRows(page, IdentityDirectoryEntry::getId), tree);
    }

    private HandlerFunDto<IdentityDirectoryEntry> handlerRows(IPage<IdentityDirectoryEntry> page,
            Function<IdentityDirectoryEntry, String> storageId) {
        return new HandlerFunDto<>(page.getRecords(), page.getTotal())
                .setStorageId(storageId)
                .setHandlerCode(IdentityDirectoryEntry::getCode)
                .setHandlerName(IdentityDirectoryEntry::getName)
                .setCreateTime(entry -> formatDate(entry.getCreateTime()));
    }

    private String formatDate(Date date) {
        return date == null ? "" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }
}
