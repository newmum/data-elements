{
  "properties": {},
  "id": "885b78dbd4474a4cb47566030e050c76",
  "script": null,
  "groupId": "c1736799c6af43359a75e6764601931f",
  "name": "02.机构保存修改",
  "createTime": 1782660716404,
  "updateTime": 1784437771383,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/saveOrUpdate",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"name\": \"自然资源部\",\n    \"shortName\": \"测试\",\n    \"nature\": \"2\",\n    \"status\": 0,\n    \"parentId\": \"2067073568296509440\",\n    \"sortNum\": \"测试\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "校验请求资料后执行机构保存修改，返回保存或配置结果；使用请求参数 id、name、parentId、shortName、creditCode、standard、nature、status；涉及 rm_org_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.机构保存修改
// 路由：POST /sym/org/saveOrUpdate
// 业务说明：校验请求资料后执行机构保存修改，返回保存或配置结果；使用请求参数 id、name、parentId、shortName、creditCode、standard、nature、status；涉及 rm_org_t 等业务表。
// 组织机构保存/更新接口
// 路径: /api/org/saveOrUpdate
// 方法: POST
// 数据源: sso (192.168.175.86:3306)
// 参数: id(可选,有则更新无则新增), name, parentId, shortName, creditCode, standard, nature,
//       nationalCode, nationalName, regionCode, nodeType, label, leaderOrgId

import cn.hutool.core.util.IdUtil
import "@post:/sym/user/me" as me

var now = new Date();

// 获取入参
var id            = body?.id || '';
var name          = body?.name || '';
var parentId      = body?.parentId || '';
var shortName     = body?.shortName || '';
var creditCode    = body?.creditCode || '';
var standard      = body?.standard || '';
var nature        = body?.nature != null ? body.nature : '2';  // 区域:1 部门:2 虚拟:3，默认部门
var status        = body?.status != null ? body.status : 0;    // 禁用:1 启用:0
var serialNumber  = body?.serialNumber || '';
var sortNum       = body?.sortNum != null ? body.sortNum : null;
var description   = body?.description || '';
var deptType      = body?.deptType || '';
var deptCode      = body?.deptCode || '';
var nationalCode  = body?.nationalCode || '';
var nationalName  = body?.nationalName || '';
var regionCode    = body?.regionCode || '';
var nodeType      = body?.nodeType || '';
var label         = body?.label || '';
var leaderOrgId   = body?.leaderOrgId || '';
var createId      = me().createId;
var updateId      = body?.updateId || '';


try {
    // 构建字段数据（驼峰，与数据库字段一致）
    var data = {
        name: name,
        parentId: parentId,
        shortName: shortName,
        creditCode: creditCode,
        standard: standard,
        nature: nature,
        status: status,
        serialNumber: serialNumber,
        sortNum: sortNum,
        description: description,
        deptType: deptType,
        deptCode: deptCode,
        nationalCode: nationalCode,
        nationalName: nationalName,
        regionCode: regionCode,
        nodeType: nodeType,
        label: label,
        leaderOrgId: leaderOrgId
    };

    if (id != null && id != '') {
        // ========== 更新逻辑 ==========
        var exist = db.table("rm_org_t")
            .where().eq("id", id).eq("deleted", 0).selectOne();

        if (exist == null) {
            // 参数或业务状态不满足要求时立即终止请求。
            exit 404, '记录不存在: ' + id;
        }

        // 普通操作（如启用/禁用）也会复用本接口；未传字段必须保留原值，不能写成空串。
        data.name = body?.name != null ? name : exist.name;
        data.parentId = body?.parentId != null && parentId != '' ? parentId : exist.parentId;
        data.shortName = body?.shortName != null ? shortName : exist.shortName;
        data.creditCode = body?.creditCode != null ? creditCode : exist.creditCode;
        data.standard = body?.standard != null ? standard : exist.standard;
        data.nature = body?.nature != null ? nature : exist.nature;
        data.status = body?.status != null ? status : exist.status;
        data.serialNumber = exist.serialNumber;
        data.sortNum = body?.sortNum != null ? sortNum : exist.sortNum;
        data.description = body?.description != null ? description : exist.description;
        data.deptType = body?.deptType != null ? deptType : exist.deptType;
        data.deptCode = body?.deptCode != null ? deptCode : exist.deptCode;
        data.nationalCode = body?.nationalCode != null ? nationalCode : exist.nationalCode;
        data.nationalName = body?.nationalName != null ? nationalName : exist.nationalName;
        data.regionCode = body?.regionCode != null ? regionCode : exist.regionCode;
        data.nodeType = body?.nodeType != null ? nodeType : exist.nodeType;
        data.label = body?.label != null ? label : exist.label;
        data.leaderOrgId = body?.leaderOrgId != null ? leaderOrgId : exist.leaderOrgId;
        data.updateId = createId || updateId || exist.updateId;
        data.updateTime = now;

        if (data.name == null || data.name == '') {
            exit 400, '机构名称不能为空';
        }

        var oldParentId = exist.parentId || 'ROOT';
        var targetParentId = data.parentId || 'ROOT';
        var parentChanged = targetParentId != oldParentId;
        var nameChanged = data.name != exist.name;
        var oldOrgPath = exist.orgPath || '';
        var oldSerialNumber = exist.serialNumber || '';
        var newOrgPath = oldOrgPath;
        var newSerialNumber = oldSerialNumber;

        if (parentChanged) {
            if (id == 'ROOT') {
                exit 400, '全部机构为系统根节点，不能修改上级机构';
            }
            if (targetParentId == id) {
                exit 400, '不能将机构设置为自身的上级机构';
            }

            var parent = db.table("rm_org_t")
                .where().eq("id", targetParentId).eq("deleted", 0).selectOne();
            if (parent == null) {
                exit 404, '上级机构不存在: ' + targetParentId;
            }

            // 父级链反向检查，禁止把节点移动到自己的任意子孙节点下。
            var ancestorId = targetParentId;
            var visitedParents = {};
            while (ancestorId && ancestorId != 'ROOT') {
                if (ancestorId == id) {
                    exit 400, '不能将机构设置为其子孙机构的下级';
                }
                if (visitedParents[ancestorId] == true) {
                    exit 400, '上级机构层级存在循环，无法保存';
                }
                visitedParents[ancestorId] = true;
                var ancestor = db.table("rm_org_t")
                    .where().eq("id", ancestorId).eq("deleted", 0).selectOne();
                if (ancestor == null) {
                    exit 404, '上级机构链不完整';
                }
                ancestorId = ancestor.parentId;
            }

            var siblings = db.table("rm_org_t")
                .where().eq("parentId", targetParentId).eq("deleted", 0).select();
            var nextNum = siblings.size() + 1;
            var nextNumStr = nextNum < 10 ? ('000' + nextNum) : (nextNum < 100 ? ('00' + nextNum) : (nextNum < 1000 ? ('0' + nextNum) : ('' + nextNum)));
            newSerialNumber = (parent.serialNumber || '') + nextNumStr;
            newOrgPath = (parent.orgPath || '') != '' ? (parent.orgPath + '/' + data.name) : data.name;
        } else if (nameChanged) {
            var currentParent = db.table("rm_org_t")
                .where().eq("id", targetParentId).eq("deleted", 0).selectOne();
            newOrgPath = currentParent != null && (currentParent.orgPath || '') != ''
                ? currentParent.orgPath + '/' + data.name
                : data.name;
        }

        if (parentChanged || nameChanged) {
            var duplicate = db.table("rm_org_t")
                .where().eq("parentId", targetParentId).eq("name", data.name).eq("deleted", 0).ne("id", id).selectOne();
            if (duplicate != null) {
                exit 400, '上级机构下已存在同名机构: ' + data.name;
            }
            data.orgPath = newOrgPath;
            data.serialNumber = newSerialNumber;
        }

        // 先持久化当前机构，再重写全部子孙的路径前缀，保证父级修改后树路径保持一致。
        db.table("rm_org_t").withBlank()
            .where().eq("id", id)
            .update(data);

        if ((parentChanged || nameChanged) && oldOrgPath != '' && newOrgPath != oldOrgPath) {
            var descendants = [];
            var queue = [id];
            while (queue.size() > 0) {
                var currentId = queue.remove(0);
                var children = db.table("rm_org_t")
                    .where().eq("parentId", currentId).eq("deleted", 0).select();
                for (child in children) {
                    descendants.add(child);
                    queue.add(child.id);
                }
            }

            for (descendant in descendants) {
                var descendantPath = descendant.orgPath || '';
                var descendantData = { updateId: data.updateId, updateTime: now };
                if (descendantPath.startsWith(oldOrgPath + '/')) {
                    descendantData.orgPath = newOrgPath + descendantPath.substring(oldOrgPath.length());
                }
                var descendantSerial = descendant.serialNumber || '';
                if (parentChanged && oldSerialNumber != '' && descendantSerial.startsWith(oldSerialNumber)) {
                    descendantData.serialNumber = newSerialNumber + descendantSerial.substring(oldSerialNumber.length());
                }
                db.table("rm_org_t").where().eq("id", descendant.id).update(descendantData);
            }
        }

        // 启用/禁用级联：更新该节点及所有子孙节点的status
        if (body?.status != null && (body.status == 0 || body.status == 1)) {
            var allIds = [id];
            var queue = [id];

            while (queue.size() > 0) {
                var currentId = queue.remove(0);
                // 从 MySQL 业务表读取当前有效数据。
                var children = db.table("rm_org_t")
                    .where().eq("parentId", currentId).eq("deleted", 0)
                    .select();

                for (child in children) {
                    allIds.add(child.id);
                    queue.add(child.id);
                }
            }

            // 批量更新所有子孙节点status（自身已在上方更新）
            if (allIds.size() > 1) {
                // 从 MySQL 业务表读取当前有效数据。
                db.table("rm_org_t")
                    .where().in("id", allIds).ne("id", id)
                    .update({ status: status, updateId: updateId, updateTime: now });
            }
        }

        // 返回当前业务分支的标准结果。
        return { id: id, parentId: targetParentId, orgPath: newOrgPath, serialNumber: newSerialNumber };

    } else {
        // ========== 新增逻辑 ==========
        // 参数校验
        if (name == null || name == '') {
            // 参数或业务状态不满足要求时立即终止请求。
            exit 400, '机构名称不能为空';
        }

        // parentId必传
        parentId = parentId || "ROOT"

        // 同级下同名校验
        var sameName = db.table("rm_org_t")
            .where().eq("parentId", parentId).eq("name", name).eq("deleted", 0).selectOne();
        if (sameName != null) {
            // 参数或业务状态不满足要求时立即终止请求。
            exit 400, '同级下已存在同名机构: ' + name;
        }

        var newId = IdUtil.getSnowflakeNextIdStr();

        // 若未传sortNum，查同级最大值+1
        if (!sortNum) {
            // 从 MySQL 业务表读取当前有效数据。
            var maxSortRow = db.table("rm_org_t")
                .where().eq("parentId", parentId != '' ? parentId : 'ROOT').eq("deleted", 0)
                .orderBy("sortNum", "desc").selectOne();
            sortNum = maxSortRow != null ? (maxSortRow.sortNum + 1) : 1;
        }

        // 计算orgPath和serialNumber
        var orgPath = '';
        var serialNumberNew = '';

        // 从 MySQL 业务表读取当前有效数据。
        var parent = db.table("rm_org_t")
                .where().eq("id", parentId).eq("deleted", 0).selectOne();

        if (!parent) {
            // 参数或业务状态不满足要求时立即终止请求。
            exit 500, '父级节点不存在';
        }
        
        var parentPath = parent.orgPath || '';
        orgPath = parentPath != '' ? (parentPath + '/' + name) : name;

        var parentSerial = parent.serialNumber || '';
        // 从 MySQL 业务表读取当前有效数据。
        var siblingCount = db.table("rm_org_t")
            .where().eq("parentId", parentId).eq("deleted", 0).select();
        var nextNum = siblingCount.size() + 1;
        // 4位补零格式：0001、0021、0102
        var nextNumStr = nextNum < 10 ? ('000' + nextNum) : (nextNum < 100 ? ('00' + nextNum) : (nextNum < 1000 ? ('0' + nextNum) : ('' + nextNum)));
        serialNumberNew = parentSerial + nextNumStr;

        data.sortNum = sortNum;
        data.id = newId;
        data.parentId = parentId;
        data.orgPath = orgPath;
        data.serialNumber = serialNumberNew != '' ? serialNumberNew : serialNumber;
        data.createId = createId;
        data.createTime = now;
        data.updateId = createId;
        data.updateTime = now;
        data.deleted = 0;

        // 将校验后的业务数据写入 MySQL 主表。
        db.table("rm_org_t").insert(data);

        // 返回当前业务分支的标准结果。
        return { id: newId };
    }

} catch(e) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '操作失败: ' + e.message;
}
