{
  "properties": {},
  "id": "347c0f505eed4457ac03b77f07eb6896",
  "script": null,
  "groupId": "e79210b931bf5e1e9070de78883499c9",
  "name": "02.业务附件下载",
  "createTime": 1782660730226,
  "updateTime": 1786951859077,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/download",
  "method": "GET",
  "parameters": [
    {
      "name": "fileId",
      "value": "048cd1d7f33e456dbc4051fe95842eed",
      "description": "真实数据示例参数：fileId",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按当前租户和附件标识读取对象存储文件，返回原始二进制和附件响应头。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime
import com.linewell.dataelement.utils.MinioUtils
import cn.hutool.core.util.StrUtil
import '@/common/configData' as configData
import systemConfig
import response;

// GET /dst/file/download 按当前租户和附件标识读取对象存储文件，返回原始二进制和附件响应头。

var tenantId = tenantRuntime.id()

// 下载单个文件
var fileInfo = db.table("file_upload_t").where().eq("tid", fileId).eq("tenant_id", tenantId).eq("is_del", 0).selectOne()
//return fileInfo
if(fileInfo == null){
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, "fileId不正确";
}else{
    fileName = fileInfo.fileName
    bucket =  StrUtil.subBefore(fileInfo.filePath, "/", false)
    relaName = StrUtil.subAfter(fileInfo.filePath, "/", false);
    MinioUtils.getMinioClient(configData().minio_url, configData().minio_user, systemConfig.legacyMinioPassword());
    response.setHeader("Content-Type", "application/octet-stream");
    response.setHeader("Content-disposition", "attachment; filename="+fileName);
    MinioUtils.downloadFile(bucket, relaName, response.getOutputStream());
    // 二进制响应到此结束，禁止再追加通用 JSON 成功信封。
    return response.end();
}
