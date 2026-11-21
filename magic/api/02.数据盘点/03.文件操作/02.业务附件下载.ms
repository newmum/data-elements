{
  "properties" : { },
  "id" : "347c0f505eed4457ac03b77f07eb6896",
  "script" : null,
  "groupId" : "e79210b931bf5e1e9070de78883499c9",
  "name" : "02.业务附件下载",
  "createTime" : 1782660730226,
  "updateTime" : 1795211597953,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/download",
  "method" : "GET",
  "parameters" : [ {
    "name" : "fileId",
    "value" : "048cd1d7f33e456dbc4051fe95842eed",
    "description" : "真实数据示例参数：fileId",
    "required" : false,
    "dataType" : "String",
    "type" : null,
    "defaultValue" : null,
    "validateType" : null,
    "error" : null,
    "expression" : null,
    "children" : null
  } ],
  "options" : [ {
    "name" : "access",
    "value" : "TENANT_SESSION",
    "description" : null,
    "required" : false,
    "dataType" : "String",
    "type" : null,
    "defaultValue" : null,
    "validateType" : null,
    "error" : null,
    "expression" : null,
    "children" : null
  }, {
    "name" : "head",
    "value" : "true",
    "description" : null,
    "required" : false,
    "dataType" : "String",
    "type" : null,
    "defaultValue" : null,
    "validateType" : null,
    "error" : null,
    "expression" : null,
    "children" : null
  } ],
  "requestBody" : "{\n}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : null,
  "description" : "按当前租户和附件标识读取磁盘上的数据上报原件或原有对象存储附件，返回原始二进制和附件响应头。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
import tenantRuntime
import com.linewell.dataelement.utils.MinioUtils
import cn.hutool.core.util.StrUtil
import '@/common/configData' as configData
import '@/common/reportoriginal' as reportOriginal
import systemConfig
import response;
import cn.dev33.satoken.stp.StpUtil
import java.net.URLEncoder

// GET /dst/file/download 校验当前租户的附件记录，通过 /common/reportoriginal 流式读取磁盘原件。
// 其他记录沿用原存储位置，返回文件原始字节及附件响应头。

var tenantId = tenantRuntime.id()

// 下载单个文件
var fileInfo = db.table("file_upload_t").where().eq("tid", fileId).eq("tenant_id", tenantId).eq("is_del", 0).selectOne()
if (fileInfo == null) { exit 500, 'fileId不正确' }
if (fileInfo.filePath != null && fileInfo.filePath.startsWith('report-local:')) {
    StpUtil.checkLogin()
    var stream = null
    try { stream = reportOriginal('open', tenantId, fileInfo.filePath, null) }
    catch (e) { exit 404, '上传原件不可读取' }
    try {
        var filename = URLEncoder.encode(fileInfo.fileName, 'UTF-8').replace('+', '%20')
        response.setHeader('Content-Type', 'application/octet-stream')
        response.setHeader('Content-Disposition', "attachment; filename*=UTF-8''" + filename)
        stream.transferTo(response.getOutputStream())
    } finally { stream.close() }
    return response.end();
}
var bucket = StrUtil.subBefore(fileInfo.filePath, '/', false)
var objectName = StrUtil.subAfter(fileInfo.filePath, '/', false)
var configuration = configData()
MinioUtils.getMinioClient(configuration.minio_url, configuration.minio_user, systemConfig.legacyMinioPassword())
response.setHeader('Content-Type', 'application/octet-stream')
response.setHeader('Content-Disposition', 'attachment; filename=' + fileInfo.fileName)
MinioUtils.downloadFile(bucket, objectName, response.getOutputStream())
// 二进制响应到此结束，禁止再追加通用 JSON 成功信封。
return response.end()
