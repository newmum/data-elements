{
  "properties": {},
  "id": "347c0f505eed4457ac03b77f07eb6896",
  "script": null,
  "groupId": "651b3afb2fba41afbb67eb9056d8af3b",
  "name": "08.文件下载接口",
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
  "description": "fileId    - 外部系统文件ID（可选）；涉及 file_upload_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：08.文件下载接口
// 路由：GET /data/download
// 业务说明：fileId    - 外部系统文件ID（可选）；涉及 file_upload_t 等业务表。
import tenantRuntime
import com.linewell.dataelement.utils.MinioUtils
import cn.hutool.core.util.IdUtil
import cn.hutool.core.util.StrUtil
import log
import "@post:/sym/user/me" as me
import '@/common/configData' as configData
import response;


/**
 * 文件上传接口
 * POST /api/upload/file  (multipart/form-data)
 *
 * 请求参数：
 *   fileId    - 外部系统文件ID（可选）
 */  

// ========== 获取请求参数 ==========
var tenantId = tenantRuntime.id()

// 下载单个文件
var fileInfo = db.table("file_upload_t").where().eq("tid", fileId).eq("tenant_id", tenantId).selectOne()
//return fileInfo
if(fileInfo == null){
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, "fileId不正确";
}else{
    fileName = fileInfo.fileName
    bucket =  StrUtil.subBefore(fileInfo.filePath, "/", false)
    relaName = StrUtil.subAfter(fileInfo.filePath, "/", false); 
    response.setHeader("Content-disposition", "attachment; filename="+fileName);
    MinioUtils.downloadFile(bucket, relaName, response.getOutputStream());
}
