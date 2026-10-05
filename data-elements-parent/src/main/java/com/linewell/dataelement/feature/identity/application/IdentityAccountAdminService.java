package com.linewell.dataelement.feature.identity.application;

import com.linewell.dataelement.feature.identity.infrastructure.persistence.entity.IdentityAccountEntity;
import com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper.IdentityAccountMapper;
import com.linewell.dataelement.platform.persistence.id.NumericId;
import com.linewell.dataelement.platform.tenant.application.TenantAccessService;
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.UseControlDataSource;
import com.linewell.dataelement.utils.PwdRuleUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

@Service
@UseControlDataSource
public class IdentityAccountAdminService {

    private static final Pattern STORED_PASSWORD = Pattern.compile("(?i)^[0-9a-f]{96}$");

    private final IdentityAccountMapper accountMapper;
    private final TenantAccessService tenantAccessService;
    @Value("${idaas.legacy-control-accounts.enabled:false}")
    private boolean legacyControlAccountsEnabled;

    public IdentityAccountAdminService(
            IdentityAccountMapper accountMapper,
            TenantAccessService tenantAccessService
    ) {
        this.accountMapper = accountMapper;
        this.tenantAccessService = tenantAccessService;
    }

    @Transactional
    public Map<String, Object> save(
            Map<String, Object> body,
            String tenantId,
            String operator
    ) {
        requireCurrentTenant(tenantId);
        String userId = text(body, "id");
        boolean creating = userId == null;
        String userName = required(body, "userName", "用户账号不能为空");
        String password = text(body, "password");
        IdentityAccountEntity duplicate = accountMapper.selectActiveByUserName(userName);
        if (duplicate != null && (creating || !duplicate.getId().equals(userId))) {
            throw new TenantAccessException("IDENTITY-ACCOUNT-DUPLICATE", "用户账号已存在");
        }

        IdentityAccountEntity account;
        LocalDateTime now = LocalDateTime.now();
        if (creating) {
            if (password == null || "******".equals(password)) {
                throw new TenantAccessException("IDENTITY-PASSWORD-REQUIRED", "新增用户密码不能为空");
            }
            account = new IdentityAccountEntity();
            userId = NumericId.nextId();
            account.setId(userId);
            account.setCreateId(operator);
            account.setCreateTime(now);
            account.setDeleted(0);
            account.setUserType(0);
            account.setIsTemporary(0);
        } else {
            account = accountMapper.selectTenantAccount(userId, tenantId);
            if (account == null) {
                throw new TenantAccessException("IDENTITY-ACCOUNT-NOT-FOUND", "当前租户下未找到该用户");
            }
        }

        account.setRealName(required(body, "realName", "姓名不能为空"));
        account.setPhone(required(body, "phone", "手机号码不能为空"));
        account.setUserName(userName);
        account.setStatus(integer(body.get("status"), "账号状态不能为空"));
        account.setIdCard(text(body, "idCard"));
        account.setGender(text(body, "gender"));
        account.setDateBirth(date(body.get("dateBirth")));
        account.setNation(text(body, "nation"));
        account.setAuthorizedStrength(text(body, "authorizedStrength"));
        account.setOriginPosition(text(body, "originPosition"));
        account.setOriginRank(text(body, "originRank"));
        account.setOfficeAddress(text(body, "officeAddress"));
        account.setAppFileId(text(body, "appFileId"));
        account.setJobNumber(text(body, "jobNumber"));
        account.setUpdateId(operator);
        account.setUpdateTime(now);
        if (password != null && !"******".equals(password)) {
            account.setPassword(STORED_PASSWORD.matcher(password).matches()
                    ? password.toUpperCase()
                    : PwdRuleUtil.encryptPwdBySalt(password));
        }

        if (creating) {
            accountMapper.insert(account);
        } else {
            accountMapper.updateById(account);
        }
        tenantAccessService.assignUserInCurrentTenant(userId, tenantId, operator);
        return Map.of("id", userId);
    }

