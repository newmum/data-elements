{
  "properties": {},
  "id": "a3413c9232f240c2b0cc98b99b9f2695",
  "script": null,
  "groupId": "5716c78e9d9641528c2dfbd78b708e3b",
  "name": "02.文件预览",
  "createTime": 1782660716390,
  "updateTime": 1784437771378,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/preview",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "id - 文件ID（query 参数）。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.文件预览
// 路由：GET /sym/file/preview
// 业务说明：id - 文件ID（query 参数）。
import tenantAccounts
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.codec.Base64;

// 预览/下载文件
// 路径: /sym/file/preview
// 方法: GET
// 参数:
//   id - 文件ID（query 参数）
// 说明:
//   根据文件ID从 sym_file_t 读取 content (BLOB)，编码为 base64 字符串返回
//   前端拿到 base64 后解码为 Blob 再显示，彻底避开 magic-api 二进制输出问题


try {
  tenantAccounts.session();
} catch (e) {
  // 参数或业务状态不满足要求时立即终止请求。
  exit -1, "账号未登入或登入已过期";
}

// ==================== 获取参数 ====================
if (id == null || id == "") {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, "文件ID不能为空";
}

// ==================== 查询文件记录 ====================
var fileRecord = db.lower().table("sym_file_t")
    .where()
    .eq("id", id)
    .eq("deleted", 0)
    .selectOne();

if (fileRecord == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 404, "文件不存在或已被删除";
}

if (fileRecord.content == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 404, "文件内容为空";
}

// ==================== 根据文件名后缀判定 Content-Type ====================
var fileName = fileRecord.file_name || "file";
var contentType = "application/octet-stream";

var lowerName = fileName.toLowerCase();
if (lowerName.endsWith(".png")) {
    contentType = "image/png";
} else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
    contentType = "image/jpeg";
} else if (lowerName.endsWith(".gif")) {
    contentType = "image/gif";
} else if (lowerName.endsWith(".bmp")) {
    contentType = "image/bmp";
} else if (lowerName.endsWith(".webp")) {
    contentType = "image/webp";
} else if (lowerName.endsWith(".svg")) {
    contentType = "image/svg+xml";
} else if (lowerName.endsWith(".pdf")) {
    contentType = "application/pdf";
}

// ==================== 将 BLOB 编码为 base64 返回 ====================
// 前端解码 base64 → Blob → ObjectURL 显示
var base64Content = Base64.encode(fileRecord.content);

// 返回当前业务分支的标准结果。
return {
    content: base64Content,
    contentType: contentType,
    fileName: fileName
};
