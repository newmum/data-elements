{
  "properties": {},
  "id": "35e3408567e34c46abc946414c3405da",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "21.接入任务关联表批量读取",
  "path": "/access-table-family",
  "description": "在当前租户内批量读取分页来源表的有效下游关联，保留最近链路距离与稳定顺序。",
  "parameters": [
    {
      "name": "rootIds",
      "description": "调用方已经按当前租户和业务范围校验的当前页来源表 ID",
      "required": true,
      "dataType": "Array",
      "type": "java.util.List"
    }
  ],
  "options": []
}
================================
import tenantRuntime
import tableRead

/**
 * 批量读取本页来源表的现有物化链路。rootIds 由接口授权分页提供。
 * 返回每个来源的下游表、每个下游所属的来源、任务读取 ID 和关联数据源 ID。
 * 读取始终按当前租户和有效表限定。接口须再校验输出数据源及任务目标。
 * 最多 16 跳、2,000 个来源与下游关联。按最近距离、下游 ID 排序，无写入。
 * 超限返回 limitMessage，由接口转换为 HTTP 业务响应。
 */
var tenantId = tenantRuntime.id()
if (tenantId == null || ('' + tenantId).trim() == '') {
    exit 401, '当前登录租户无效，请重新登录'
}
if (rootIds == null || rootIds.size() > 100) {
    exit 400, '来源表分页参数无效'
}

var frontier = {}
var sourceIds = []
for (rootId in rootIds) {
    if (!frontier.containsKey(rootId)) {
        frontier[rootId] = [rootId]
        sourceIds.add(rootId)
    }
}

var targetIds = []
var targetIdsByRoot = {}
var rootsByTarget = {}
var associations = 0
var depth = 0
var edgeId = tableRead.column('edge', 'tid', 'TEXT')
var previousId = tableRead.column('edge', 'source_table_id', 'TEXT')
var canonicalParent = tableRead.column('parent', 'tid', 'TEXT')

// 每轮整批查询当前层。按主键推进窗口，超限时不返回截断的链路。
while (depth < 16 && !frontier.isEmpty()) {
    var previousIds = []
    for (previous in frontier.keySet()) { previousIds.add(previous) }
    var nextFrontier = {}
    var lastId = null
    var hasMore = true
    while (hasMore) {
        var edges = tableRead.from('db_table_t', 'edge', tenantId)
        edges.leftJoin('db_table_t', 'parent', 'tid', 'source_table_id', [
            tableRead.equal(tableRead.column('parent', 'is_del', 'NUMBER'), 0)
        ])
        edges.where(tableRead.all([
            tableRead.equal(tableRead.column('edge', 'is_del', 'NUMBER'), 0),
            tableRead.in(previousId, previousIds),
            tableRead.not(tableRead.equal(edgeId, previousId))
        ]))
        if (lastId != null) { edges.where(tableRead.greaterThan(edgeId, lastId)) }
        var rows = edges.rows({tid: edgeId, source_id: previousId, parent_id: canonicalParent}, edgeId, 'asc', 1000)
        for (row in rows) {
            var targetId = row.tid
            var roots = frontier[row.parent_id]
            if (roots == null) { continue }
            for (root in roots) {
                // 根本身不会作为目标。同一来源首次到达目标时距离最短，也能截断环路。
                if (root == targetId || (root == row.parent_id && root != row.source_id)) { continue }
                var matchedRoots = rootsByTarget[targetId]
                if (matchedRoots != null && matchedRoots.contains(root)) { continue }
                associations += 1
                if (associations > 2000) {
                    return {limitMessage: '当前页关联表超过 2000 项，请缩小页大小或选择具体数据源后重试'}
                }
                if (matchedRoots == null) {
                    matchedRoots = []
                    rootsByTarget[targetId] = matchedRoots
                    targetIds.add(targetId)
                }
                matchedRoots.add(root)
                if (targetIdsByRoot[root] == null) { targetIdsByRoot[root] = [] }
                targetIdsByRoot[root].add(targetId)
                if (nextFrontier[targetId] == null) { nextFrontier[targetId] = [] }
                nextFrontier[targetId].add(root)
            }
        }
        hasMore = rows.size() == 1000
        if (hasMore) { lastId = rows[rows.size() - 1].tid }
    }
    frontier = nextFrontier
    depth += 1
}

// 链路扫描只读标识。确定范围后一次读取已命中的表字段，并按各来源的链路顺序组装。
var targetsById = {}
var datasourceIds = []
if (!targetIds.isEmpty()) {
    var targetQuery = db.table('db_table_t').where().eq('tenant_id', tenantId).eq('is_del', 0)
    if (targetIds.size() > 1000) {
        targetQuery.and(ids => ids.in('tid', targetIds.subList(0, 1000)).or()
            .in('tid', targetIds.subList(1000, targetIds.size())))
    } else {
        targetQuery.in('tid', targetIds)
    }
    var targets = targetQuery.select()
    for (target in targets) {
        targetsById[target.tid] = target
        var datasourceId = target.datasource_id == null ? target.datasourceId : target.datasource_id
        if (datasourceId != null && !datasourceIds.contains(datasourceId)) { datasourceIds.add(datasourceId) }
        if (!sourceIds.contains(target.tid)) { sourceIds.add(target.tid) }
    }
}
var targetsByRoot = {}
for (root in targetIdsByRoot.keySet()) {
    targetsByRoot[root] = targetIdsByRoot[root].map(id => targetsById[id]).filter(row => row != null)
}
return {targetsByRoot: targetsByRoot, rootsByTarget: rootsByTarget, sourceIds: sourceIds, datasourceIds: datasourceIds}
