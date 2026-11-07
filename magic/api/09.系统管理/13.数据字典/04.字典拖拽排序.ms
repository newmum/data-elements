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
  "description": "在当前租户内按 tid、targetTid 和 dropType 调整字典顺序或移动子树，事务内批量更新并返回 true。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime
import dictionaryTreeStorage

/**
 * 字典拖拽排序。POST /sym/dictDragSort
 * 根据待移动节点 tid、参照节点 targetTid 和 dropType 调整同级顺序或移动子树。
 * 只操作当前会话租户的有效节点，兼容旧客户端传入的 targetParentId。
 * 在同一事务中复核快照并批量保存，成功返回 true，冲突或写入失败时回滚。
 */

// 参数只描述拖拽意图，目标父节点从当前租户的真实树关系推导。
var tid = (body?.tid || '') + ''
var targetTid = (body?.targetTid || '') + ''
var dropType = body?.dropType || ''
if (tid == '') {
    exit 500, 'tid 不能为空'
}
if (dropType == '') {
    exit 500, 'dropType 不能为空'
}
if (dropType != 'before' && dropType != 'after' && dropType != 'inner') {
    exit 500, 'dropType 必须为 before/after/inner'
}
if (targetTid == '') {
    exit 500, 'targetTid 不能为空'
}
if (tid == targetTid) {
    exit 500, '不能将节点拖拽到自身'
}
var tenantId = tenantRuntime.id()

// 历史数据中根节点使用空值、0 或 root 表示。
var isRoot = id => id == null || id == '' || id == '0' || id == 'root'
var nodesInTenant = () => db.lower().table('sym_dict_t')
    .columns('tid', 'parent_id', 'sort_no', 'level_no', 'tree_path')
    .where()
    .eq('tenant_id', tenantId)
    .eq('is_del', 0)
var rowMap = rows => {
    var result = {}
    for (row in rows) {
        result[row.tid] = row
    }
    return result
}
var pathOf = row => (row.tree_path || row.tid).replaceAll('[.]+$', '')
var change = (row, parentId, sortNo, levelNo, treePath) => ({
    before: row,
    after: {
        parent_id: parentId,
        sort_no: sortNo,
        level_no: levelNo,
        tree_path: treePath
    }
})
var treeSizeLimit = 10000

