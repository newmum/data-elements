{
  "properties": {},
  "id": "3171a186086b59bd9f7a06bfb8754286",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "27.资源启用状态判断",
  "createTime": 1790636427981,
  "updateTime": 1790949884838,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/resource-enabled",
  "description": "按应用、角色和资源状态判断业务功能是否可用，供授权计算和接口保护共用。",
  "parameters": [
    {
      "name": "appId",
      "value": null,
      "description": "目标业务应用标识",
      "required": false,
      "dataType": "Object",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "identityDomain",
      "value": null,
      "description": "人员或公众身份域",
      "required": false,
      "dataType": "Object",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "resourceIds",
      "value": null,
      "description": "目标应用资源标识集合",
      "required": false,
      "dataType": "Object",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ],
  "returnType": null,
  "mappingPath": null
}
================================
// 共享函数：27.资源启用状态判断
// 业务说明：按应用、角色和资源状态判断业务功能是否可用，供授权计算和接口保护共用。
// 函数输入：appId：目标业务应用标识；identityDomain：人员或公众身份域；resourceIds：目标应用资源标识集合。
// 返回与副作用：按应用、角色和资源状态判断业务功能是否可用，供授权计算和接口保护共用。
import db;

// 资源祖先也是授权条件；父级缺失、停用、删除或形成环时，子资源一并拒绝。
var rows=db.normal().select("""
select tid as id,parent_id,status,is_del
from iam_resource_t
where app_id=#{appId}
    and identity_domain=#{identityDomain}
""");
return (resourceIds||[]).filter(id=>{
    var seen=[];
    var current=id;
    // 沿父资源逐级检查；缺失、停用、删除或出现环均拒绝该资源。
    while(current!=null) {
        if(seen.contains(current) || seen.size()>=50) {
            return false;
        }
        seen.add(current);
        var row=rows.filter(r=>r.id==current);
        if(row.size()!=1 || row[0].status!='ACTIVE' || row[0].is_del!=0) {
            return false;
        }
        current=row[0].parent_id;
    }
    return true;
});
