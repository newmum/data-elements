{
  "properties": {},
  "id": "9b187462901a488daf65d5d098d529c0",
  "script": null,
  "groupId": "5716c78e9d9641528c2dfbd78b708e3b",
  "name": "01.文件上传",
  "createTime": 1782660716361,
  "updateTime": 1784437771374,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/upload",
  "method": "POST",
  "parameters": [
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
  "requestBody": "{\n    \"file_name\": \"测试\",\n    \"content\": \"测试\",\n    \"belongType\": \"app\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "上传文件（头像等）- base64 直传方案；使用请求参数 file_name、content、belongType；执行数据库写入并返回处理结果。",
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
        "value": "100120",
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
        "value": "user_no_login",
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
// 接口：01.文件上传
// 路由：POST /sym/file/upload
// 业务说明：上传文件（头像等）- base64 直传方案；使用请求参数 file_name、content、belongType；执行数据库写入并返回处理结果。
import tenantAccounts
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.codec.Base64;

// 上传文件（头像等）- base64 直传方案
// 路径: /sym/file/upload
// 方法: POST
// 参数:
//   file_name - 文件名 
//   content   - 文件内容 base64 编码 


try {
  tenantAccounts.session();
} catch (e) {
  // 参数或业务状态不满足要求时立即终止请求。
  exit -1, "账号未登入或登入已过期";
}

// ==================== 校验参数 ====================
var fileName = body.file_name;
if (StrUtil.isBlank(fileName)) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, "文件名为空";
}

var base64Content = body.content;
if (StrUtil.isBlank(base64Content)) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, "文件内容为空";
}

var belongType = body.belongType || "AVATAR"

// ==================== 解码 base64 → bytes ====================
var fileBytes = Base64.decode(base64Content);
var fileSize = fileBytes.length;

if (fileSize <= 0) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, "文件大小为0，请选择有效文件";
}

// ==================== 获取文件后缀 ====================
var fileSuffix = "";
if (fileName.contains(".")) {
    fileSuffix = fileName.substring(fileName.lastIndexOf(".") + 1);
}

// ==================== 当前用户 ====================
var currentUserId = StpUtil.getLoginId();
var now = new Date();

// ==================== 存入 sym_file_t ====================
var fileId = IdUtil.getSnowflakeNextIdStr();

db.lower().table("sym_file_t").insert({
    id: fileId,
    file_name: fileName,
    content: fileBytes,
    create_id: currentUserId,
    create_time: now,
    belong_type: belongType,
    deleted: 0,
    has_temporary: 0
});

// ==================== 返回结果 ====================
return {
    id: fileId,
    url: "/sym/file/preview?id=" + fileId,
    name: fileName,
};
