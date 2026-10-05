package com.linewell.dataelement.feature.approval.infrastructure.warmflow.designer;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.linewell.dataelement.feature.identity.application.IdentityDirectoryService;
import com.linewell.dataelement.feature.identity.domain.IdentityDirectoryEntry;
import com.linewell.dataelement.feature.approval.infrastructure.warmflow.designer.model.FlowOrgVo;
import com.linewell.dataelement.feature.approval.infrastructure.warmflow.designer.model.FlowRoleVo;
import com.linewell.dataelement.feature.approval.infrastructure.warmflow.designer.model.FlowUserVo;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        List<FlowRoleVo> rows = page.getRecords().stream().map(this::toRole).toList();
        HandlerFunDto<FlowRoleVo> handler = new HandlerFunDto<>(rows, page.getTotal())
                .setStorageId(role -> "role:" + role.getId())
                .setHandlerCode(FlowRoleVo::getCodeNum)
                .setHandlerName(FlowRoleVo::getName)
                .setCreateTime(role -> formatDate(role.getCreateTime()));
        return getHandlerSelectVo(handler);
    }

    private HandlerSelectVo getOrganization(HandlerQuery query) {
        IPage<IdentityDirectoryEntry> page = directoryService.pageOrganizations(
                query.getPageNum(), query.getPageSize(), query.getHandlerName()
        );
        List<FlowOrgVo> rows = page.getRecords().stream().map(this::toOrganization).toList();
        HandlerFunDto<FlowOrgVo> handler = new HandlerFunDto<>(rows, page.getTotal())
                .setStorageId(org -> "org:" + org.getId())
                .setHandlerCode(FlowOrgVo::getSerialNumber)
                .setHandlerName(FlowOrgVo::getName)
                .setCreateTime(org -> formatDate(org.getCreateTime()));
        return getHandlerSelectVo(handler);
    }

    private HandlerSelectVo getUser(HandlerQuery query) {
        IPage<IdentityDirectoryEntry> page = directoryService.pageUsers(
                query.getPageNum(), query.getPageSize(), query.getGroupId(),
                query.getHandlerCode(), query.getHandlerName()
        );
        List<FlowUserVo> users = page.getRecords().stream().map(this::toUser).toList();
        List<FlowOrgVo> organizations = directoryService.listOrganizations().stream()
                .map(this::toOrganization)
                .toList();
        HandlerFunDto<FlowUserVo> handler = new HandlerFunDto<>(users, page.getTotal())
                .setStorageId(FlowUserVo::getId)
                .setHandlerCode(FlowUserVo::getUserName)
                .setHandlerName(FlowUserVo::getRealName)
                .setCreateTime(user -> formatDate(user.getCreateTime()));
        TreeFunDto<FlowOrgVo> tree = new TreeFunDto<>(organizations)
                .setId(FlowOrgVo::getId)
                .setName(FlowOrgVo::getName)
                .setParentId(FlowOrgVo::getParentId);
        return getHandlerSelectVo(handler, tree);
    }

    private FlowRoleVo toRole(IdentityDirectoryEntry item) {
        FlowRoleVo role = new FlowRoleVo();
        role.setId(item.getId());
        role.setName(item.getName());
        role.setCodeNum(item.getCode());
        role.setAppId(item.getAppId());
        role.setDescription(item.getDescription());
        role.setStatus(item.getStatus() == null ? 0 : item.getStatus());
        role.setSortNum(item.getSortNum() == null ? 0 : item.getSortNum());
        role.setCreateTime(item.getCreateTime());
        role.setUpdateTime(item.getUpdateTime());
        return role;
    }

    private FlowOrgVo toOrganization(IdentityDirectoryEntry item) {
        FlowOrgVo org = new FlowOrgVo();
        org.setId(item.getId());
        org.setName(item.getName());
        org.setParentId(item.getParentId());
        org.setSerialNumber(item.getCode());
        org.setStatus(item.getStatus() == null ? 0 : item.getStatus());
        org.setSortNum(item.getSortNum() == null ? 0 : item.getSortNum());
        org.setCreateTime(item.getCreateTime());
        org.setUpdateTime(item.getUpdateTime());
        return org;
    }

    private FlowUserVo toUser(IdentityDirectoryEntry item) {
        FlowUserVo user = new FlowUserVo();
        user.setId(item.getId());
        user.setUserName(item.getCode());
        user.setRealName(item.getName());
        user.setPhone(item.getPhone());
        user.setStatus(item.getStatus() == null ? 0 : item.getStatus());
        user.setCreateTime(item.getCreateTime());
        user.setUpdateTime(item.getUpdateTime());
        return user;
    }

    private String formatDate(Date date) {
        return date == null ? "" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }
}
