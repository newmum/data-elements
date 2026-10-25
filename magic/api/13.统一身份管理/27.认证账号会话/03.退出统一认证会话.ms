{
  "properties": {},
  "id": "15180e2b284a51a3b055e10e0602eaf7",
  "script": null,
  "groupId": "0d24c0ffb0bf58e59cf30e12786d94fe",
  "name": "03.退出统一认证会话",
  "createTime": 1790635007936,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/logout",
  "description": "定位当前统一认证账号会话并撤销令牌，记录退出事件；不影响平台控制台或租户本地会话。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 先撤销当前统一认证会话；协议会话撤销与审计故障不阻止本地退出。
import db;
import authSession;
import auditEvents;
import identityRuntime;
import log;
return identityRuntime.control(()=>{
    if(!authSession.isLogin()){return {loggedOut:true};}
    var id=authSession.id();
    authSession.logout();
    var confirmed=true;
    try {
        db.normal().update("""
        update iam_auth_session_t set revoked_at=utc_timestamp(6),revoke_reason='CENTER_LOGOUT',version=version+1
        where principal_kind='AUTH_ACCOUNT' and principal_id=#{id} and revoked_at is null
        """);
    } catch(error) {confirmed=false;log.error('统一认证协议会话撤销尚未确认');}
    auditEvents.afterLogout(confirmed);
    return {loggedOut:true,protocolRevocationConfirmed:confirmed};
});
