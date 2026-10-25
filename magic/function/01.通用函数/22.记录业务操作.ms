{
  "id": "log_operation_function_v1",
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "22.记录业务操作",
  "path": "/audit-operation",
  "parameters": [
    {
      "name": "action",
      "dataType": "Object",
      "required": false,
      "description": "已经过业务授权与校验的操作信息"
    },
    {
      "name": "targetType",
      "dataType": "Object",
      "required": false,
      "description": "已经过业务授权与校验的操作信息"
    },
    {
      "name": "targetId",
      "dataType": "Object",
      "required": false,
      "description": "已经过业务授权与校验的操作信息"
    },
    {
      "name": "targetName",
      "dataType": "Object",
      "required": false,
      "description": "已经过业务授权与校验的操作信息"
    },
    {
      "name": "details",
      "dataType": "Object",
      "required": false,
      "description": "已经过业务授权与校验的操作信息"
    }
  ]
}
================================
// 在业务调用方的同一 Magic 事务中追加审计；只传已授权目标的白名单摘要。
import db;
import auditEvents;
var event=auditEvents.prepare('OPERATION',action,'SUCCESS',targetType,targetId,targetName,details);
var items=auditEvents.batchRows(event,details||{});
db.normal().table(event.table).primary('tid').insert(event.row);
for(item in items){db.normal().table('log_batch_item_t').primary('tid').insert(item);}
return event.row.tid;
