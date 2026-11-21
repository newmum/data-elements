{
  "properties": {},
  "id": "sym_scope_save_v1",
  "script": null,
  "groupId": "sym_scope_group_v1",
  "name": "02.保存角色数据范围",
  "path": "/save",
  "method": "POST",
  "parameters": [],
  "options": [
    {
      "name": "jsonBody",
      "value": "CACHED"
    }
  ],
  "headers": [],
  "paths": [],
  "description": "对当前租户的启用角色保存 ALL、ORG 或 OWNER。先校验资源编码，再在单一租户事务内写入。"
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

// POST /sym/scope/save 保存角色级数据范围。租户取自会话，客户端资源编码仅用于回显。
// 先校验启用角色和全部参数。写入条件再次限定同租户有效角色，失败时回滚。
return respond(() => {
    var signature = 'com.linewell.dataelement.model.common.CommonResponse<?> '
        + 'com.linewell.dataelement.feature.identity.api.DataScopeAuthorizationController'
        + '.saveRoleDataScope(java.util.Map<java.lang.String, java.lang.Object>)'
    var input = readBody(body, Map, 'java.util.Map', signature)
    var roleId = input.roleId == null ? '' : input.roleId.toString()
    if (roleId.isBlank()) {
        throw new IllegalArgumentException('角色ID不能为空')
    }
    var tenantId = tenantRuntime.id()
    var activeRole = () => db.table('rm_role_t').where()
        .eq('tenant_id', tenantId).eq('id', roleId).eq('deleted', 0).eq('status', 0)
    var result = db.transaction(() => {
        if (activeRole().selectOne() == null) {
            throw new IllegalArgumentException('角色不存在或不属于当前租户')
        }
        var scope = input.scope == null ? '' : input.scope.toString().trim().toUpperCase(Locale.ROOT)
        if (!['ALL', 'ORG', 'OWNER'].contains(scope)) {
            throw new IllegalArgumentException('数据范围仅支持 ALL、ORG 或 OWNER')
        }
        var resource = input.resourceCode == null ? '' : input.resourceCode.toString()
        resource = resource.isBlank() ? 'DATA_SOURCE' : resource.trim().toUpperCase(Locale.ROOT)
        if (!resource.matches('[A-Z0-9_:.-]{1,160}')) {
            throw new IllegalArgumentException('资源标识不合法')
        }
        if (activeRole().update({data_scope: scope, update_time: new Date()}) != 1) {
            throw new IllegalArgumentException('角色不存在或不属于当前租户')
        }
        return {resourceCode: resource, roleId: roleId, scope: scope, configured: true}
    })
    return ResponseEntity.ok(CommonResponse.success(result))
})
