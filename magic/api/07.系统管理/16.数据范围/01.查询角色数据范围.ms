{
  "properties": {},
  "id": "sym_scope_role_v1",
  "script": null,
  "groupId": "sym_scope_group_v1",
  "name": "01.查询角色数据范围",
  "path": "/role",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "读取当前租户有效角色的数据范围。历史未配置或非法值按 OWNER 返回。"
}
================================
import '@/common/nifiLifecycleBody' as readBody
import '@/common/nifiPipelineReadHttp' as respond
import com.linewell.dataelement.model.common.CommonResponse
import java.lang.IllegalArgumentException
import java.util.Locale
import java.util.Map
import org.springframework.http.ResponseEntity
import db
import tenantRuntime

// POST /sym/scope/role 为权限树读取指定角色的范围。客户端资源编码只作为返回标识。
// 只查当前租户角色，缺失、删除或未配置的历史角色沿用 OWNER 默认范围。
return respond(() => {
    var signature = 'com.linewell.dataelement.model.common.CommonResponse<?> '
        + 'com.linewell.dataelement.feature.identity.api.DataScopeAuthorizationController'
        + '.getRoleDataScope(java.util.Map<java.lang.String, java.lang.Object>)'
    var input = readBody(body, Map, 'java.util.Map', signature)
    var roleId = input.roleId == null ? '' : input.roleId.toString()
    if (roleId.isBlank()) {
        throw new IllegalArgumentException('角色ID不能为空')
    }
    var tenantId = tenantRuntime.id()
    var role = db.table('rm_role_t').column('data_scope').where()
        .eq('tenant_id', tenantId).eq('id', roleId).eq('deleted', 0).selectOne()
    var stored = role?.dataScope || role?.data_scope || role?.datascope || ''
    var value = stored.trim().toUpperCase(Locale.ROOT)
    var configured = ['ALL', 'ORG', 'OWNER'].contains(value)
    var resource = input.resourceCode == null ? '' : input.resourceCode.toString()
    resource = resource.isBlank() ? 'DATA_SOURCE' : resource.trim().toUpperCase(Locale.ROOT)
    if (!resource.matches('[A-Z0-9_:.-]{1,160}')) {
        throw new IllegalArgumentException('资源标识不合法')
    }
    return ResponseEntity.ok(CommonResponse.success({
        resourceCode: resource, roleId: roleId, scope: configured ? value : 'OWNER', configured: configured
    }))
})
