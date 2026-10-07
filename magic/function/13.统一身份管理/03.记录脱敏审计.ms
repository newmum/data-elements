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
// 共享函数：03.记录脱敏审计
// 业务说明：写入操作主体、对象、动作与结果，对请求摘要中的敏感字段脱敏，供多个写接口复用。
// 函数输入：ctx：已校验的操作上下文；result：业务执行结果；action：审计动作编码；objectId：被操作对象标识；reason：操作原因；summary：脱敏后的审计摘要。
// 返回与副作用：写入操作主体、对象、动作与结果，对请求摘要中的敏感字段脱敏，供多个写接口复用。
import db;
import request;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;

// 脱敏审计。调用方仅传入白名单摘要，严禁密码、密钥、令牌或请求原文。
var sql=db.normal();
var id=IdUtil.fastSimpleUUID();
var actor=ctx==null?null:ctx.userId;
var eventType=action.startsWith('auth:')?'AUTH':action.startsWith('api:')?'API':'OPERATION';
var snapshot=summary==null?null:JSONUtil.toJsonStr(summary);
var ip=request.getClientIP();
var actorKind=ctx==null?'ANONYMOUS':ctx.actorKind||'OPERATOR';
var domain=ctx==null?'workforce':ctx.requiredDomain||'workforce';
sql.update("""
insert into iam_audit_event_t(tid,
                event_type, identity_domain, actor_kind, actor_id, app_id, action, object_type, object_id,
                result, reason_code, trace_id, ip, event_time, snapshot_json, created_time, updated_time)
values(#{id},#{eventType},#{domain},#{actorKind},#{actor},null,#{action},'IAM',#{objectId},#{result},#{reason},#{id},#{ip},utc_timestamp(6),#{snapshot},utc_timestamp(6),utc_timestamp(6))
""");
return id;
