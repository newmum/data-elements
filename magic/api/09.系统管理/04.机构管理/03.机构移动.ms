{
  "properties": {},
  "id": "c5d287b565064b539aef7bf0587fa07a",
  "script": null,
  "groupId": "c1736799c6af43359a75e6764601931f",
  "name": "03.机构移动",
  "createTime": 1782660716418,
  "updateTime": 1784437771386,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/move",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "组织机构移动（修改父级部门）接口；使用请求参数 id、newParentId；涉及 rm_org_t 等业务表；执行数据库写入并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.机构移动
// 路由：POST /sym/org/move
// 业务说明：组织机构移动（修改父级部门）接口；使用请求参数 id、newParentId；涉及 rm_org_t 等业务表；执行数据库写入并返回处理结果。
// 组织机构移动（修改父级部门）接口
// 路径: /api/org/move
// 方法: POST
// 数据源: sso (192.168.175.86:3306)
// 参数: id (必填,要移动的机构ID), newParentId (必填,新父级机构ID,ROOT表示移到顶层)
// 说明: 移动节点到新父级下，同步更新 orgPath/serialNumber，级联更新所有子孙的 orgPath

import "@post:/sym/user/me" as me

var now = new Date();

// 获取入参
var id          = body?.id || '';
var newParentId = body?.newParentId || '';

// 参数校验
if (!id) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, '机构ID不能为空';
}
if (!newParentId) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, '新父级机构ID不能为空';
}

try {
    var updateId = me().createId;

    // 1. 校验要移动的机构是否存在
    var org = db.table("rm_org_t")
        .where().eq("id", id).eq("deleted", 0).selectOne();

    if (org == null) {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 404, '机构不存在: ' + id;
    }

    // 2. 不能移到自身
    if (id == newParentId) {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 400, '不能将机构移到自身下';
    }

    // 3. 校验新父级是否存在（非ROOT时）
    var newParent = null;
    if (newParentId != 'ROOT') {
        // 从 MySQL 业务表读取当前有效数据。
        newParent = db.table("rm_org_t")
            .where().eq("id", newParentId).eq("deleted", 0).selectOne();

        if (newParent == null) {
            // 参数或业务状态不满足要求时立即终止请求。
            exit 404, '新父级机构不存在: ' + newParentId;
        }

        // 4. 防止循环：新父级不能是当前节点的子孙
        var checkId = newParentId;
        while (checkId && checkId != 'ROOT') {
            if (checkId == id) {
                // 参数或业务状态不满足要求时立即终止请求。
                exit 400, '不能将机构移到其子孙节点下';
            }
            // 从 MySQL 业务表读取当前有效数据。
            var checkNode = db.table("rm_org_t")
                .where().eq("id", checkId).eq("deleted", 0).selectOne();
            if (checkNode == null) {
                break;
            }
            checkId = checkNode.parentId;
        }
    }

    // 5. 计算新 orgPath 和 serialNumber
    var newOrgPath = '';
    var newSerialNumber = '';

    if (newParentId == 'ROOT') {
        newOrgPath = org.name;
        newSerialNumber = org.serialNumber || '';
    } else {
        var parentPath = newParent.orgPath || '';
        newOrgPath = parentPath ? (parentPath + '/' + org.name) : org.name;
        // 从 MySQL 业务表读取当前有效数据。
        var siblings = db.table("rm_org_t")
            .where().eq("parentId", newParentId).eq("deleted", 0).select();
        var nextNum = siblings.size() + 1;
        var nextNumStr = nextNum < 10 ? ('000' + nextNum) : (nextNum < 100 ? ('00' + nextNum) : (nextNum < 1000 ? ('0' + nextNum) : ('' + nextNum)));
        var parentSerial = newParent.serialNumber || '';
        newSerialNumber = parentSerial + nextNumStr;
    }

    // 6. 更新自身：parentId、orgPath、serialNumber
    var oldOrgPath = org.orgPath || '';
    // 从 MySQL 业务表读取当前有效数据。
    db.table("rm_org_t")
        .where().eq("id", id)
        .update({
            parentId: newParentId,
            orgPath: newOrgPath,
            serialNumber: newSerialNumber,
            updateId: updateId,
            updateTime: now
        });

    // 7. 级联更新所有子孙的 orgPath（替换旧前缀为新前缀）
    if (oldOrgPath) {
        var allDescendants = [];
        var queue = [id];

        while (queue.size() > 0) {
            var currentId = queue.remove(0);
            // 从 MySQL 业务表读取当前有效数据。
            var children = db.table("rm_org_t")
                .where().eq("parentId", currentId).eq("deleted", 0)
                .select();

            for (child in children) {
                allDescendants.add(child);
                queue.add(child.id);
            }
        }

        for (desc in allDescendants) {
            var descPath = desc.orgPath || '';
            if (descPath.startsWith(oldOrgPath + '/')) {
                var newDescPath = newOrgPath + descPath.substring(oldOrgPath.length());
                // 从 MySQL 业务表读取当前有效数据。
                db.table("rm_org_t")
                    .where().eq("id", desc.id)
                    .update({ orgPath: newDescPath, updateId: updateId, updateTime: now });
            }
        }
    }

    // 返回当前业务分支的标准结果。
    return { id: id, newParentId: newParentId, orgPath: newOrgPath, serialNumber: newSerialNumber };
} catch(e) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '移动失败: ' + e.message;
}
