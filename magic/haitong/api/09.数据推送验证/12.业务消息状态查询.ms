{
  "id": "haitong-validation-12",
  "groupId": "haitong-validation-api",
  "name": "12.业务消息状态查询",
  "path": "/messages/status",
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

// 1. messageId 来自 01～06 数据推送 的响应；查询时必须使用原上传/推送客户端的密钥。
// 已预填本机成功联调的 ID，可直接运行；查询自己的新请求时只替换下面这一行。
var messageId = 'f3d0ba78-3dea-4919-9199-9f4e784b38a3'
var method = 'GET'
var url = 'http://127.0.0.1:18083/api/v1/messages/' + messageId

// 2. Postman → Headers：GET 状态查询只需 X-Api-Key，无需 Idempotency-Key。
var headers = {'X-Api-Key': apiKey}
// 3. Postman → Body → none；GET 不发送 JSON 正文。
var requestBytes = ''.getBytes('UTF-8')
var bodyForDisplay = null
var headersForDisplay = {'X-Api-Key': '已从私有配置读取'}
// 最终成功状态：SUCCEEDED。一次运行只查询一次，便于观察；不要把轮询业务藏在验证脚本中。

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
