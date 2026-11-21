{
  "id": "dws_push_registry_rotate_v1",
  "groupId": "dws_push_registry_group_v1",
  "name": "04.轮换业务推送密钥",
  "path": "/rotate",
  "method": "POST",
  "description": "在当前登记会话轮换数据源专属密钥并同步海通；明文只在本次生成时返回。",
  "parameters": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "requestBody": "{\"datasourceId\":\"当前租户数据源标识\"}",
  "headers": [],
  "paths": []
}
================================
import '@/pushregistry/credential' as credential
import cn.hutool.extra.spring.SpringUtil
import com.linewell.dataelement.model.common.BizException
import java.util.Map
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.server.ResponseStatusException
import tenantRuntime

// POST /dws/push/rotate 轮换当前登录租户的数据源密钥。/pushregistry/credential 共用角色校验、摘要持久化和节点同步。
var errors = SpringUtil.getBean('globalExceptionHandler')
var datasourceId = body instanceof Map && body.datasourceId != null ? (body.datasourceId + '').trim() : ''
if (!datasourceId.matches('[A-Za-z0-9_.-]{1,255}')) {
    return ResponseEntity.ok(errors.handleException(
        new ResponseStatusException(HttpStatus.BAD_REQUEST, 'datasourceId 格式不正确')))
}
try {
    var data = credential(tenantRuntime.id(), datasourceId, true)
    return ResponseEntity.ok().header('Cache-Control', 'no-store').body({code: 0, message: 'success', data: data})
} catch (error) {
    var cause = error
    while (!(cause instanceof BizException) && cause.getCause() != null && cause.getCause() != cause) {
        cause = cause.getCause()
    }
    return ResponseEntity.ok(cause instanceof BizException ? errors.handleBizException(cause) : errors.handleException(cause))
}
