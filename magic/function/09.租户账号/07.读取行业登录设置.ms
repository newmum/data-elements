{
  "id": "portal_fn_settings_v1",
  "groupId": "ga_local_accounts_functions_01",
  "name": "07.读取行业登录设置",
  "path": "/settings",
  "parameters": [],
  "description": "GET 和 HEAD 行业登录设置共用，核验启用租户后通过现有缓存读取其公开字段，不创建会话。"
}
================================
import '@/tenant-account/response' as respond
import com.linewell.dataelement.model.common.CommonResponse
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException
import db
import request
import systemConfig

// /tenant-account/settings 绑定 tenantId 参数，从该启用行业读取允许公开的品牌配置。
return respond(() => {
    var values = request.getValues('tenantId')
    if (values == null) {
        values = request.getValues('tenantId[]')
    }
    if (values == null || values.isEmpty()) {
        return CommonResponse.fail(20001, '缺少必要参数: tenantId', '参数缺失')
    }
    // 保留 Spring 字符串参数的多值拼接与普通参数优先级。
    var tenantId = values.join(',')
    var active = db.normal().table('sym_tenant_t').columns('tid').where()
        .eq('tid', tenantId).eq('status', 1).eq('is_del', 0).selectOne()
    if (active == null) {
        throw new TenantAccessException('TENANT-NOT-AVAILABLE', 'Requested tenant is not available')
    }
    return systemConfig.publicSettings(active.tid)
})
