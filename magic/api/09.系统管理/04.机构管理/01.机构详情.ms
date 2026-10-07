{
  "properties": {},
  "id": "1f91899a67444ecb8cbdaae5485ef17f",
  "script": null,
  "groupId": "c1736799c6af43359a75e6764601931f",
  "name": "01.机构详情",
  "createTime": 1782660716455,
  "updateTime": 1784437771402,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/detail",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按机构标识读取当前租户的组织机构资料，返回查看和编辑页面所需字段。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.机构详情
// 路由：POST /sym/org/detail
// 业务说明：按机构标识读取当前租户的组织机构资料，返回查看和编辑页面所需字段。
// 组织机构详情接口
// 路径: /api/org/detail
// 方法: POST
// 数据源: sso (192.168.175.86:3306)
// 参数: id (必填,机构ID)

// 获取入参
var id = body?.id || '';

// 参数校验
if (id == null || id == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, '机构ID不能为空';
}

try {
    // 从 MySQL 业务表读取当前有效数据。
    var record = db.table("rm_org_t")
        .where().eq("id", id).eq("deleted", 0).selectOne();

    if (record == null) {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 404, '机构不存在: ' + id;
    }

    // 返回当前业务分支的标准结果。
    return record;
} catch(e) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '查询失败: ' + e.message;
}
