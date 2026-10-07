{
  "properties": {},
  "id": "7ce142655939405bbcd64abace9ba0a7",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "17.按父节点读取属性映射",
  "createTime": 1782660730470,
  "updateTime": 1787153840799,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/getPropMap",
  "description": "按父节点标识读取关联属性，组装为可按键访问的映射供表单和配置查询复用。",
  "returnType": null,
  "mappingPath": null,
  "parameters": [
    {
      "name": "parentId",
      "value": null,
      "description": "父节点标识",
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
// 共享函数：17.按父节点读取属性映射
// 函数路径：/common/getPropMap
// 业务说明：按父节点标识读取关联属性，组装为可按键访问的映射供表单和配置查询复用。
// 函数输入：parentId：父节点标识。
import cn.hutool.json.JSONUtil
import log
import cn.hutool.core.util.IdUtil
import IdGenerator
import jsons


// 1. 查询旧的动态属性数据
var oldProps = db.table('da_prop_t')
    .where()
    .eq('parent_id', parentId)
    //.eq('data_type', assetType)
    .eq('is_del', 0)
    .select()

// 2. 构建旧属性 Map: prop_name -> {tid, prop_value, prop_type}
var oldPropMap = {}
if (oldProps != null && oldProps.size() > 0) {
    for (prop in oldProps) {
        // oldPropMap[prop.propName] = {
        //     tid: prop.tid,
        //     prop_value: prop.propValue,
        //     prop_type: prop?.propType
        // }
        oldPropMap[prop.propName] = prop.propValue
    }
}

var oldAsset = db.table('da_asset_t')
    .where()
    .eq('tid', parentId)
    //.eq('data_type', assetType)
    .eq('is_del', 0)
    .selectOne()

oldPropMap.tid = parentId
oldPropMap.assetType = oldAsset?.assetType


log.info('旧属性数量: {}', oldPropMap.size())

return oldPropMap
