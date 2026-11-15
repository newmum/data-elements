{
  "properties": {},
  "id": "sym_scope_current_v1",
  "script": null,
  "groupId": "sym_scope_group_v1",
  "name": "03.查询当前数据范围",
  "path": "/current",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "查询当前登录人的有效范围，复用数据源和登记接口使用的 dataScope 权限计算。"
}
================================
import '@/common/nifiLifecycleBody' as readBody
import '@/common/nifiPipelineReadHttp' as respond
import com.linewell.dataelement.model.common.CommonResponse
import java.util.Map
import org.springframework.http.ResponseEntity
import dataScope

// POST /sym/scope/current 查询本人有效范围。dataScope 与数据源和登记接口共用。
// 有效角色、组织及租户从服务端会话解析，资源编码沿用请求中的原值。
return respond(() => {
    var input = readBody(body, Map, 'java.util.Map', null)
    var resource = input?.resourceCode == null ? '' : input.resourceCode.toString()
    return ResponseEntity.ok(CommonResponse.success(dataScope.resource(resource)))
})
