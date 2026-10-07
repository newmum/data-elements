{
  "properties": {},
  "id": "54371175f20c5b8cb28c1769c05e2180",
  "script": null,
  "groupId": "2c7d2be6a1f65dcc917d9d26722a0a72",
  "name": "02.查询本人法人登记",
  "createTime": 1790636427981,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/mine",
  "description": "按当前认证账号查询其申请或可办理的法人资料和审核状态，仅返回本人可见记录。",
  "parameters": [],
  "method": "GET",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：02.查询本人法人登记
// 路由：GET /idaas/self-entities/mine
// 业务说明：按当前认证账号查询其申请或可办理的法人资料和审核状态，仅返回本人可见记录。
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
    var payload={};
    for(key in ['id','appId','domain']) {
        var value=request.getValues(key);
        if(value!=null && value.size()>0) {
            payload.put(key,value[0]);
        }
    }
    // 请求参数与本接口业务处理。
    var operation = 'mine';

    var ctx=context();
    // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
    var sql=db.normal();
    var p=payload||{};
    if(ctx.account.identity_domain!='public' || ctx.account.must_change_password==1) {
        exit 403,'请先登录并完成公众账号密码设置';
    }
    if(operation=='mine') {
        return sql.select("""
select e.tid as id,e.name,e.registration_code registrationCode,
       case
           when e.verification_status='VERIFIED'
                and not exists
                    (select 1
                     from iam_verification_t v
                     where v.target_type='LEGAL_ENTITY'
        and v.target_id=e.tid
        and v.status='VERIFIED'
        and v.expires_at>utc_timestamp(6)
        and v.tid=
        (select x.tid as id
        from iam_verification_t x
        where x.target_type='LEGAL_ENTITY'
        and x.target_id=e.tid
        order by x.requested_time desc, x.tid desc
        limit 1)) then 'EXPIRED' else e.verification_status
       end verificationStatus,m.tid memberId,
       case
           when m.status='ACTIVE'
                and (m.valid_from>utc_timestamp(6)
                     or m.valid_until<=utc_timestamp(6)) then 'EXPIRED' else m.status
       end relationStatus,m.relation_type relationType
from iam_legal_entity_t e
join iam_legal_member_t m on m.entity_id=e.tid
and m.subject_id=#{ctx.account.subject_id}
and m.is_del=0
where e.is_del=0
order by e.created_time desc
""");
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
