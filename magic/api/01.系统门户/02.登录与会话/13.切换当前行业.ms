{
  "id": "portal_switch_v1",
  "groupId": "652292437ed443acba737511e3597522",
  "name": "13.切换当前行业",
  "path": "/switch",
  "method": "POST",
  "parameters": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "headers": [],
  "paths": [],
  "description": "验证请求租户与当前账号范围，读取目标资料后更新本令牌。"
}
================================
import '@/tenant-account/profile' as profile
import '@/tenant-account/response' as respond
import cn.dev33.satoken.stp.StpUtil
import cn.hutool.extra.spring.SpringUtil
import com.linewell.dataelement.platform.configuration.MagicBootConfiguration
import com.linewell.dataelement.platform.tenant.application.TenantSessions
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException
import com.linewell.dataelement.platform.tenant.domain.TenantContext
import com.linewell.dataelement.platform.tenant.web.TenantWebInterceptor
import org.springframework.http.converter.HttpMessageNotReadableException
import request
import tenantRuntime

// POST /portal/sso/switch 只修改当前行业令牌。LOCAL 只能继续访问原行业，旧共享账号使用原成员校验。
return respond(() => {
    var parseError = request.get().getAttribute(MagicBootConfiguration.MAGIC_PORTAL_SWITCH_JSON_ERROR)
    if (parseError != null) {
        throw parseError
    }
    if (!(body instanceof Map)) {
        throw new HttpMessageNotReadableException('Required request body is missing', null, null)
    }
    var requested = body.containsKey('tenantId') ? (body.tenantId == null ? 'null' : body.tenantId + '') : ''
    requested = requested.trim()
    var result
    if (TenantSessions.isLocal()) {
        if (tenantRuntime.id() != requested) {
            throw new TenantAccessException('TENANT-FORBIDDEN', '本地账号只能访问所属租户，请切换系统后重新登录')
        }
        result = profile(requested, true)
        if (result.isEmpty()) {
            throw new TenantAccessException('TENANT-NOT-AVAILABLE', 'Requested tenant is not available')
        }
    } else {
        if (SpringUtil.getBean('tenantAccountPolicy').isLocal(requested)) {
            throw new TenantAccessException('TENANT-FORBIDDEN', '所选租户使用独立账号，请重新登录')
        }
        var target = SpringUtil.getBean('tenantAccessService').requireMembership(StpUtil.getLoginId() + '', requested)
        // 保留旧租户上下文格式校验。先读取资料，失败或拒绝时保留当前令牌的行业。
        result = TenantContext.call(target.tenantId(), () => profile(target.tenantId(), false))
    }
    // 校验和数据库读取均成功后才提交当前令牌的行业。
    StpUtil.getTokenSession().set(TenantWebInterceptor.SESSION_TENANT_ID, requested)
    return result
})
