{
  "id": "haitong-validation-push-event",
  "groupId": "haitong-validation-api",
  "name": "05.事件登记数据推送",
  "path": "/push/event",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "requestBody": "{}",
  "description": "Postman式真实 HTTP 示例；URL/Headers/Body/参数来源均在格式化脚本内。登录18083海通编辑器后直接运行，无须填写接口信息页；仅验证示例启用编辑器登录校验。"
}
================================
import com.haitong.core.HttpCalls
import java.net.URI
import java.util.UUID
import magicHttp

// 与 Postman 一样，下面只准备 URL、Headers、Body，再发起一次真实 HTTP 请求。
// 这里不调用推送业务函数，也不在验证接口内查库、同步登记或轮询。
var json = magicHttp.bean('json')
// X-Api-Key 是登记数据源专用业务密钥，不是编辑器密码，也不是同步密钥。
// 获取位置：元数据平台 → 数据源登记 → 结构化数据与附件推送验证 → 第5步签发密钥。
// 本机调用方为 push-validation-batch-ingestion，已获该测试源授权；私有 YAML 的 haitong.validation.api-key 只保存在本机，不能提交 Git。
var apiKey = magicHttp.bean('secrets').resolve(magicHttp.bean('environment').getProperty('haitong.validation.api-key', ''))

import java.time.ZoneId
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// 1. Postman：选择 POST，输入下面的完整地址；18083 是本机独立海通端口。
var method = 'POST'
var url = 'http://127.0.0.1:18083/api/v1/data-push'
// 每次“运行”创建新批次。模拟网络重试时，把这个值改为同一个固定字符串，且正文保持相同。
var batchId = 'validation-event-' + UUID.randomUUID().toString()
var now = LocalDateTime.now(ZoneId.of('Asia/Shanghai')).format(DateTimeFormatter.ofPattern('yyyy-MM-dd HH:mm:ss'))

// 2. Postman → Headers：逐项添加以下三个请求头。
var headers = {
    'Content-Type': 'application/json', // Body 类型为 raw → JSON。
    'X-Api-Key': apiKey,                // 仅允许当前测试数据源；不要使用其他数据源的密钥。
    'Idempotency-Key': batchId          // 幂等键：同一请求重试用原值，新批次用新值。
}

// 3. Postman → Body → raw → JSON：按下面对象填写。
var payload = {
    // 数据源：结构化数据与附件推送验证；在登记列表/详情取得 ID，保持字符串避免大整数精度丢失。
    datasourceId: '2113960660861288448',
    // 表：事件登记；来自该数据源登记模板的英文表名，不能填写中文资源名或 ODS 目标表名。
    tableName: 'validation_push_event',
    // FTP 表示使用海通自己 YAML 的 relay.ftp 目标；不向元数据平台查询 FTP 地址。
    deliveryType: 'FTP',
    batchId: batchId, // 上游批次标识，可在消息、文件与对账记录中关联同批数据。
    // records 是行数组；下面字段名必须与登记模板字段英文名一致。
    records: [
        {
            record_id: 'VALIDATION-POSTMAN-EVENT-001', // 该表业务主键；与附件提交的 businessKey 对应。
            code: '01',            // 示例业务编码；字典表中作为字典项编码。
            name: '事件登记示例一', // 示例名称，不包含真实个人信息。
            status_code: '01',     // 状态字典编码：01。
            amount: 10,            // 示例数值字段，JSON 数字不要加引号。
            updated_at: now,       // 增量更新时间：北京时间，避免服务的 UTC 时区使数据落在旧水位之前。
            business_time: now,
            // 关联附件：来自本目录 09.事件附件上传 的响应 fileId；重新上传后，把新 fileId 复制到这里。
            // 对应登记字段 attachment_file_id；当前预填值已经上传并成功投递到 FTP。
            attachment_file_id: '79e83508-d18f-465a-8829-32e860dc3bfd'
        },
        {
            record_id: 'VALIDATION-POSTMAN-EVENT-002',
            code: '02',
            name: '事件登记示例二',
            status_code: '02',
            amount: 20,
            updated_at: now,
            business_time: now
        }
    ]
}
var requestBytes = json.bytes(payload)
var bodyForDisplay = payload
// 响应中隐藏密钥；在 Postman 里仍需填写真实 X-Api-Key。
var headersForDisplay = {'Content-Type': 'application/json', 'X-Api-Key': '已从私有配置读取', 'Idempotency-Key': batchId}

// 4. 发出请求。HttpCalls 仅提供 HTTP 传输；30 是超时秒数，1048576 是响应体上限（字节）。
var response = HttpCalls.call(URI.create(url), method, requestBytes, headers, 30, 1048576)
// HTTP 202 仅表示受理；推送和附件的最终结果分别用 12、11 查询。
return {
    request: {
        method: method,
        url: url,
        headers: headersForDisplay,
        body: bodyForDisplay
    },
    response: {
        httpStatus: response.status(),
        body: json.object(response.body())
    }
}