    public Map<String, Object> page(Map<String, Object> body, String tenantId) {
        requireCurrentTenant(tenantId);
        long pageNum = positiveLong(body.get("pageNum"), 1);
        long pageSize = Math.min(positiveLong(body.get("pageSize"), 20), 200);
        List<String> ids = ids(body.get("ids"));
        if (body.containsKey("ids") && ids.isEmpty()) {
            return Map.of("list", List.of(), "total", 0L);
        }
        String userName = text(body, "userName");
        String realName = text(body, "realName");
        long total = accountMapper.countTenantAccounts(tenantId, userName, realName, ids);
        List<Map<String, Object>> rows = total == 0
                ? List.of()
                : accountMapper.selectTenantAccounts(
                        tenantId, userName, realName, ids,
                        (pageNum - 1) * pageSize, pageSize
                ).stream().map(this::toMap).toList();
        return Map.of("list", rows, "total", total);
    }

    public List<Map<String, Object>> list(String tenantId, String userName) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("pageNum", 1);
        filters.put("pageSize", 200);
        filters.put("userName", userName);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) page(filters, tenantId)
                .get("list");
        return rows;
    }

    private Map<String, Object> toMap(IdentityAccountEntity account) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", account.getId());
        row.put("realName", account.getRealName());
        row.put("userName", account.getUserName());
        row.put("password", "******");
        row.put("phone", account.getPhone());
        row.put("idCard", account.getIdCard());
        row.put("gender", account.getGender());
        row.put("dateBirth", account.getDateBirth());
        row.put("nation", account.getNation());
        row.put("status", account.getStatus());
        row.put("appFileId", account.getAppFileId());
        row.put("officeAddress", account.getOfficeAddress());
        row.put("jobNumber", account.getJobNumber());
        row.put("authorizedStrength", account.getAuthorizedStrength());
        row.put("originPosition", account.getOriginPosition());
        row.put("originRank", account.getOriginRank());
        row.put("createTime", account.getCreateTime());
        row.put("updateTime", account.getUpdateTime());
        return row;
    }

    private void requireCurrentTenant(String tenantId) {
        if (!legacyControlAccountsEnabled) throw new TenantAccessException("TENANT-ACCOUNT-SOURCE-RETIRED", "共享账号维护已退役，请通过所属应用的 Magic API 维护本地账号");
        if (tenantId == null || !tenantId.equals(TenantContext.requireTenantId())) {
            throw new TenantAccessException("IDENTITY-TENANT-MISMATCH", "用户操作租户与当前会话不一致");
        }
    }

    private String required(Map<String, Object> body, String key, String message) {
        String value = text(body, key);
        if (value == null) {
            throw new TenantAccessException("IDENTITY-PARAM-INVALID", message);
        }
        return value;
    }

    private String text(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).trim();
    }

    private int integer(Object value, String message) {
        if (value == null || String.valueOf(value).isBlank()) {
            throw new TenantAccessException("IDENTITY-PARAM-INVALID", message);
        }
        return value instanceof Number number
                ? number.intValue()
                : Integer.parseInt(String.valueOf(value));
    }

    private long positiveLong(Object value, long fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return Math.max(1L, Long.parseLong(String.valueOf(value)));
    }

    private LocalDate date(Object value) {
        return value == null || String.valueOf(value).isBlank()
                ? null
                : LocalDate.parse(String.valueOf(value).substring(0, 10));
    }

    private List<String> ids(Object value) {
        if (value == null) {
            return null;
        }
        List<String> result = new ArrayList<>();
        if (value instanceof Iterable<?> values) {
            values.forEach(item -> addId(result, item));
        } else {
            for (String item : String.valueOf(value).split(",")) {
                addId(result, item);
            }
        }
        return result;
    }

    private void addId(List<String> ids, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            ids.add(String.valueOf(value).trim());
        }
    }
}
