package com.linewell.dataelement.platform.configuration;

import cn.hutool.core.util.IdUtil;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.TenantDataSourceRegistry;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/** Tenant-owned dynamic forms live in the selected tenant database. */
@Component
@MagicModule("tenantForm")
public class TenantFormMagicModule {

    private final TenantDataSourceRegistry tenantDataSourceRegistry;

    public TenantFormMagicModule(TenantDataSourceRegistry tenantDataSourceRegistry) {
        this.tenantDataSourceRegistry = tenantDataSourceRegistry;
    }

    @Comment("Read dynamic forms from the current tenant database")
    public List<Map<String, Object>> list(Object formName, Object env, Object tid) {
        StringBuilder sql = new StringBuilder(
                "select tid, form_name as formName, form_json as formJson, form_config as formConfig, "
                        + "env, created_time as createdTime, updated_time as updatedTime, is_del as isDel, "
                        + "tenant_id as tenantId from sym_form where is_del = 0");
        List<Object> args = new ArrayList<>();
        appendEquals(sql, args, "tid", text(tid));
        appendEquals(sql, args, "form_name", text(formName));
        appendEquals(sql, args, "env", text(env));
        sql.append(" order by created_time");
        return jdbc().queryForList(sql.toString(), args.toArray());
    }

    @Comment("Save a dynamic form into the current tenant database")
    public String save(Map<String, Object> body) {
        String tid = text(body.get("tid"));
        String formName = required(body, "formName", "表单名称不能为空");
        String formJson = required(body, "formJson", "表单配置不能为空");
        String env = text(body.get("env"));
        JdbcTemplate jdbc = jdbc();
        if (tid.isBlank()) {
            int duplicate = jdbc.queryForObject(
                    "select count(*) from sym_form where form_name = ? and env = ? and is_del = 0",
                    Integer.class,
                    formName,
                    env
            );
            if (duplicate > 0) {
                throw new IllegalArgumentException(env + "运行环境下已存在同名表单");
            }
            tid = IdUtil.getSnowflakeNextIdStr();
            jdbc.update(
                    "insert into sym_form (tid, form_name, form_json, form_config, env, created_time, updated_time, is_del, tenant_id) "
                            + "values (?, ?, ?, ?, ?, ?, ?, 0, ?)",
                    tid,
                    formName,
                    formJson,
                    body.get("formConfig"),
                    env,
                    Timestamp.valueOf(LocalDateTime.now()),
                    Timestamp.valueOf(LocalDateTime.now()),
                    tenantId()
            );
            return tid;
        }
        int updated = jdbc.update(
                "update sym_form set form_name = ?, form_json = ?, form_config = ?, env = ?, updated_time = ? "
                        + "where tid = ? and is_del = 0",
                formName,
                formJson,
                body.get("formConfig"),
                env,
                Timestamp.valueOf(LocalDateTime.now()),
                tid
        );
        if (updated == 0) {
            throw new IllegalArgumentException("表单不存在或已删除");
        }
        return tid;
    }

    @Comment("Delete a dynamic form from the current tenant database")
    public boolean delete(Object tid) {
        String id = text(tid);
        if (id.isBlank()) {
            throw new IllegalArgumentException("表单ID不能为空");
        }
        return jdbc().update(
                "update sym_form set is_del = 1, updated_time = ? where tid = ? and is_del = 0",
                Timestamp.valueOf(LocalDateTime.now()),
                id
        ) > 0;
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(tenantDataSourceRegistry.dataSourceForTenant(tenantId()));
    }

    private String tenantId() {
        return TenantContext.requireTenantId();
    }

    private void appendEquals(StringBuilder sql, List<Object> args, String column, String value) {
        if (!value.isBlank()) {
            sql.append(" and ").append(column).append(" = ?");
            args.add(value);
        }
    }

    private String required(Map<String, Object> body, String field, String message) {
        String value = text(body.get(field));
        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String text(Object value) {
        return Objects.toString(value, "").trim();
    }
}
