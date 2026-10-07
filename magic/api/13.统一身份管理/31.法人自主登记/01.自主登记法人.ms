{
  "properties": {},
  "id": "c97fb943c17750e8a59547fbb5f9ca03",
  "script": null,
  "groupId": "2c7d2be6a1f65dcc917d9d26722a0a72",
  "name": "01.自主登记法人",
  "createTime": 1790636427981,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/register",
  "description": "校验当前认证账号及法人登记资料，创建待核验法人和申请人与法人的成员关系。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：01.自主登记法人
// 路由：POST /idaas/self-entities/register
// 业务说明：校验当前认证账号及法人登记资料，创建待核验法人和申请人与法人的成员关系。
import db;
import identityTransport;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.crypto.digest.DigestUtil;
import '@/idaas/auth-account-context' as context;
import '@/idaas/audit' as audit;
import identityRuntime;
import request;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    var payload=body;
    // 请求参数与本接口业务处理。
    var operation = 'register';

    var ctx=context();
    // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
    var sql=db.normal();
    var p=payload||{};
    if(ctx.account.identity_domain!='public' || ctx.account.must_change_password==1) {
        exit 403,'请先登录并完成公众账号密码设置';
    }
    if(operation!='register' || p.consent!=true || p.requestId==null || !p.requestId.matches('[A-Za-z0-9_-]{8,64}')) {
        exit 400,'请同意法人资料登记并提供申请号';
    }
    var name=(p.name||'').trim();
    var registration=(p.registrationCode||'').trim().toUpperCase();
    if(name=='' || name.length()>200 || !registration.matches('[0-9A-HJ-NPQRTUWXY]{18}')) {
        exit 400,'法人名称或信用代码格式不正确';
    }
    // 把读取校验和状态写入放在同一事务，防止并发覆盖。
    return sql.transaction(()=>{
        sql.selectOne("""
select tid as id
from iam_application_t
where tid='idaas-platform-internal'
    for update
""");
        var hash=identityTransport.protectedFingerprint(DigestUtil.sha256Hex(identityTransport.canonicalJson(p)),'receipt:v2');
        var prior=sql.selectOne("""
select *,tid as id
from iam_request_receipt_t
where operator_id=#{ctx.userId}
    and operation_code='SELF_LEGAL_REGISTER'
    and request_id=#{p.requestId}
""");
        if(prior!=null) {
            if(prior.request_hash!=hash) {
                exit 409,'申请号资料不一致';
            }
            return JSONUtil.parseObj(prior.result_json);
        }
        if(sql.selectOne("""
select tid as id
from iam_legal_entity_t
where registration_code=#{registration}
""")!=null) {
            exit 409,'此法人已登记，请联系身份管理人员办理关联';
        }
        var id=IdUtil.fastSimpleUUID();
        var code='PUBLIC_LEGAL_'+id;
        var mid=IdUtil.fastSimpleUUID();
        sql.update("""
insert into iam_legal_entity_t(tid,
                created_by, code, name, registration_code, verification_status, status)
values(#{id},#{ctx.userId},#{code},#{name},#{registration},'UNVERIFIED','ACTIVE')
""");
        sql.update("""
insert into iam_legal_member_t(tid,
                created_by, entity_id, subject_id, auth_account_id, relation_type, valid_from, status)
values(#{mid},#{ctx.userId},#{id},#{ctx.account.subject_id},#{ctx.userId},'AGENT',utc_timestamp(6),'PENDING')
""");
        var result=identityTransport.canonicalJson({
            id:id,memberId:mid,verificationStatus:'UNVERIFIED',relationStatus:'PENDING'
        });
        var rid=IdUtil.fastSimpleUUID();
        sql.update("""
insert into iam_request_receipt_t(tid,
                operator_id, operation_code, request_id, request_hash, status, result_json)
values(#{rid},#{ctx.userId},'SELF_LEGAL_REGISTER',#{p.requestId},#{hash},'SUCCEEDED',#{result})
""");
        // 记录操作主体、目标及结果，供审计与故障追踪。
        audit(ctx,'SUCCESS','auth:legal-register',id,null,{
            id:id
        });
        return JSONUtil.parseObj(result);
    });

}));
