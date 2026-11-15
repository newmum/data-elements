{
  "properties": {},
  "id": "304c8bfbc2d445ba92686159208b19ae",
  "script": null,
  "groupId": "5716c78e9d9641528c2dfbd78b708e3b",
  "name": "03.文件删除",
  "createTime": 1782660716378,
  "updateTime": 1784437771376,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/delete",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按目标标识和当前状态执行文件删除，返回处理结果；使用请求参数 id。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.文件删除
// 路由：POST /sym/file/delete
// 业务说明：按目标标识和当前状态执行文件删除，返回处理结果；使用请求参数 id。
import tenantAccounts
import cn.dev33.satoken.stp.StpUtil;

// 删除文件
// 路径: /sym/file/delete
// 方法: POST
// 参数:
//   id - 文件ID（必填）


try {
  tenantAccounts.session();
} catch (e) {
  // 参数或业务状态不满足要求时立即终止请求。
  exit -1, "账号未登入或登入已过期";
}

// ==================== 获取参数 ====================
var id = body?.id;
if (id == null || id == "") {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, "文件ID不能为空";
}

// ==================== 校验文件是否存在 ====================
var fileRecord = db.lower().table("sym_file_t")
    .where()
    .eq("id", id)
    .eq("deleted", 0)
    .selectOne();

if (fileRecord == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 404, "文件不存在或已被删除";
}

// ==================== 软删除 ====================
var currentUserId = StpUtil.getLoginId();

db.lower().table("sym_file_t")
    .primary("id")
    .update({
        id: id,
        deleted: 1,
        update_id: currentUserId,
        update_time: now
    });

// 返回当前业务分支的标准结果。
return { id: id };
