{
  "properties": {},
  "id": "6a8a78b375224683a6afbcdac223aef6",
  "script": null,
  "groupId": "651b3afb2fba41afbb67eb9056d8af3b",
  "name": "07.文件操作",
  "createTime": 1782660730213,
  "updateTime": 1786951858953,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/file",
  "method": "POST",
  "parameters": [
    {
      "name": "bucket",
      "value": "测试",
      "description": "桶名称",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "targetBucket",
      "value": null,
      "description": "真实数据示例参数：targetBucket",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "fileNames",
      "value": "测试",
      "description": "文件名称",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "action",
      "value": "download",
      "description": "真实数据示例参数：action",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "file",
      "value": {},
      "description": "真实数据示例参数：file",
      "required": false,
      "dataType": "MultipartFile",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "tid",
      "value": "2e83a7abf33140c7a3df27d0c79e98c1",
      "description": "真实数据示例参数：tid",
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
  "responseBody": "{\n    \"code\": 500,\n    \"msg\": \"fileId不正确\",\n    \"success\": false,\n    \"data\": null\n}",
  "description": "file       - 上传的文件（multipart）；涉及 file_upload_t 等业务表；执行数据库写入并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": {
    "name": "",
    "value": "",
    "description": "",
    "required": false,
    "dataType": "Object",
    "type": null,
    "defaultValue": null,
    "validateType": "",
    "error": "",
    "expression": "",
    "children": [
      {
        "name": "code",
        "value": "500",
        "description": "",
        "required": false,
        "dataType": "Integer",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "msg",
        "value": "fileId不正确",
        "description": "",
        "required": false,
        "dataType": "String",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "success",
        "value": "false",
        "description": "",
        "required": false,
        "dataType": "Boolean",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "data",
        "value": "null",
        "description": "",
        "required": false,
        "dataType": "Object",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      }
    ]
  }
}
================================
// 接口：07.文件操作
// 路由：POST /data/file
// 业务说明：file       - 上传的文件（multipart）；涉及 file_upload_t 等业务表；执行数据库写入并返回处理结果。
import tenantRuntime
import com.linewell.dataelement.utils.MinioUtils
import cn.hutool.core.util.IdUtil
import log
import "@post:/sym/user/me" as me
import '@/common/configData' as configData
import systemConfig
import response;
import cn.hutool.core.util.StrUtil;
import cn.dev33.satoken.stp.StpUtil

/**
 * 文件上传接口
 * POST /api/upload/file  (multipart/form-data)
 *
 * 请求参数：
 *   file       - 上传的文件（multipart）
 *   bucket     - 存储桶名称
 *   created_by - 上传人ID
 *   file_id    - 外部系统文件ID（可选）
 */  

// ========== 获取请求参数 ==========
var tenantId = tenantRuntime.id()
log.info("bucket:{}",bucket)
if(bucket == null || bucket == ""){
  bucket = bucket || "attachment"
}

// ========== 上传到 Minio ==========
MinioUtils.getMinioClient(configData().minio_url, configData().minio_user, systemConfig.legacyMinioPassword());

if(action == 'upload'){
    createdBy = ""
    try{
        createdBy = StpUtil.getLoginId() + ""
    }catch(e) {
        createdBy = null
    }

    // ========== 获取文件元信息 ==========
    var fileName   = file.getOriginalFilename()
    var fileSize   = file.getSize()
    var fileSuffix = ''
    if (fileName != null && fileName.contains('.')) {
        fileSuffix = fileName.substring(fileName.lastIndexOf('.') + 1)
    }


    // 上传单个文件
    var tid = IdUtil.getSnowflakeNextIdStr()
    var now = new Date()
    
    MinioUtils.uploadFile(bucket, tid + "." + fileSuffix, file)

    var filePath = bucket + "/" + tid + "." + fileSuffix;

    log.info('文件上传Minio成功, bucket: {}, fileName: {}, filePath: {}', bucket, fileName, filePath)

    // ========== 保存上传记录 file_upload_t ==========
    db.table('file_upload_t').primary('tid').insert({
        tid:          tid,
        file_id:      fileId,
        file_path:    filePath,
        file_name:    fileName,
        file_size:    fileSize,
        file_suffix:  fileSuffix,
        tenant_id:    tenantId,
        created_by:   createdBy,
        created_time: now,
        updated_by:   createdBy,
        updated_time: now,
        is_del:       0
    })
    log.info('保存文件上传记录成功, tid: {}, fileName: {}, fileSize: {}', tid, fileName, fileSize)

    // 返回当前业务分支的标准结果。
    return {
        tid:       tid,
        name:      fileName,
        url:       `/data/download?fileId=${tid}`,
        preView:   `/${filePath}`
    }
}
else if(action == 'delete'){
    // 从 MySQL 业务表读取当前有效数据。
    var fileInfo =  db.table("file_upload_t").where().eq("tid", tid).eq("tenant_id", tenantId).selectOne()
    var relaName = StrUtil.subAfter(fileInfo.filePath, "/", false);
    // 删除单个文件
    return MinioUtils.deleteFile(bucket, relaName);
}
else if(action == 'download'){
    // 从 MySQL 业务表读取当前有效数据。
    var fileInfo =  db.table("file_upload_t").where().eq("tid", tid).eq("tenant_id", tenantId).selectOne()
    if(fileInfo == null){
        // 参数或业务状态不满足要求时立即终止请求。
        exit 500, "fileId不正确";
    }
    // 下载单个文件
    relaName = StrUtil.subAfter(fileInfo.filePath, "/", false);
    bucket =  StrUtil.subBefore(fileInfo.filePath, "/", false);
    response.setHeader("Content-disposition", "attachment; filename="+ fileInfo.fileName);
    MinioUtils.downloadFile(bucket, relaName, response.getOutputStream());
}
// else if(action == 'syncfile'){
//     // 同步多个文件
//     return MinioUtils.syncSpecificFiles(bucket, targetBucket, Arrays.asList(fileNames.split(",")));
// }else{
//     // 文件是否存在
//     return MinioUtils.isObjectExists(bucket, fileNames);
// }
