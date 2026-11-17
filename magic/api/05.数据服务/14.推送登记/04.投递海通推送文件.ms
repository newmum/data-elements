{
  "id": "dws_push_registry_deliver_v1",
  "groupId": "dws_push_registry_group_v1",
  "name": "04.投递海通推送文件",
  "path": "/deliver",
  "method": "POST",
  "description": "核验海通文件投递批次，向当前数据源绑定的 ODS/FJGHCC 文件存储提交幂等投递。",
  "parameters": [],
  "options": [{"name": "access", "value": "HAITONG_RELAY"}],
  "headers": [{"name": "X-Relay-Client-Id", "dataType": "String", "required": false}],
  "paths": []
}
================================
import '@/pushregistry/verifybatch' as verifyBatch
import cn.hutool.extra.spring.SpringUtil
import java.util.Map
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.server.ResponseStatusException
import identityRuntime
import request

// POST /dws/push/deliver 在已验证服务间密钥的前提下投递文件。脚本确认处理租户、表与字段后调用文件传输适配。
var errors = SpringUtil.getBean('globalExceptionHandler')
var required = (value, name) => {
    var text = value == null ? '' : (value + '').trim()
    if (!text.matches('[A-Za-z0-9_.-]{1,255}')) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + ' 格式不正确')
    }
    return text
}
try {
    if (!(body instanceof Map) || !(body.batch instanceof Map)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 'batch 必须是对象')
    }
    required(body.batch.datasourceId, 'datasourceId')
    var caller = required(request.get().getHeader('X-Relay-Client-Id'), 'X-Relay-Client-Id')
    var deliveryId = required(body.deliveryId, 'deliveryId')
    var checked = verifyBatch(caller, body.batch, 'table-to-ftp')
    if (deliveryId.length() > 128) { throw new IllegalArgumentException('deliveryId 格式不正确') }
    // 文件传输及其连接属性查找共用已验证的租户上下文。
    var result = identityRuntime.inTenant(checked.tenantId, () => SpringUtil.getBean('dataPushFjghccDeliveryService')
        .deliver(checked.tenantId, checked.contract.datasourceId, checked.contract.tableName,
            deliveryId, checked.records, checked.contract.schemaVersion))
    return ResponseEntity.ok({code: 0, message: 'success', data: result})
} catch (error) {
    var cause = error
    while (cause.getCause() != null && cause.getCause() != cause) { cause = cause.getCause() }
    if (cause instanceof ResponseStatusException) {
        error = cause
    } else if (cause instanceof IllegalArgumentException) {
        error = new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, cause.getMessage())
    } else if (cause instanceof IllegalStateException) {
        error = new ResponseStatusException(HttpStatus.CONFLICT, cause.getMessage())
    }
    return ResponseEntity.ok(errors.handleException(error))
}