return db.transaction(() => {
    // 一次读取两个端点，避免分别查找时把其中一个外租户或已删除节点纳入拖拽。
    var nodes = rowMap(nodesInTenant().in('tid', [tid, targetTid]).select())
    var moving = nodes[tid]
    var target = nodes[targetTid]
    if (moving == null) {
        exit 500, '被拖拽节点不存在: ' + tid
    }
    if (target == null) {
        exit 500, '参照目标节点不存在: ' + targetTid
    }

    // 目标父级从租户内参照节点推导。旧客户端的 targetParentId 仅保留为兼容参数。
    var parentId = dropType == 'inner' ? target.tid : target.parent_id
    var parent = null
    var parentPath = '0'
    var guards = [moving, target]
    if (!isRoot(parentId)) {
        parent = nodes[parentId]
        if (parent == null) {
            parent = nodesInTenant().eq('tid', parentId).selectOne()
        }
        if (parent == null) {
            exit 409, '参照节点的父级已变化，请刷新后重试'
        }
        parentPath = pathOf(parent)
        var pathIds = []
        for (part in parentPath.split('[.]')) {
            if (!isRoot(part)) {
                pathIds.add(part)
            }
        }
        if (pathIds.size() == 0 || pathIds[pathIds.size() - 1] != parentId) {
            exit 409, '参照节点的祖先路径不完整，请刷新后重试'
        }
        pathIds.remove(pathIds.size() - 1)
        // 只按服务端路径批量读取候选祖先，随后按真实 parent_id 逐级验证链路。
        if (pathIds.size() > 509) {
            exit 400, '参照节点的祖先链过深'
        }
        var ancestors = pathIds.size() == 0 ? {} : rowMap(nodesInTenant().in('tid', pathIds).select())
        var visited = [tid]
        var next = parent
        var index = pathIds.size() - 1
        while (next != null) {
            if (visited.contains(next.tid)) {
                exit 500, '不能将节点拖拽到其子孙节点下，会导致循环嵌套'
            }
            visited.add(next.tid)
            if (next.tid != targetTid) {
                guards.add(next)
            }
            if (isRoot(next.parent_id)) {
                if (index >= 0) {
                    exit 409, '参照节点的祖先路径不完整，请刷新后重试'
                }
                break
            }
            if (index < 0 || pathIds[index] != next.parent_id) {
                exit 409, '参照节点的祖先路径不完整，请刷新后重试'
            }
            next = ancestors[next.parent_id]
            if (next == null) {
                exit 409, '参照节点的祖先链不完整，请刷新后重试'
            }
            index = index - 1
        }
    }

    // 读取一个受限的同级窗口，按排序值及标识确定顺序，空排序值统一排最后。
    var siblingsQuery = nodesInTenant().ne('tid', tid)
    if (parentId == null) {
        siblingsQuery.isNull('parent_id')
    } else {
        siblingsQuery.eq('parent_id', parentId)
    }
    var siblingsPage = siblingsQuery.orderBy('sort_no').orderBy('tid').page(treeSizeLimit + 1, 0)
    if (siblingsPage.total > treeSizeLimit) {
        exit 400, '同级字典项超过 10000 项'
    }
    var siblingRows = siblingsPage.list || []
    var siblings = []
    siblings.addAll(siblingRows.filter(row => row.sort_no != null))
    siblings.addAll(siblingRows.filter(row => row.sort_no == null))
    var ordered = []
    for (sibling in siblings) {
        if (dropType == 'before' && sibling.tid == targetTid) {
            ordered.add(moving)
        }
        ordered.add(sibling)
        if (dropType == 'after' && sibling.tid == targetTid) {
            ordered.add(moving)
        }
    }
    if (dropType == 'inner') {
        ordered.add(moving)
    }
    if (ordered.size() != siblings.size() + 1) {
        exit 409, '参照节点已变化，请刷新后重试'
    }

    // 同级排序只变更顺序。跨父级才调整移动节点与子孙的路径和层级。
    var sameParent = moving.parent_id == parentId
    var movingPath = sameParent ? moving.tree_path : parentPath + '.' + tid
    var movingLevel = sameParent
        ? moving.level_no
        : (parent == null ? (target.level_no || 1) : (parent.level_no || 1) + 1)
    if (movingPath != null && movingPath.length() > 1000) {
        exit 400, '移动后的字典路径过长'
    }

    var changes = []
    var order = 0
    for (row in ordered) {
        order = order + 1
        if (row.tid == tid && !sameParent) {
            changes.add(change(row, parentId, order, movingLevel, movingPath))
        } else if (row.sort_no != order) {
            changes.add(change(row, row.parent_id, order, row.level_no, row.tree_path))
        }
    }
    if (!sameParent) {
        var oldPath = pathOf(moving)
        var prefix = oldPath + '.'
        var descendantsPage = nodesInTenant().like('tree_path', prefix + '%').page(treeSizeLimit + 1, 0)
        if (descendantsPage.total > treeSizeLimit) {
            exit 400, '待移动的子树超过 10000 项'
        }
        var oldLevel = moving.level_no || 1
        for (row in (descendantsPage.list || [])) {
            // LIKE 可能把历史标识内的下划线视为通配符，只有精确前缀匹配才能纳入改动。
            if (!row.tree_path.startsWith(prefix)) {
                continue
            }
            var suffix = row.tree_path.substring(oldPath.length())
            var path = movingPath + suffix
            if (path.length() > 1000) {
                exit 400, '移动后的字典路径过长'
            }
            var level = row.level_no == null
                ? movingLevel + suffix.split('[.]').length - 1
                : row.level_no + movingLevel - oldLevel
            changes.add(change(row, row.parent_id, row.sort_no, level, path))
        }
    }

    if (changes.size() > 0) {
        // 复核并锁住已经授权的路径快照。其他请求先改动任一相关节点时，本次整批回滚。
        dictionaryTreeStorage.requireUnchanged(guards)
        dictionaryTreeStorage.applyChanges(changes)
    }
    return true
})
