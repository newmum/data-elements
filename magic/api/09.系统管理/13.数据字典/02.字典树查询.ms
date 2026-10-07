{
  "properties": {},
  "id": "f7622ea4ac5547af954ae35a3ab56826",
  "script": null,
  "groupId": "9668e1a8b17d458f8739c9558d60ca98",
  "name": "02.字典树查询",
  "createTime": 1782955148933,
  "updateTime": 1786191954494,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/getDict",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "字典数据查询接口（树形结构）；使用请求参数 tid、parentId、dictCode、dictName、levelNo、isDel、showRow；涉及 sym_dict_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.字典树查询
// 路由：POST /sym/getDict
// 业务说明：字典数据查询接口（树形结构）；使用请求参数 tid、parentId、dictCode、dictName、levelNo、isDel、showRow；涉及 sym_dict_t 等业务表。
import tenantRuntime
/**
 * 字典数据查询接口（树形结构）
 * 二级字典为字典类型，其dictCode 要唯一
 * GET /api/dict/query
 *
 * 满足需求：
 * 1. 根据 level_no 查询字典类型
 * 2. 根据 dict_code 查询【该节点 + 所有下级字典树】
 *
 * 调用示例：
 * /api/dict/query?dictCode=tv_program_type
 * 返回：电视节目类型 + 新闻类、综艺类、影视剧...
 */

// 1. 参数获取
var tid = body?.tid || query?.tid
var parentId = body?.parentId || query?.parentId
var dictCode = body?.dictCode || query?.dictCode
var dictName = body?.dictName || query?.dictName
var levelNo = body?.levelNo || query?.levelNo
var tenantId = tenantRuntime.id()
var isDel = body?.isDel || query?.isDel
var showRow = body?.showRow // 返回列表结构，而非树形结构

if (isDel == null) {
    isDel = 0
}
const toTree = (list, rootId) => {
    const map = {}
    list.forEach(i => {i.children = []; map[i.tid] = i})

    // 返回当前业务分支的标准结果。
    return list.filter(i => {
        const p = map[i.parentId || root]
        p ? p.children.push(i) : null
        // 返回当前业务分支的标准结果。
        return !p
    }).sort((a, b) => a.sortNo - b.sortNo)
}


// =====================================================================
// 有父节点，即根据 dicCode、parentId查询
// =====================================================================
var rootNode;
if (dictCode) {
    // 1. 先查当前 dictCode 对应的节点
    rootNode = db.table('sym_dict_t')
        .where()
        .eq('is_del', isDel)
        .eq('dict_code', dictCode)
        .orderBy('sort_no')
        .selectOne()

    if (!rootNode) {
        // 页面侧可选字典缺失时返回空数组，不能因为一个字典项缺失阻断页面加载。
        return []
    }
}

if (parentId) {
    // 从 MySQL 业务表读取当前有效数据。
    rootNode = db.table('sym_dict_t')
        .where()
        .eq('is_del', isDel)
        .eq('tid', parentId)
        .orderBy('sort_no')
        .selectOne()
}

// 从 MySQL 业务表读取当前有效数据。
var list = db.table('sym_dict_t')
            .excludes(["updatedTime", "updatedBy", "createdTime", "createdBy", "isDel", "tenantId"])
            .where()
            .eq('is_del', isDel)


if (dictName) {
    list = list.like('dict_name', '%' + dictName + '%')
}

// 有根节点
if (rootNode){
    // 根据 tree_path 查询所有子孙节点（核心！）
    var rows = list
        .like('tree_path', rootNode.treePath + '.%')
        .orderBy('sort_no')
        .select()
    rows.each(item => {
        item.label = item.dictName
        item.value = item.dictCode
    })
    // 返回当前业务分支的标准结果。
    return showRow ? rows : toTree(rows, rootNode.tid)
}

// 多个根节点
if (levelNo) {
    list = list.eq('level_no', levelNo)
}

// 单个节点
if (tid) {
    list = list.eq('tid', tid)
}

var rows = list.orderBy('sort_no').select() || []

rows.each(item => {
    item.label = item.dictName
    item.value = item.dictCode
})

// 返回当前业务分支的标准结果。
return rows
