{
  "properties": {},
  "id": "cd79504e4b1448d3ab46ee6dc1034a8d",
  "script": null,
  "groupId": "019b772ee9b44705a27710f91bbd731a",
  "name": "01.标准目录树",
  "createTime": 1782955344942,
  "updateTime": 1784437771824,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "standard-class-list",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "标准目录树：为数据元和标准代码页面提供统一分类树；使用请求参数 tid；涉及 sym_dict_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.标准目录树
// 路由：POST /dwm/standard/standard-class-list
// 业务说明：标准目录树：为数据元和标准代码页面提供统一分类树；使用请求参数 tid；涉及 sym_dict_t 等业务表。
// 标准目录树：为数据元和标准代码页面提供统一分类树。
// 只读 sym_dict_t 中 biz_type=standard_class、is_del=0 的记录，不修改业务数据。
// 返回以 tid=0 为根节点的树；空目录安全返回空数组。
var tid = body?.tid ? body?.tid : query?.tid
var isDel = 0

// ========== 1. 查询数据 ==========
var list = db.table('sym_dict_t').excludes("createdBy","createdTime","updatedBy","updatedTime","isDel","tenantId")
    .where().eq("biz_type","standard_class")
    .eq('is_del', isDel)

if (tenantId != null && tenantId != '') {
    list = list.eq('tenant_id', tenantId)
}


var rows = list.orderBy('sort_no').select()

if (rows == null) {
    rows = []
    // 返回当前业务分支的标准结果。
    return rows
}

// 根据parentId 过滤当前节点
var toTree = (list,parentId)=>{
    // 返回当前业务分支的标准结果。
    return list.filter(it => it.parentId == parentId)
    .each(it=>{
        it.children = toTree(list,it.tid)    // 过滤后循环这些节点递归添加children属性
    }) 
}

//添加根节点
var root =  db.table('sym_dict_t').excludes("createdBy","createdTime","updatedBy","updatedTime","isDel","tenantId")
    .where().eq("tid","0")
    .eq('is_del', isDel).selectOne()

if(root != null){
    rows.addFirst(root)
}


var root;
//字段转换
for( item in rows){
    item.label = item.dictName
    item.value = item.dictCode
    item.remove("dictName")
    item.remove("dictCode")
    //parentId
    if(item.tid == '0'){
        root = item;
    }
}
if(rows.size() == 0){
    // 返回当前业务分支的标准结果。
    return rows
}
// 根节点为 0
var treeDate = toTree(rows, '0')
root.children = treeDate

// 返回当前业务分支的标准结果。
return root
