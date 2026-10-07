{
  "properties": {},
  "id": "c6d4f90ef64c535784d7f1df4030c92f",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "01.平台权限上下文",
  "createTime": 1790612165860,
  "updateTime": 1791205192024,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/context",
  "description": "从平台管理会话提取操作人、身份域、可管理应用和功能授权，供受保护接口建立统一的权限上下文。",
  "parameters": [],
  "returnType": null,
  "mappingPath": null
}
================================
// 共享函数：01.平台权限上下文
// 业务说明：从平台管理会话提取操作人、身份域、可管理应用和功能授权，供受保护接口建立统一的权限上下文。
// 函数输入：无显式参数；从当前 Magic 执行上下文读取会话或请求信息。
// 返回与副作用：从平台管理会话提取操作人、身份域、可管理应用和功能授权，供受保护接口建立统一的权限上下文。
import db;
import platformSession;
import request;

// 平台会话与即时权限来源。仅查询baseline的IAM表，不查询行业账号或切换业务库。
var sql=db.normal();
if(!platformSession.isLogin()) {
    exit 401,'请登录统一身份管理平台';
}
var operatorId=platformSession.id();
// 仅bootstrap只读闭包启用请求内快照；普通读写接口每次仍重新校验。
var scope=request.get().getAttribute('idaas.bootstrap.permissionSnapshot');
var copy=(value)=>({
    userId:value.userId,account:value.account,permissions:value.permissions,pairs:value.pairs
});
if(scope!=null && scope.operatorId==operatorId && scope.context!=null) {
    return copy(scope.context);
}
var account=sql.selectOne("""
select o.*,o.tid as id,s.security_version
from iam_operator_profile_v o
join iam_account_security_t s on s.principal_kind='OPERATOR'
and s.principal_id=o.tid
where o.tid=#{operatorId}
    and o.is_del=0
    and o.status='ACTIVE'
""");
if(account==null || platformSession.session().get('securityVersion')!=account.security_version) {
    platformSession.logout();
    exit 401,'平台账号或安全状态已变更，请重新登录';
}
var assignments=sql.select("""
select a.tid as id,a.role_id
from iam_operator_role_rela_t a
join iam_role_t r on r.tid=a.role_id
and r.is_del=0
and r.status='ACTIVE'
join iam_application_t app on app.tid=r.app_id
and app.kind='INTERNAL'
and app.is_del=0
and app.status='ACTIVE'
where a.operator_id=#{operatorId}
    and a.status='ACTIVE'
    and a.valid_from<=utc_timestamp(6)
    and (a.valid_until is null
         or a.valid_until>utc_timestamp(6))
""");
var permissions=[];
var pairs=[];
var assignmentIds=assignments.map(a=>a.id).distinct();
var roleIds=assignments.map(a=>a.role_id||a.roleId).distinct();
var codesByRole={};
var scopesByAssignment={};
if(assignmentIds.size()>0) {
    // 权限上下文按角色分配批量预取，不在每个 assignment 上查两次库。
    var codeRows=sql.select("""
select rr.role_id,r.code
from iam_role_resource_rela_t rr
join iam_resource_t r on r.tid=rr.resource_id
and r.app_id=rr.app_id
and r.status='ACTIVE'
and r.is_del=0
where rr.role_id in
    <foreach collection="roleIds" item="roleId" open="(" close=")" separator=",">#{roleId}</foreach>
""");
    var scopeRows=sql.select("""
select *,tid as id
from iam_operator_scope_t
where assignment_id in
    <foreach collection="assignmentIds" item="assignmentId" open="(" close=")" separator=",">#{assignmentId}</foreach>
""");
    for(row in codeRows) {
        var roleId=''+(row.role_id||row.roleId);
        if(codesByRole[roleId]==null) { codesByRole[roleId]=[]; }
        codesByRole[roleId].add(row.code);
    }
    for(row in scopeRows) {
        var assignmentId=''+(row.assignment_id||row.assignmentId);
        if(scopesByAssignment[assignmentId]==null) { scopesByAssignment[assignmentId]=[]; }
        scopesByAssignment[assignmentId].add(row);
    }
}
for(a in assignments) {
    var codes=codesByRole[''+(a.role_id||a.roleId)]||[];
    var scopes=scopesByAssignment[''+a.id]||[];
    for(code in codes) {
        if(!permissions.contains(code)) {
            permissions.add(code);
        }
    }
    pairs.add({
        assignmentId:a.id,roleId:a.role_id,codes:codes,scopes:scopes
    });
}
var result={
    userId:operatorId,account:account,permissions:permissions,pairs:pairs
};
if(scope!=null && scope.operatorId==operatorId) {
    scope.context=result;
    return copy(result);
}
return result;
