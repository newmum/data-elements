{
  "properties": {},
  "id": "17326938ed5c4cd0a2e377a3ed68d239",
  "script": null,
  "groupId": "d5ce0d4911e94d7bb3c22a9f67547406",
  "name": "01.质检维度查询",
  "createTime": 1782955345051,
  "updateTime": 1784437771839,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "getQualityDimension",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "读取质检规则及字典中的维度定义，组装质量规则页面可选择的维度资料。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.质检维度查询
// 路由：GET /dwm/quality/getQualityDimension
// 业务说明：读取质检规则及字典中的维度定义，组装质量规则页面可选择的维度资料。
import '@post:/sym/getDict' as getDict 
import log


// 从 MySQL 业务表读取当前有效数据。
var list = db.table('sym_dict_t').excludes("createdBy","createdTime","updatedBy","updatedTime","isDel")
    .where()
    .eq('is_del', 0)

var parentId = "1891300000000000001"

if (parentId != null && parentId != '' ) {
    // 未传 tid 时可按 parentId 筛选
    list = list.eq('parent_id', parentId)
}

var rows = list.orderBy('sort_no').select()


var dimensionId = []

if (rows == null) {
    rows = []
}else {
  rows.each(item ->{
    item.label = item.name
    item.value = item.dictCode
    dimensionId.add(item.value)
  })

  // 从 MySQL 业务表读取当前有效数据。
  var map = db.table('data_gov_quality_rule').columns("check_dimension","tid")
    .where()
    .in("check_dimension",dimensionId)
    .eq('is_del', 0)
    .select()
    // 根据api_group_id 分组，分组后，值为分组结果的大小
    .group(it => it.checkDimension,list => list.size())
    // 根据value排序
    .sort((k1,k2,v1,v2) => v2 - v1)

   rows.each(item ->{
     if(map[item.dictCode] != null) {
        item.count = map[item.dictCode]
     }else {
        item.count = 0
     }   
  })  
}

// 返回当前业务分支的标准结果。
return rows
