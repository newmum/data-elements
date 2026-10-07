{
  "properties": {},
  "id": "548164e3c6de52b3b8e8b9563dcec75b",
  "script": null,
  "groupId": "36e1ceb97f9550bcb7092a621f8ee8bd",
  "name": "01.读取实名核验服务配置",
  "createTime": 1790635007936,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/provider",
  "description": "读取已登记实名核验提供方及可展示状态，对密钥与回调凭据脱敏。",
  "parameters": [],
  "method": "GET",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：01.读取实名核验服务配置
// 路由：GET /idaas/verifications/provider
// 业务说明：读取已登记实名核验提供方及可展示状态，对密钥与回调凭据脱敏。
import db;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import identityTransport;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import '@/idaas/auth-account-context' as accountContext;
import '@/idaas/guard' as guard;
import '@/idaas/mutate' as mutate;
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
    var operation = 'provider';

    // 核验资料加密保存；只有受信任提供方的真实签名回执可以标记通过。
    var sql=db.normal();
    var p=payload||{};
    if(operation=='provider' || operation=='save-provider') {
        // 先校验当前会话的功能权限、身份域和应用管理范围。
        var ctx=guard(operation=='provider'?'public:read':'public:write',null,'public');
        var old=sql.selectOne("""
select *,tid as id
from iam_setting_t
where scope_kind='GLOBAL'
    and scope_key='global'
    and category='VERIFICATION'
    and config_key='provider'
    and is_del=0
""");
        if(operation=='provider') {
            var value=old==null?{}:JSONUtil.parseObj(old.value_json);
            return {
                id:old==null?null:old.id,version:old==null?null:old.version,configured:old!=null && value.enabled==true,providerCode:value.providerCode||'',endpoint:value.endpoint||'',enabled:value.enabled||false,status:old==null || value.enabled!=true?'UNCONFIGURED':'CONFIGURED'
            };
        }
        // 写入通过共享的幂等事务处理，并记录审计；版本冲突直接返回。
        return mutate(ctx,'verification:save-provider',p,()=>{
            old=sql.selectOne("""
select *,tid as id
from iam_setting_t
where scope_kind='GLOBAL'
    and scope_key='global'
    and category='VERIFICATION'
    and config_key='provider'
    and is_del=0
    for update
""");
            // 以当前版本校验修改目标，阻止旧页面覆盖已经变更的记录。
            if(old!=null && old.version!=p.version) {
                exit 409,'核验配置已变更';
            }
            var code=(p.providerCode||'').trim();
            if(!code.matches('[A-Za-z][A-Za-z0-9_-]{2,63}')) {
                exit 400,'核验渠道编码不正确';
            }
            var endpoint=identityTransport.validateUrl(p.endpoint);
            var id=old==null?IdUtil.fastSimpleUUID():old.id;
            var encoded=JSONUtil.toJsonStr({
                providerCode:code,endpoint:endpoint,enabled:p.enabled==true
            });
            if(old==null) {
                sql.update("""
insert into iam_setting_t(tid,
                scope_kind, scope_key, category, config_key, value_json, status)
values(#{id},'GLOBAL','global','VERIFICATION','provider',#{encoded},'ACTIVE')
""");
            }
            else {
                sql.update("""
update iam_setting_t
set value_json=#{encoded},version=version+1
where tid=#{id}
""");
            }
            var prior=sql.selectOne("""
select *,tid as id
from iam_credential_t
where owner_kind='VERIFY_PROVIDER'
    and owner_id=#{id}
    and credential_type='HMAC_KEY'
    and status='ACTIVE'
    for update
""");
            if(p.secret==null || p.secret=='') {
                if(prior==null) {
                    exit 400,'核验接入密钥不能为空';
                }
            }
            else {
                var cipher=identityTransport.encrypt(p.secret);
                var revision=prior==null?1:prior.revision+1;
                if(prior!=null) {
                    sql.update("""
update iam_credential_t
set status='RETIRED'
where tid=#{prior.id}
""");
                }
                var cid=IdUtil.fastSimpleUUID();
                sql.update("""
insert into iam_credential_t(tid,
                owner_kind, owner_id, credential_type, revision, algorithm, secret_cipher, status)
values(#{cid},'VERIFY_PROVIDER',#{id},'HMAC_KEY',#{revision},'AES256_GCM',#{cipher},'ACTIVE')
""");
            }
            // 记录操作主体、目标及结果，供审计与故障追踪。
            audit(ctx,'SUCCESS','verification:save-provider',id,null,{
                id:id,provider:code
            });
            return {
                id:id
            };
        });
    }
    var ctx=accountContext();
    if(ctx.account.identity_domain!='public' || ctx.account.must_change_password==1) {
        exit 403,'请使用已完成密码设置的公众认证账号';
    }
    var provider=sql.selectOne("""
select *,tid as id
from iam_setting_t
where category='VERIFICATION'
    and config_key='provider'
    and scope_key='global'
    and is_del=0
    and status='ACTIVE'
""");
    var settings=provider==null?{}:JSONUtil.parseObj(provider.value_json);
    if(settings.enabled!=true) {
        exit 503,'实名核验提供方尚未配置，不能完成实名核验';
    }
    var key=sql.selectOne("""
select secret_cipher
from iam_credential_t
where owner_kind='VERIFY_PROVIDER'
    and owner_id=#{provider.id}
    and credential_type='HMAC_KEY'
    and status='ACTIVE'
""");
    if(key==null) {
        exit 503,'核验提供方凭据未配置';
    }
    var type=p.targetType||'SUBJECT';
    var target=type=='SUBJECT'?ctx.account.subject_id:p.targetId;
    if(!['SUBJECT','LEGAL_ENTITY'].contains(type) || p.consent!=true || p.requestId==null || !p.requestId.matches('[A-Za-z0-9_-]{8,64}')) {
        exit 400,'请明确同意核验并提供申请号';
    }
    if(type=='LEGAL_ENTITY' && sql.selectOne("""
select tid as id
from iam_legal_member_t
where entity_id=#{target}
    and subject_id=#{ctx.account.subject_id}
    and relation_type in ('REPRESENTATIVE','AGENT')
    and (status='ACTIVE'
         or status='PENDING'
         and created_by=#{ctx.userId})
    and is_del=0
    and valid_from<=utc_timestamp(6)
    and (valid_until is null
         or valid_until>utc_timestamp(6))
limit 1
""")==null) {
        exit 403,'没有此法人的有效经办资格';
    }
    var document=(p.document||'').trim();
    if(document=='' || document.length()>100 || (p.documentType||'').length()>32) {
        exit 400,'核验资料不正确';
    }
    var identity=type=='SUBJECT'?sql.selectOne("""
select name
from iam_subject_t
where tid=#{target}
    and is_del=0
"""):sql.selectOne("""
select name,registration_code
from iam_legal_entity_t
where tid=#{target}
    and is_del=0
""");
    if(identity==null || type=='LEGAL_ENTITY' && identity.registration_code!=document.toUpperCase()) {
        exit 400,'法人核验资料须匹配登记的信用代码';
    }
    var requestId=DigestUtil.sha256Hex(ctx.userId+':'+p.requestId).substring(0,32);
    var inputHash=identityTransport.protectedFingerprint(type+':'+target+':'+identity.name+':'+document+':'+p.documentType,'verification');
    // 把读取校验和状态写入放在同一事务，防止并发覆盖。
    var created=sql.transaction(()=>{
        var prior=sql.selectOne("""
select *,tid as id
from iam_verification_t
where provider_code=#{settings.providerCode}
    and provider_request_id=#{requestId}
    for update
""");
        if(prior!=null) {
            if(prior.consent_ref!=inputHash) {
                exit 409,'同一申请号的核验资料不一致';
            }
            return prior;
        }
        var id=IdUtil.fastSimpleUUID();
        sql.update("""
insert into iam_verification_t(tid,
                created_by, target_type, target_id, provider_code, provider_request_id, consent_ref,
                requested_time)
values(#{id},#{ctx.userId},#{type},#{target},#{settings.providerCode},#{requestId},#{inputHash},utc_timestamp(6))
""");
        if(type=='SUBJECT') {
            var profile=sql.selectOne("""
select *,tid as id
from iam_public_profile_t
where subject_id=#{target}
""");
            var pid=profile==null?IdUtil.fastSimpleUUID():profile.id;
            var cipher=identityTransport.protectValue(document,'DOCUMENT:'+target);
            var fingerprint=identityTransport.protectedFingerprint(document,'document:'+p.documentType);
            if(profile==null) {
                sql.update("""
insert into iam_public_profile_t(tid,
                subject_id, document_type, document_cipher, document_fingerprint, verification_status,
                verification_id)
values(#{pid},#{target},#{p.documentType},#{cipher},#{fingerprint},'PENDING',#{id})
""");
            }
            else {
                sql.update("""
update iam_public_profile_t
set document_type=#{p.documentType},document_cipher=#{cipher},document_fingerprint=#{fingerprint},
    verification_status='PENDING',verification_id=#{id},version=version+1
where tid=#{pid}
""");
            }
        }
        else {
            sql.update("""
update iam_legal_entity_t
set verification_status='PENDING',version=version+1
where tid=#{target}
""");
        }
        return sql.selectOne("""
select *,tid as id
from iam_verification_t
where tid=#{id}
""");
    });
    if(created.status!='PENDING') {
        return {
            id:created.id,status:created.status
        };
    }
    var body=identityTransport.canonicalJson({
        protocol:'IDAAS_VERIFY/1',providerCode:settings.providerCode,requestId:requestId,targetType:type,targetId:target,name:identity.name,documentType:p.documentType,document:document,consentRef:inputHash
    });
    var receipt=null;
    try {
        receipt=identityTransport.sendVerification(settings.endpoint,key.secret_cipher,settings.providerCode,requestId,body);
    }
    catch(e) {
        sql.update("""
update iam_verification_t
set result_code='PROVIDER_UNAVAILABLE',version=version+1
where tid=#{created.id}
""");
        return {
            id:created.id,status:'PENDING',resultCode:'PROVIDER_UNAVAILABLE'
        };
    }
    if(receipt.protocol!='IDAAS_VERIFY/1' || receipt.providerCode!=settings.providerCode || receipt.requestId!=requestId || receipt.targetType!=type || receipt.targetId!=target || !['VERIFIED','REJECTED','PENDING'].contains(receipt.status) || (receipt.resultCode||'').length()>100) {
        exit 502,'核验回执目标或结果不正确';
    }
    var expiry=null;
    var evidence=receipt.evidenceRef;
    if(receipt.status=='VERIFIED') {
        try {
            var end=Instant.parse(receipt.expiresAt);
            var now=Instant.now();
            if(!end.isAfter(now) || end.isAfter(now.plusSeconds(31536000)) || evidence==null || !evidence.matches('[A-Za-z0-9_.:/-]{1,1000}')) {
                exit 502,'核验回执缺少有效期限或受控证据引用';
            }
            expiry=DateTimeFormatter.ofPattern('yyyy-MM-dd HH:mm:ss.SSSSSS').format(LocalDateTime.ofInstant(end,ZoneOffset.UTC));
        }
        catch(e) {
            exit 502,'核验回执有效期限不正确';
        }
    }
    return sql.transaction(()=>{
        var current=type=='SUBJECT'?sql.selectOne("""
select name
from iam_subject_t
where tid=#{target}
    and is_del=0
    for update
"""):sql.selectOne("""
select name,registration_code
from iam_legal_entity_t
where tid=#{target}
    and is_del=0
    for update
""");
        if(current==null || current.name!=identity.name || type=='LEGAL_ENTITY' && current.registration_code!=identity.registration_code) {
            sql.update("""
update iam_verification_t
set status='REJECTED',result_code='IDENTITY_CHANGED',version=version+1
where tid=#{created.id}
""");
            return {
                id:created.id,status:'REJECTED',resultCode:'IDENTITY_CHANGED'
            };
        }
        var status=receipt.status;
        sql.update("""
update iam_verification_t
set status=#{status},result_code=#{receipt.resultCode},completed_time=case
        when #{status}='PENDING' then null
        else utc_timestamp(6)
        end,
        expires_at=#{expiry},
        evidence_ref=#{evidence},version=version+1
where tid=#{created.id}
""");
        if(type=='SUBJECT') {
            sql.update("""
update iam_public_profile_t
set verification_status=#{status},verified_time=case
        when #{status}='VERIFIED' then utc_timestamp(6) else null
        end,version=version+1
where subject_id=#{target}
    and verification_id=#{created.id}
""");
        }
        else {
            var latest=sql.selectOne("""
select tid as id
from iam_verification_t
where target_type='LEGAL_ENTITY'
    and target_id=#{target}
order by requested_time desc,tid desc
limit 1
""");
            if(latest!=null && latest.id==created.id) {
                sql.update("""
update iam_legal_entity_t
set verification_status=#{status},version=version+1
where tid=#{target}
""");
            }
        }
        audit(ctx,status=='VERIFIED'?'SUCCESS':'FAILED','auth:verification',target,status,{
            id:created.id,status:status
        });
        return {
            id:created.id,status:status
        };
    });

}));
