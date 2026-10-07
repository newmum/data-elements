{
  "properties": {},
  "id": "0629b541c30b4bf6823fc29c5861f335",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "02.保存数据对象",
  "createTime": 1782660730380,
  "updateTime": 1786498740258,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/save-data",
  "description": "按传入的数据对象和目标表执行保存处理，供通用数据写接口复用；调用方负责限定可写表和字段。",
  "returnType": null,
  "mappingPath": null,
  "parameters": [
    {
      "name": "tableName",
      "value": null,
      "description": "目标业务表名",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "data",
      "value": null,
      "description": "需要保存的数据",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ]
}
================================
// 共享函数：02.保存数据对象
// 函数路径：/common/save-data
// 业务说明：按传入的数据对象和目标表执行保存处理，供通用数据写接口复用；调用方负责限定可写表和字段。
// 函数输入：tableName：目标业务表名；data：需要保存的数据。
import cn.dev33.satoken.stp.StpUtil
import cn.hutool.core.util.IdUtil
import cn.hutool.core.date.DateUtil

String userId = StpUtil.getLoginId()
// 默认字段赋值
data = {
    ...data,
    updatedTime: DateUtil.now(),
    updatedBy: userId,
}
if(data.tid){
    db.table(tableName).primary("tid").update(data)
}else{
    data = {
        ...data,
        createdTime: DateUtil.now(),
        createdBy: userId,
        isDel: 0
    }
    var tid = IdUtil.getSnowflakeNextIdStr()
    db.table(tableName).primary("tid", tid).insert(data)
    data.tid = tid
}
return data
