{
  "id": "dws_push_registry_validate_v1",
  "groupId": "dws_push_registry_group_v1",
  "name": "03.校验海通推送批次",
  "path": "/validate",
  "method": "POST",
  "description": "使用服务间访问策略和登记绑定校验表推送批次，返回当前规范版本与投递目的。",
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

// POST /dws/push/validate 验证批次并返回登记的投递计划。服务间密钥先由资源访问策略校验。
// 公共校验读取调用方绑定和已发布字段，本入口按请求通道解析 Kafka 或验证 ODS 文件存储。
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
    var channel = required(body.channel, 'channel')
    var checked = verifyBatch(caller, body.batch, channel)
    var result = checked.contract
    if (channel == 'table-to-ftp') {
        // 传输适配器还会读取当前租户连接属性，必须在已确认的行业库上下文执行。
        identityRuntime.inTenant(checked.tenantId, () => SpringUtil.getBean('dataPushFjghccDeliveryService').requireTarget(checked.tenantId))
        result.targetType = 'PLATFORM_FTP'
        result.destination = 'ODS/FJGHCC'
    } else {
        if (checked.destination == '') { throw new IllegalStateException('登记数据源尚未配置 Kafka Topic') }
        result.targetType = 'KAFKA'
        result.destination = checked.destination
    }
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
