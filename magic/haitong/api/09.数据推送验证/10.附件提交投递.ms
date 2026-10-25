{
  "id": "haitong-validation-10",
  "groupId": "haitong-validation-api",
  "name": "10.附件提交投递",
  "path": "/attachments/submit",
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

// 1. fileId 来自 07.人员附件上传响应；用 11 确认 READY 后即可提交。
// 本机预填的是已上传的人员 PNG。要测试新附件，只替换下面这个 fileId。
var fileId = 'fec585ae-a0ff-4c1a-9832-d9a5d7954f8a'
var method = 'POST'
var url = 'http://127.0.0.1:18083/api/v1/file-uploads/' + fileId + '/submit'
var requestId = 'validation-submit-' + fileId

// 2. Postman → Headers：同一附件重复提交沿用此幂等键和相同正文。
var headers = {
    'Content-Type': 'application/json',
    'X-Api-Key': apiKey,
    'Idempotency-Key': requestId
}

// 3. Postman → Body → raw → JSON。
var payload = {
    // businessKey 是附件所关联的业务行主键，对应 03.人员登记数据推送的第一行 record_id。
    businessKey: 'VALIDATION-POSTMAN-PERSON-001',
    // metadata 是上游补充信息；实际 source/table/field 绑定已在上传时确定，不能靠这里改变归属。
    metadata: {recordId: 'VALIDATION-POSTMAN-PERSON-001'}
}
var requestBytes = json.bytes(payload)
var bodyForDisplay = payload
var headersForDisplay = {'Content-Type': 'application/json', 'X-Api-Key': '已从私有配置读取', 'Idempotency-Key': requestId}
// 返回受理后，在 11 查询同一 fileId；deliveryStatus=SUCCEEDED 才表示附件已送达海通 YAML 配置的 FTP。

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
