{
  "properties": {},
  "id": "d2ca261a63de4aa38188a0ed93c93acb",
  "script": null,
  "groupId": "c1736799c6af43359a75e6764601931f",
  "name": "05.机构删除",
  "createTime": 1782660716431,
  "updateTime": 1787278225032,
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
  "description": "组织机构删除接口（支持批量，并递归删除全部子机构）；使用请求参数 ids、id；涉及 rm_org_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.机构删除
// 路由：POST /sym/org/delete
// 业务说明：组织机构删除接口（支持批量，并递归删除全部子机构）；使用请求参数 ids、id；涉及 rm_org_t 等业务表。
// 组织机构删除接口（支持批量，并递归删除全部子机构）
import "@post:/sym/user/me" as me

var now = new Date();
var ids = body?.ids;
if (ids == null || ids.size() == 0) {
    var singleId = body?.id || '';
    if (singleId != '') {
        ids = [singleId];
    }
}
if (ids == null || ids.size() == 0) {
    exit 400, '机构ID不能为空';
}

try {
    var updateId = me().create_id;
    var rootIds = [];
    for (id in ids) {
        if (id != null && id != '' && !rootIds.contains(id)) {
            rootIds.add(id);
        }
    }
    if (rootIds.size() == 0) {
        exit 400, '机构ID不能为空';
    }
    // ROOT 仅是“全部机构”的系统根节点，不是可删除的业务机构。
    if (rootIds.contains('ROOT')) {
        exit 400, '全部机构为系统根节点，不能删除';
    }

    var existList = db.table('rm_org_t').where().in('id', rootIds).eq('deleted', 0).select();
    var existIds = [];
    for (item in existList) {
        existIds.add(item.id);
    }
    var notFound = [];
    for (id in rootIds) {
        if (!existIds.contains(id)) {
            notFound.add(id);
        }
    }
    if (notFound.size() > 0) {
        exit 404, '机构不存在或已删除: ' + notFound.join(',');
    }

    // 从待删机构向下遍历；visited 同时避免批量选择父子节点或异常循环造成重复处理。
    var deleteIds = [];
    var visited = {};
    var queue = [];
    for (id in rootIds) {
        queue.add(id);
    }
    while (queue.size() > 0) {
        var currentId = queue.remove(0);
        if (visited[currentId] == true) {
            continue;
        }
        visited[currentId] = true;
        deleteIds.add(currentId);
        var children = db.table('rm_org_t').where().eq('parentId', currentId).eq('deleted', 0).select();
        for (child in children) {
            queue.add(child.id);
        }
    }

    db.table('rm_org_t').where().in('id', deleteIds).update({
        deleted: 1,
        updateId: updateId,
        updateTime: now
    });
    return {
        ids: rootIds,
        count: deleteIds.size(),
        deleted: true,
        recursive: true
    };
} catch (e) {
    exit 500, '删除失败: ' + e.message;
}
