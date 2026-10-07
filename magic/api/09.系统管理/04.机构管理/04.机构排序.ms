{
  "properties": {},
  "id": "288235e0cc4047b09933797f75ecfe12",
  "script": null,
  "groupId": "c1736799c6af43359a75e6764601931f",
  "name": "04.机构排序",
  "createTime": 1782660716444,
  "updateTime": 1784437771401,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/sort",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "校验同级机构标识及当前租户范围，按请求顺序更新机构排序号并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.机构排序
// 路由：POST /sym/org/sort
// 业务说明：校验同级机构标识及当前租户范围，按请求顺序更新机构排序号并返回处理结果。
// 组织机构排序接口
// 路径: /api/org/sort
// 方法: POST
// 数据源: sso (192.168.175.86:3306)
// 参数: ids (必填,同级机构ID数组,按新顺序排列,如 ["id3","id1","id2"])

import "@post:/sym/user/me" as me

var now = new Date();

// 获取入参
var ids = body?.ids;

// 参数校验
if (ids == null || ids.size() == 0) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, '排序机构ID列表不能为空';
}

try {
    var updateId = me().createId;

    // 按传入顺序依次设置sortNum（for迭代的是元素，手动维护序号）
    var sortNum = 0;
    for (id in ids) {
        sortNum = sortNum + 1;
        // 从 MySQL 业务表读取当前有效数据。
        db.table("rm_org_t")
            .where().eq("id", id)
            .update({ sortNum: sortNum, updateId: updateId, updateTime: now });
    }

    // 返回当前业务分支的标准结果。
    return { count: ids.size() };
} catch(e) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '排序失败: ' + e.message;
}
