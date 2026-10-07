{
  "properties": {},
  "id": "4d54c6e215625d9ca6220ca6477b5d6c",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "25.统一认证账号上下文",
  "createTime": 1790635007936,
  "updateTime": 1790949884838,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/auth-account-context",
  "description": "核验统一认证账号会话并读取人员、法人办理身份和安全版本，区别于平台操作人上下文。",
  "parameters": [],
  "returnType": null,
  "mappingPath": null
}
================================
// 共享函数：25.统一认证账号上下文
// 业务说明：核验统一认证账号会话并读取人员、法人办理身份和安全版本，区别于平台操作人上下文。
// 函数输入：无显式参数；从当前 Magic 执行上下文读取会话或请求信息。
// 返回与副作用：核验统一认证账号会话并读取人员、法人办理身份和安全版本，区别于平台操作人上下文。
import db;
import authSession;
import '@/idaas/auth-session-policy' as sessionPolicy;

// 统一认证会话按独立的空闲时长校验，不能复用管理控制台登录态。
var policy=sessionPolicy('read',null);
if(!authSession.isLogin(policy.idleSeconds)) {
    exit 401,'请使用统一认证账号登录';
}
var id=authSession.id();
var sql=db.normal();
var a=sql.selectOne("""
select a.*,a.tid as id,s.security_version,subject.name
from iam_auth_account_t a
join iam_account_security_t s on s.principal_kind='AUTH_ACCOUNT'
and s.principal_id=a.tid
join iam_subject_t subject on subject.tid=a.subject_id
and subject.identity_domain=a.identity_domain
and subject.status='ACTIVE'
and subject.is_del=0
where a.tid=#{id}
    and a.status='ACTIVE'
    and a.is_del=0
""");
// 账号停用或安全版本变化时立即撤销旧会话，阻止旧令牌继续使用。
if(a==null || authSession.session().get('securityVersion')!=a.security_version) {
    authSession.logout();
    exit 401,'统一认证账号的安全状态已变更';
}
return {
    userId:a.id,account:a,actorKind:'AUTH_ACCOUNT',requiredDomain:a.identity_domain,permissions:[],pairs:[]
};
