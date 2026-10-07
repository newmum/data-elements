{
  "properties": {},
  "id": "fa050435331a44da84dafa64d74d39c5",
  "script": null,
  "groupId": "9668e1a8b17d458f8739c9558d60ca98",
  "name": "04.字典拖拽排序",
  "createTime": 1782660715695,
  "updateTime": 1784437771321,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/dictDragSort",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"4929b4a49206483e81ea9501fe6e670a\",\n    \"targetParentId\": \"0\",\n    \"targetTid\": \"ca77c1b09d344090baff16c2070bd02d\",\n    \"dropType\": \"after\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "- tid:            被拖拽节点 ID（必填）；执行数据库写入并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.字典拖拽排序
// 路由：POST /sym/dictDragSort
// 业务说明：- tid:            被拖拽节点 ID（必填）；执行数据库写入并返回处理结果。
// 树节点拖拽排序接口
// 路径: /dictDragSort
// 方法: POST
// 入参:
//   - tid:            被拖拽节点 ID（必填）
//   - targetParentId: 拖拽完成后归属的父节点 ID（根节点传 '0'）
//   - targetTid:      参照目标节点 ID（必填）
//   - dropType:       放置类型：before / after / inner（必填）

var tid            = body?.tid || '';
var targetParentId = body?.targetParentId || '0';
var targetTid      = body?.targetTid || '';
var dropType       = body?.dropType || '';

// ========== 1. 参数校验 ==========
if (tid == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, 'tid 不能为空';
}
if (dropType == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, 'dropType 不能为空';
}
if (dropType != 'before' && dropType != 'after' && dropType != 'inner') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, 'dropType 必须为 before/after/inner';
}
if (targetTid == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, 'targetTid 不能为空';
}

// ========== 2. 查询被拖拽节点 ==========
var curNode = db.lower().table("sym_dict_t")
    .where().eq("tid", tid).eq("is_del", 0).selectOne();
if (curNode == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '被拖拽节点不存在: ' + tid;
}

// ========== 3. 防循环嵌套校验 ==========
// 若 dropType = inner，需确保 tid 不在 targetTid 的子孙链中
if (dropType == 'inner' && targetTid != '0') {
    var checkTid = targetTid;
    var hasCycle = false;
    while (checkTid != null && checkTid != '' && checkTid != '0') {
        if (checkTid == tid) {
            hasCycle = true;
            break;
        }
        // 从 MySQL 业务表读取当前有效数据。
        var parentNode = db.lower().table("sym_dict_t")
            .where().eq("tid", checkTid).eq("is_del", 0).selectOne();
        if (parentNode == null) { break; }
        checkTid = parentNode.parent_id;
    }
    if (hasCycle) {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 500, '不能将节点拖拽到其子孙节点下，会导致循环嵌套';
    }
}

// ========== 4. 查询参照目标节点 ==========
var targetNode = db.lower().table("sym_dict_t")
    .where().eq("tid", targetTid).eq("is_del", 0).selectOne();
if (targetNode == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '参照目标节点不存在: ' + targetTid;
}

