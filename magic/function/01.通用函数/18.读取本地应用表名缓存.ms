{
  "properties": {},
  "id": "08aba97010b949d39bafa1e13f71619a",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "18.读取本地应用表名缓存",
  "createTime": 1782660730492,
  "updateTime": 1787153840900,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/localCache",
  "description": "先按 tid 查本地名称缓存；未命中时依 assetType 从资产属性、机构或接入任务表读取名称并写回缓存。",
  "returnType": null,
  "mappingPath": null,
  "parameters": [
    {
      "name": "tid",
      "value": null,
      "description": "目标记录主键",
      "required": false,
      "dataType": "String",
      "type": "java.lang.String",
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "assetType",
      "value": null,
      "description": "待读取名称的资产、机构或接入任务类型",
      "required": false,
      "dataType": "String",
      "type": "java.lang.String",
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ]
}
================================
// 共享函数：18.读取本地应用表名缓存
// 函数路径：/common/localCache
// 业务说明：先按 tid 查本地名称缓存；未命中时依 assetType 从资产属性、机构或接入任务表读取名称并写回缓存。
// 函数输入：tid：目标记录主键；assetType：待读取名称的资产、机构或接入任务类型。
// LocalCache  bean 对象
import com.linewell.dataelement.utils.LocalCache as cache
import log
//获取资产缓存
var value = cache.get(tid);
if( value != null && value != "") { 
    return value
}else {
   if( assetType == "app" || assetType == "db" || assetType == "table" || assetType == "catalog") {
      var assetPropValue = db.table("da_prop_t").column("prop_value").where()
         .eq(assetType == "app", "prop_name","appName")
         .eq(assetType == "db", "prop_name","dbName")
         .eq(assetType == "table", "prop_name","tableName")
         .eq(assetType == "catalog", "prop_name","catalogName")
         .eq("parent_id",tid)
         .selectOne()
         log.info(assetPropValue);
         if( assetPropValue != null){
            cache.put(tid, assetPropValue.propValue);
         }
   }else {
      //获取组织机构名称
      if( assetType == "org" ){
          var orgInfo= db.table('rm_org_t')
                .where()
                .eq('id', tid)
                .eq('deleted', '0').selectOne()
         if( orgInfo != null){
            cache.put(tid, orgInfo?.orgPath)
         }
      } 
      //获取组织机构路径
      else  if( assetType == "orgPath" ){
          id = tid.split(":")[0]
          var orgInfo= db.table('rm_org_t')
                .where()
                .eq('id', id)
                .eq('deleted', '0').selectOne()
         if( orgInfo != null){
            cache.put(tid, orgInfo?.serialNumber)
         }
      } 
      // 接入任务
      else if ( assetType == "accessTask" ){
          var taskInfo= db.table('data_access_agg_task_t')
                .where()
                .eq('tid', tid)
                .eq('is_del', '0').selectOne()
         if( taskInfo != null){
            cache.put(tid, taskInfo?.taskName)
         }  
      }
   }
}
return cache.get(tid)
