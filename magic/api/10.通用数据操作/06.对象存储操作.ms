{
  "properties": {},
  "id": "5a3dbc0e6bbc4e9cbafb113dacfc79f6",
  "script": null,
  "groupId": "651b3afb2fba41afbb67eb9056d8af3b",
  "name": "06.对象存储操作",
  "createTime": 1782660730201,
  "updateTime": 1784437771501,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/oss",
  "method": "POST",
  "parameters": [
    {
      "name": "bucket",
      "value": "catalog.apply",
      "description": "真实数据示例参数：bucket",
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
      "value": "catalog",
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
      "value": "04bf40acd3704df2ac06a90cb371ae18",
      "description": "真实数据示例参数：fileNames",
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
      "value": "upload",
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
    }
  ],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": null\n}",
  "description": "按请求动作调用对象存储服务执行上传、下载或文件维护，并返回文件或操作结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：06.对象存储操作
// 路由：POST /data/oss
// 业务说明：按请求动作调用对象存储服务执行上传、下载或文件维护，并返回文件或操作结果。
// 核心业务逻辑：按接口约定编排当前请求并返回业务处理结果。
import com.linewell.dataelement.utils.MinioUtils;
import java.util.Arrays;
import response;
import log;
import '@/common/configData' as configData
import systemConfig
 
// var datasource = db.table("asset_datasource_t")
//                .columns("db_ip,db_port","db_user","db_password")
//                .where()
//                .eq("tid", "1878976890209591296")
//                .selectOne();
// log.info("{}", datasource);
MinioUtils.getMinioClient(configData().minio_url, configData().minio_user, systemConfig.legacyMinioPassword());
if(action == 'upload'){
    // 上传单个文件
    return MinioUtils.uploadFile(bucket, fileNames, file);
}else if(action == 'download'){
    // 下载单个文件
    response.setHeader("Content-disposition", "attachment; filename="+fileNames);
    MinioUtils.downloadFile(bucket, fileNames, response.getOutputStream());
}else if(action == 'delete'){
    // 删除单个文件
    return MinioUtils.deleteFile(bucket, fileNames);
}else if(action == 'syncfile'){
    // 同步多个文件
    return MinioUtils.syncSpecificFiles(bucket, targetBucket, Arrays.asList(fileNames.split(",")));
}else{
    // 文件是否存在
    return MinioUtils.isObjectExists(bucket, fileNames);
}
