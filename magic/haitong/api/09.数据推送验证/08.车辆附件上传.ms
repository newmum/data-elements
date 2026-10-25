{
  "id": "haitong-validation-upload-vehicle",
  "groupId": "haitong-validation-api",
  "name": "08.车辆附件上传",
  "path": "/attachments/vehicle",
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

import java.util.Base64

// 1. 单次附件上传：在 Postman 选择 POST，URL 的 Query Params 如下逐项填写。
// datasourceId：结构化数据与附件推送验证的登记 ID；tableName：车辆表英文名。
// fieldName：登记模板标注“关联附件”的字段英文名 attachment_file_id。
// fileName：上游文件原始名称；扩展名需符合该字段登记的允许格式。
var method = 'POST'
var url = 'http://127.0.0.1:18083/api/v1/file-uploads/single'
    + '?datasourceId=2113960660861288448'
    + '&tableName=validation_push_vehicle'
    + '&fieldName=attachment_file_id'
    + '&fileName=validation-vehicle.png'
var requestId = 'validation-vehicle-file-' + UUID.randomUUID().toString()

// 2. Postman → Headers：使用源专属密钥；二进制文件上传的类型不是 application/json。
var headers = {
    'Content-Type': 'application/octet-stream',
    'X-Api-Key': apiKey,
    'Idempotency-Key': requestId
}

// 3. Postman → Body → binary → 选择 PNG 文件。
// 为了在编辑器直接“运行”，把一张 1×1 PNG 嵌在下面；解码后发送的是原始文件字节。
// 实际接口不接收 base64 JSON，也不要求上游把文件转为 base64。
var pngBase64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a3ioAAAAASUVORK5CYII='
var requestBytes = Base64.getDecoder().decode(pngBase64)
var bodyForDisplay = 'binary：内嵌 1×1 PNG，' + requestBytes.length + ' 字节'
var headersForDisplay = {'Content-Type': 'application/octet-stream', 'X-Api-Key': '已从私有配置读取', 'Idempotency-Key': requestId}
// 下一步：复制响应 fileId → 11 查询，状态 READY 后 → 10 提交 → 11 确认 deliveryStatus=SUCCEEDED。
// 人员/车辆/事件的 fileId 分别回填 03/04/05 的 attachment_file_id；10/11 的示例默认演示人员附件。

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
