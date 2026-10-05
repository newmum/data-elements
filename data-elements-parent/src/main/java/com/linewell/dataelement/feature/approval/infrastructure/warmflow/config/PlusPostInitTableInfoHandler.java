package com.linewell.dataelement.feature.approval.infrastructure.warmflow.config;

/**
 * @author zwenbo
 * @Description: TODO
 * @date 2026/3/23
 */

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import com.baomidou.mybatisplus.core.handlers.PostInitTableInfoHandler;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import org.apache.ibatis.session.Configuration;
import org.dromara.warm.flow.core.exception.FlowException;
import org.springframework.stereotype.Component;

/**
 * 关闭逻辑删除，需高版本比如3.5.3或者以上
 *
 * @author warm
 */
@Component
public class PlusPostInitTableInfoHandler implements PostInitTableInfoHandler {

    List<String> tableNames = Arrays.asList("flow_definition", "flow_node", "flow_skip", "flow_instance", "flow_task"
        , "flow_his_task", "flow_user");
    @Override
    public void postTableInfo(TableInfo tableInfo, Configuration configuration) {
        String tableName = tableInfo.getTableName();
        if (tableNames.contains(tableName)) {
            Class<?> clazz = tableInfo.getClass();
            Field[] fields = clazz.getDeclaredFields();
            try {
                for (Field field : fields) {
                    if ("withLogicDelete".equals(field.getName())) {
                        field.setAccessible(true);
                        // 关闭逻辑删除
                        field.set(tableInfo, false);
                    }
                }
            } catch (IllegalAccessException e) {
                throw new FlowException("反射设置对象值异常");
            }
        }
    }
}
