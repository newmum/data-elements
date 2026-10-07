{
  "properties": {},
  "id": "b053321931715e798c0c032e180ca907",
  "script": null,
  "groupId": "1813da709f3a559fa44c0f77be8cb11a",
  "name": "02.修改本人密码",
  "createTime": 1790612165860,
  "updateTime": 1791205192024,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/password",
  "description": "核验当前会话、原密码和新密码策略，更新平台账号安全凭据并使旧会话失效。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：02.修改本人密码
// 路由：POST /idaas/profile/password
// 业务说明：核验当前会话、原密码和新密码策略，更新平台账号安全凭据并使旧会话失效。
import db;
import platformSession;
import iamSecurity;
import '@/idaas/context' as context;
import '@/idaas/mutate' as mutate;
import '@/idaas/audit' as audit;
import '@/idaas/password-policy' as passwordPolicy;
import '@/idaas/operator-name' as operatorName;
import cn.hutool.core.util.IdUtil;
import identityRuntime;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    // 请求参数与本接口业务处理。
    var operation = 'password';
    var payload = body||{};

    // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
    var sql=db.normal();
    var p=payload||{};
    var ctx=context();
    var id=ctx.userId;
    // 写入通过共享的幂等事务处理，并记录审计；版本冲突直接返回。
    return mutate(ctx,'profile:'+operation,p,()=>{
        var operator=sql.selectOne("""
select *,tid as id
from iam_operator_t
where tid=#{id}
    and is_del=0
    and status='ACTIVE'
    for update
""");
        if(operator.version!=p.version) {
            exit 409,'个人资料已被修改，请刷新';
        }
        if(operation=='password') {
            var current=sql.selectOne("""
select *,tid as id
from iam_credential_t
where owner_kind='OPERATOR'
    and owner_id=#{id}
    and credential_type='PASSWORD'
    and status='ACTIVE'
""");
            // 核对凭据时兼容已登记的摘要算法，失败只更新安全状态而不创建会话。
            if(current==null || !iamSecurity.verify(p.oldPassword,current.algorithm,current.secret_hash)) {
                exit 400,'当前密码不正确';
            }
            passwordPolicy(p.password);
            if(p.oldPassword==p.password) {
                exit 400,'新密码需要与当前密码不同';
            }
            var encoded=iamSecurity.hash(p.password);
            var credentialId=IdUtil.fastSimpleUUID();
            var revision=current.revision+1;
            sql.update("""
update iam_credential_t
set status='RETIRED',updated_time=utc_timestamp(6)
where tid=#{current.id}
""");
            sql.update("""
insert into iam_credential_t(tid,
                owner_kind, owner_id, credential_type, revision, algorithm, secret_hash, status)
values(#{credentialId},'OPERATOR',#{id},'PASSWORD',#{revision},'PBKDF2_SHA256',#{encoded},'ACTIVE')
""");
            sql.update("""
update iam_account_security_t
set security_version=security_version+1,password_changed_time=utc_timestamp(6),failed_count=0,locked_until=null
where principal_kind='OPERATOR'
    and principal_id=#{id}
""");
            sql.update("""
update iam_operator_t
set must_change_password=0,version=version+1,updated_time=utc_timestamp(6)
where tid=#{id}
""");
        }
        else if(operation=='save') {
            var name=(p.name||'').trim();
            if(name=='' || name.length()>100) {
                exit 400,'显示名称不正确';
            }
            operatorName(id,operator.subject_id,name,p.subjectVersion,id,true);
            sql.update("""
update iam_operator_t
set version=version+1,updated_time=utc_timestamp(6)
where tid=#{id}
""");
        }
        else {
            exit 400,'操作不正确';
        }
        // 记录操作主体、目标及结果，供审计与故障追踪。
        audit(ctx,'SUCCESS','profile:'+operation,id,null,{
            id:id
        });
        return {
            id:id,relogin:operation=='password'
        };
    });

}));
