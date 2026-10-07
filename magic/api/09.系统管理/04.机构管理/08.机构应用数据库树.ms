{
  "properties": {},
  "id": "9caa9255f24d4bd08ca8de3ab9eb7de5",
  "script": null,
  "groupId": "c1736799c6af43359a75e6764601931f",
  "name": "08.机构应用数据库树",
  "createTime": 1782660716227,
  "updateTime": 1784437771357,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/getOrgAppDbTree",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"parentId\": \"2057646041423282177\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致；使用请求参数 parentId；涉及 rm_org_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：08.机构应用数据库树
// 路由：POST /sym/org/getOrgAppDbTree
// 业务说明：执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致；使用请求参数 parentId；涉及 rm_org_t 等业务表。
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import '@/common/localCache' as localCache
import jsons
import '@/common/configData' as configData

// 解析请求体并为可选参数设置默认值。
var parentId = configData().orgRootId//body?.parentId ? body?.parentId : query?.parentId


// 从 MySQL 业务表读取当前有效数据。
var parent = db.table('rm_org_t').where().eq("id",parentId).selectOne();


// 从 MySQL 业务表读取当前有效数据。
var list = db.table('rm_org_t').excludes("shortName","creditCode","standard","createId","updateId","createTime","updateTime","deleted")
    .where()
    .like('serial_number', parent.serialNumber + '%')
    .eq('deleted', '0')

var rows = list.orderBy('sort_num').select()

var ids =[]
if (rows == null) {
    rows = []
} else {
    rows.each(item -> {
        ids.add(item.id)
    })
}

var jsonQuery = {
    "bool": {
      "filter": [
        { "terms": { "orgId": ids } },
        { "terms": { "assetType": ["app","db"] } }
      ]
    }
}
// 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
existInxex = esCommonService.indexExists("dataassets");
var esDataList
if(!existInxex){
    esDataList = []
}else {
    // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
    esDataList = esCommonService.searchByJsonQuery("dataassets", jsons.toJSONStr(jsonQuery) ,Map.class, 1,2000).list
}

var mapDb = {}
var mapApp = {}
//遍历应用
esDataList.each(item -> {
    if(item.assetType == 'app'){
        if( mapApp[item.orgId] == null){
            mapApp[item.orgId] = []
        }
        mapApp[item.orgId].add(item)
    }else  if(item.assetType == 'db' && item.orgId != null){
        if( mapDb[item.orgId] == null){
            mapDb[item.orgId] = []
        }
       mapDb[item.orgId].add(item)
    }
})

var appRows = []
 rows.each(item -> {
        if(mapApp[item.id] != null){
            mapApp[item.id].each(t -> {
                let temp = {}
                temp.tid = t.id
                temp.id = t.id
                temp.parentId = item.id
                temp.name = t.appName
                temp.type = "app"
                appRows.add(temp)
            }) 
        }
    }) 

//添加组织机构下应用信息
rows.addAll(appRows)


var dbRows = []
appRows.each(item -> {
    if(mapDb[item.parentId] != null){
            mapDb[item.parentId].each(t -> {
                //比较应用id是否一致
                if(item.id == t.appId) {
                    let temp = {}
                    temp.tid = t.id
                    temp.id = t.id
                    temp.parentId = item.tid
                    temp.name = t.dbName
                    temp.type = "db"
                    temp.dbType = t.dbType
                    dbRows.add(temp)
                }
            }) 
        }
    })

//添加组织机构下数据库信息
rows.addAll(dbRows)

rows.each(item ->{
    item.label = item.name
    item.value = item.id
})


var toTree = (list,parentId)=>{
    // 返回当前业务分支的标准结果。
    return list.filter(it => it.parentId == parentId) // 根据parentId 过滤当前节点
    .each(it=>{
        it.children = toTree(list,it.id)    // 过滤后循环这些节点递归添加children属性
    }) 
}

// 根节点为 0
return toTree(rows, parentId)
