{
  "properties": {},
  "id": "cdebc557515854f7aff31170e0b6c630",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "04.幂等事务写入",
  "createTime": 1790612165860,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/mutate",
  "description": "将写操作、版本冲突处理、幂等键和审计封装在同一事务边界内，执行调用方提供的业务回调。",
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
      "name": "operation",
      "value": null,
      "description": "本次执行的业务操作",
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
      "name": "payload",
      "value": null,
      "description": "请求资料或筛选条件",
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
      "name": "callback",
      "value": null,
      "description": "在事务中执行的业务回调",
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
// 共享函数：04.幂等事务写入
// 业务说明：将写操作、版本冲突处理、幂等键和审计封装在同一事务边界内，执行调用方提供的业务回调。
// 函数输入：ctx：已校验的操作上下文；operation：本次执行的业务操作；payload：请求资料或筛选条件；callback：在事务中执行的业务回调。
// 返回与副作用：将写操作、版本冲突处理、幂等键和审计封装在同一事务边界内，执行调用方提供的业务回调。
import identityTransport;
import db;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.core.util.IdUtil;
import '@/idaas/guard' as guard;
import '@/idaas/context' as context;

// IAM同库事务：全局写入互斥用于首阶段权限/引用完整性，乐观版本、幂等、审计同事务。
var sql=db.normal();
var p=payload||{};
if(p.requestId==null || !p.requestId.matches('[A-Za-z0-9_-]{8,64}')) {
    exit 400,'请提供有效请求号';
}
var requestId=p.requestId;
var operatorId=ctx.userId;
var hash=identityTransport.protectedFingerprint(DigestUtil.sha256Hex(JSONUtil.toJsonStr(p)),'receipt:v2');
// 把读取校验和状态写入放在同一事务，防止并发覆盖。
var output=sql.transaction(()=>{
    // 单一小规模平台写入锁；角色变更与检查共享同一锁，阻止并发转授或清空最后授权入口。
    sql.selectOne("""
select tid as id
from iam_application_t
where tid='idaas-platform-internal'
    for update
""");
    var fresh=ctx.requiredPermission==null || ctx.requiredDirectory==true?context():guard(ctx.requiredPermission,ctx.requiredAppId,ctx.requiredDomain);
    ctx.pairs=fresh.pairs;
    ctx.permissions=fresh.permissions;
    ctx.account=fresh.account;
    var prior=sql.selectOne("""
select *,tid as id
from iam_request_receipt_t
where operator_id=#{operatorId}
    and operation_code=#{operation}
    and request_id=#{requestId}
    for update
""");
    if(prior!=null) {
        if(prior.request_hash!=hash) {
            exit 409,'相同请求号的内容不一致';
        }
        return JSONUtil.parse(prior.result_json);
    }
    var result=callback();
    var receiptId=IdUtil.fastSimpleUUID();
    var encoded=JSONUtil.toJsonStr(result);
    sql.update("""
insert into iam_request_receipt_t(tid,
                operator_id, operation_code, request_id, request_hash, status, result_json)
values(#{receiptId},#{operatorId},#{operation},#{requestId},#{hash},'SUCCEEDED',#{encoded})
""");
    return result;
});
return output;
