{
  "properties": {},
  "id": "bf142250e1a35995a1041adb40ea5bf4",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "09.接口访问与审计封装",
  "createTime": 1790612165860,
  "updateTime": 1790949884838,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/api-call",
  "description": "为多个平台接口统一校验请求来源、执行频率及调用审计，再执行调用方提供的业务回调。",
  "parameters": [
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
// 共享函数：09.接口访问与审计封装
// 业务说明：为多个平台接口统一校验请求来源、执行频率及调用审计，再执行调用方提供的业务回调。
// 函数输入：callback：在事务中执行的业务回调。
// 返回与副作用：为多个平台接口统一校验请求来源、执行频率及调用审计，再执行调用方提供的业务回调。
import db;
import request;
import platformSession;
import authSession;
import identityApi;
import cn.hutool.json.JSONUtil;
import cn.hutool.crypto.digest.DigestUtil;
import java.lang.System;
import '@/idaas/audit' as audit;

// 接口外层仅校验来源和频率，不保存请求正文、查询串或凭据；业务审计仍在写事务中完成。
var started=System.nanoTime();
var result='FAILED';
var path=request.get().getRequestURI();
var method=request.get().getMethod();
try {
    if(path.startsWith('/idaas/api/')) {
        var row=db.normal().selectOne("""
select value_json
from iam_setting_t
where category='API_ACCESS'
    and config_key='access'
    and scope_key='global'
    and is_del=0
    and status='ACTIVE'
""");
        if(row!=null) {
            var policy=JSONUtil.parseObj(row.value_json);
            var address=identityApi.remoteAddress();
            var deny=(policy.ipBlacklist||'').split('\n').filter(v=>v.trim()!='');
            var allow=(policy.apiWhitelist||'').split('\n').filter(v=>v.trim()!='');
            if(deny.filter(v=>identityApi.matchesAddress(v.trim(),address)).size()>0 || allow.size()>0 && allow.filter(v=>identityApi.matchesAddress(v.trim(),address)).size()==0) {
                exit 403,'接口来源不在允许范围';
            }
            if(!identityApi.withinRate(DigestUtil.sha256Hex(address),policy.apiRateLimit)) {
                exit 429,'接口请求超过当前配额';
            }
        }
    }
    var response=callback();
    result='SUCCESS';
    return response;
}
finally {
    var ctx=platformSession.isLogin()?{
        userId:platformSession.id()
    }
    :null;
    if(ctx==null && authSession.isLogin()) {
        var account=db.normal().selectOne("""
select identity_domain
from iam_auth_account_t
where tid=#{authSession.id()}
""");
        ctx={
            userId:authSession.id(),actorKind:'AUTH_ACCOUNT',requiredDomain:account==null?'public':account.identity_domain
        };
    }
    var id=audit(ctx,result,'api:'+path,null,result=='SUCCESS'?null:'REQUEST_FAILED',null);
    var duration=(System.nanoTime()-started)/1000000;
    var status=result=='SUCCESS'?200:null;
    db.normal().update("""
update iam_audit_event_t
set request_path=#{path},http_method=#{method},http_status=#{status},
    duration_ms=#{duration}
where tid=#{id}
""");
}
