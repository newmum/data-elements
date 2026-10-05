package com.linewell.dataelement.feature.approval.infrastructure.warmflow.handler;

import java.util.Date;
import java.util.Objects;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.RootEntity;
import org.dromara.warm.flow.core.handler.DataFillHandler;
import org.dromara.warm.flow.core.handler.PermissionHandler;
import org.dromara.warm.flow.core.utils.IdUtils;
import org.dromara.warm.flow.core.utils.ObjectUtil;
import org.dromara.warm.flow.core.utils.StringUtils;

/**
 * 填充器 （可通过配置文件注入，也可用@Bean/@Component方式）
 *
 * @author warm
 */
public class CustomDataFillHandler implements DataFillHandler {

    @Override
    public void idFill(Object object) {
        RootEntity entity = (RootEntity) object;
        if (ObjectUtil.isNotNull(entity)) {
            if (Objects.isNull(entity.getId())) {
                entity.setId(IdUtils.nextId());
            }
        }
    }

    @Override
    public void insertFill(Object object) {
        RootEntity entity = (RootEntity) object;
        if (ObjectUtil.isNotNull(entity)) {
            entity.setCreateTime(ObjectUtil.isNotNull(entity.getCreateTime()) ? entity.getCreateTime() : new Date());
            entity.setUpdateTime(ObjectUtil.isNotNull(entity.getUpdateTime()) ? entity.getUpdateTime() : new Date());

            PermissionHandler permissionHandler = FlowEngine.permissionHandler();
            String handler = null;
            if (permissionHandler != null) {
                handler = permissionHandler.getHandler();
            }
            entity.setCreateBy(StringUtils.isNotEmpty(handler) ? handler : entity.getCreateBy());
            entity.setUpdateBy(StringUtils.isNotEmpty(handler) ? handler : entity.getUpdateBy());
        }
    }

    @Override
    public void updateFill(Object object) {
        RootEntity entity = (RootEntity) object;
        if (ObjectUtil.isNotNull(entity)) {
            entity.setUpdateTime(ObjectUtil.isNotNull(entity.getUpdateTime()) ? entity.getUpdateTime() : new Date());

            PermissionHandler permissionHandler = FlowEngine.permissionHandler();
            String handler = null;
            if (permissionHandler != null) {
                handler = permissionHandler.getHandler();
            }
            entity.setUpdateBy(StringUtils.isNotEmpty(handler) ? handler : entity.getUpdateBy());
        }
    }
}