// ========== 5. 执行拖拽 ==========
try {
    // 目标父节点的 tree_path
    var newParentTreePath = '';
    if (targetParentId == '0' || targetParentId == 'root') {
        newParentTreePath = '0';
    } else {
        // 从 MySQL 业务表读取当前有效数据。
        var newParentNode = db.lower().table("sym_dict_t")
            .where().eq("tid", targetParentId).eq("is_del", 0).selectOne();
        if (newParentNode != null) {
            newParentTreePath = newParentNode.tree_path || targetParentId;
        } else {
            newParentTreePath = targetParentId;
        }
    }

    // 查询参照目标节点的所有同级子节点（排除已删除和自身）
    var targetSiblings = db.lower().table("sym_dict_t")
        .where().eq("parent_id", targetParentId).eq("is_del", 0)
        .ne("tid", tid).orderBy("sort_no").select();

    var insertIdx = 0;
    if (dropType == 'inner') {
        // inner: 成为目标节点子节点，排在最后
        var targetChildren = db.lower().table("sym_dict_t")
            .where().eq("parent_id", targetTid).eq("is_del", 0)
            .ne("tid", tid).orderBy("sort_no").select();
        insertIdx = targetChildren.size();
        // 替换 siblings 为目标节点的子节点列表
        targetSiblings = targetChildren;
        targetParentId = targetTid;
        newParentTreePath = targetNode.tree_path || targetTid;
    } else {
        // before / after: 同级插入，找到目标节点在 siblings 中的位置
        var targetIdx = -1;
        var idxCounter = 0;
        for (s in targetSiblings) {
            if (s.tid == targetTid) {
                targetIdx = idxCounter;
                break;
            }
            idxCounter = idxCounter + 1;
        }
        if (targetIdx < 0) {
            // 目标不在 siblings 中（理论上不会发生），插入末尾
            insertIdx = targetSiblings.size();
        } else if (dropType == 'before') {
            insertIdx = targetIdx;
        } else {
            // after
            insertIdx = targetIdx + 1;
        }
    }

    // 在 insertIdx 位置插入被拖拽节点
    var newChildren = [];
    var inserted = false;
    var ciCounter = 0;
    for (s in targetSiblings) {
        if (ciCounter == insertIdx && !inserted) {
            newChildren.push(tid);
            inserted = true;
        }
        newChildren.push(s.tid);
        ciCounter = ciCounter + 1;
    }
    // 如果插入位置在末尾
    if (!inserted) {
        newChildren.push(tid);
    }

    // ========== 6. 批量更新 sort_no ==========
    var sortCounter = 0;
    for (childTid in newChildren) {
        sortCounter = sortCounter + 1;
        var newSortNo = sortCounter;
        if (childTid == tid) {
            // 更新被拖拽节点：parent_id + sort_no + level_no + tree_path
            var newLevelNo = 1;
            if (targetParentId != 'root') {
                // 从 MySQL 业务表读取当前有效数据。
                var parentNode2 = db.lower().table("sym_dict_t")
                    .where().eq("tid", targetParentId).eq("is_del", 0).selectOne();
                if (parentNode2 != null) {
                    newLevelNo = (parentNode2.level_no || 1) + 1;
                } else {
                    newLevelNo = 2;
                }
            }
            var newTreePath = newParentTreePath + '.' + tid;
            db.lower().table("sym_dict_t").primary("tid").update({
                tid: tid,
                parent_id: targetParentId,
                sort_no: newSortNo,
                level_no: newLevelNo,
                tree_path: newTreePath
            });
        } else {
            // 更新同级其他节点：仅 sort_no
            db.lower().table("sym_dict_t").primary("tid").update({
                tid: childTid,
                sort_no: newSortNo
            });
        }
    }

    // ========== 7. 更新被拖拽节点所有子孙的 tree_path ==========
    var oldTreePath = curNode.tree_path || '';
    var newTreePathForUpdate = newParentTreePath + '.' + tid;

    if (oldTreePath != '' && oldTreePath != newTreePathForUpdate) {
        // 从 MySQL 业务表读取当前有效数据。
        var descendants = db.lower().table("sym_dict_t")
            .where().like("tree_path", oldTreePath + '.%').eq("is_del", 0).select();
        for (node in descendants) {
            var nodeOldPath = node.tree_path || '';
            var nodeNewPath = nodeOldPath.replace(oldTreePath, newTreePathForUpdate);
            db.lower().table("sym_dict_t").primary("tid").update({
                tid: node.tid,
                tree_path: nodeNewPath
            });
        }
    }

    // 返回当前业务分支的标准结果。
    return true;
} catch(e) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '拖拽排序失败: ' + e.message;
}
