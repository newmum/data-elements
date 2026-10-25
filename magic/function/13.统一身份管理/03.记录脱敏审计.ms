{
  "properties": {},
  "id": "6f5fe722a1bc5c7595107fbd4e6c7c1c",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "03.记录脱敏审计",
  "createTime": 1790612165860,
  "updateTime": 1790948254995,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/audit",
  "description": "写入操作主体、对象、动作与结果，对请求摘要中的敏感字段脱敏，供多个写接口复用。",
  "parameters": [
    {
      "name": "ctx",
      "value": null,
      "description": "已校验的操作上下文",
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
      "name": "result",
      "value": null,
      "description": "业务执行结果",
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
      "name": "action",
      "value": null,
      "description": "审计动作编码",
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
      "name": "objectId",
      "value": null,
      "description": "被操作对象标识",
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
      "name": "reason",
      "value": null,
      "description": "操作原因",
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
      "name": "summary",
      "value": null,
      "description": "脱敏后的审计摘要",
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
// 在调用方同一个 Magic SQL 对象和事务中追加已脱敏事件；不先插入再补写。
import db;
import auditEvents;
var event=auditEvents.preparePlatform(ctx,result,action,objectId,reason,summary);
db.normal().table(event.table).primary('tid').insert(event.row);
return event.row.tid;
