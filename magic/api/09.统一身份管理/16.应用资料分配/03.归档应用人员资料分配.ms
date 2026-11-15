{
  "properties": {},
  "id": "130e0dc6612c5250b7abfd777d1fd1be",
  "script": null,
  "groupId": "58dc0d18e4185b67bf18980e9a47e878",
  "name": "03.归档应用人员资料分配",
  "createTime": 1790615423659,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/remove",
  "description": "按标识加载应用人员资料分配并检查所属范围与版本；执行软删除或撤销关联，保留审计历史。管理中央人员资料与指定应用的分配关系；资料分配和访问授权分别维护。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：03.归档应用人员资料分配
// 路由：POST /idaas/assignments/remove
// 业务说明：按标识加载应用人员资料分配并检查所属范围与版本；执行软删除或撤销关联，保留审计历史。管理中央人员资料与指定应用的分配关系；资料分配和访问授权分别维护。
import db;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import '@/idaas/guard' as guard;
import '@/idaas/mutate' as mutate;
import '@/idaas/audit' as audit;
import identityRuntime;
import request;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    var payload=body;
    // 请求参数与本接口业务处理。
    var operation = 'remove';

    // 人员/机构资料分配与访问授权独立。未得到接收回执不得写ack_version。
    // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
    var sql=db.normal();
    var p=payload||{};
    var r=p.record||p;
    var appId=r.appId||p.appId;
    var domain=r.domain||p.domain||'workforce';
    // 先校验当前会话的功能权限、身份域和应用管理范围。
    var ctx=guard(operation=='list'?'assignments:read':'assignments:write',appId,domain);
    var app=sql.selectOne("""
select tid as id,identity_domains
from iam_application_t
where tid=#{appId}
    and kind='BUSINESS'
    and status='ACTIVE'
    and is_del=0
""");
    if(app==null) {
        exit 400,'有效业务应用不存在';
    }
    if(!JSONUtil.parseArray(app.identity_domains).contains(domain)) {
        exit 400,'应用尚未支持该身份域';
    }
    // 写入通过共享的幂等事务处理，并记录审计；版本冲突直接返回。
    return mutate(ctx,'assignments:'+operation,p,()=>{
        var type=r.type||'subject';
        if(!['org','subject'].contains(type)) {
            exit 400,'分配类型不正确';
        }
        var id=r.id;
        var table=type=='org'?'iam_app_org_t':'iam_subject_app_t';
        var old=id==null?null:sql.selectOne('select *,tid as id from '+table+' where tid=#{id} for update');
        // 以当前版本校验修改目标，阻止旧页面覆盖已经变更的记录。
        if(old!=null && (old.app_id!=appId || old.version!=r.version)) {
            exit 409,'分配归属或版本已变更';
        }
        var objectId=type=='org'?r.orgId:r.subjectId;
        var objectTable=type=='org'?'iam_org_t':'iam_subject_t';
        var object=sql.selectOne('select tid as id,status from '+objectTable+' where tid=#{objectId} and identity_domain=#{domain} and is_del=0');
        if(object==null || (operation!='remove' && object.status!='ACTIVE')) {
            exit 400,'同域有效资料不存在';
        }
        if(old==null) {
            old=sql.selectOne('select *,tid as id from '+table+' where app_id=#{appId} and '+(type=='org'?'org_id':'subject_id')+'=#{objectId} for update');
            if(old!=null) {
                exit 409,'已有分配请使用原ID及版本';
            }
        }
        if(operation=='remove' && old==null) {
            exit 404,'分配不存在';
        }
        if(old!=null && (type=='org'?old.org_id:old.subject_id)!=objectId) {
            exit 400,'分配对象保持稳定';
        }
        if(type=='org') {
            var status=operation=='remove'?'REVOKED':'ACTIVE';
            var include=r.includeChildren==true?1:0;
            if(old==null) {
                id=IdUtil.fastSimpleUUID();
                sql.update("""
insert into iam_app_org_t(tid, app_id, org_id, include_children, status)
values(#{id},#{appId},#{objectId},#{include},#{status})
""");
            }
            else {
                sql.update("""
update iam_app_org_t
set include_children=#{include},status=#{status},version=version+1,
        updated_time=utc_timestamp(6)
where tid=#{id}
""");
            }
        }
        else {
            var alias=(r.accountAlias||'').trim();
            if(alias!='' && !alias.matches('[A-Za-z][A-Za-z0-9_.-]{2,99}')) {
                exit 400,'应用拟用账号不正确';
            }
            var assigned=operation=='remove'?0:1;
            var mode=old==null?'DIRECTORY_ONLY':old.assignment_mode;
            var status=assigned==1 || mode=='GRANT_DERIVED'?'ACTIVE':'REVOKED';
            if(old==null) {
                id=IdUtil.fastSimpleUUID();
                sql.update("""
insert into iam_subject_app_t(tid,
                subject_id, app_id, account_alias, directory_assigned, assignment_mode, assignment_status,
                desired_enabled, sync_status)
values(#{id},#{objectId},#{appId},#{alias},#{assigned},'DIRECTORY_ONLY',#{status},0,'NOT_CONNECTED')
""");
            }
            else {
                sql.update("""
update iam_subject_app_t
set account_alias=#{alias},directory_assigned=#{assigned},assignment_status=#{status},
    desired_version=desired_version+1,version=version+1,updated_time=utc_timestamp(6)
where tid=#{id}
""");
            }
        }
        // 记录操作主体、目标及结果，供审计与故障追踪。
        audit(ctx,'SUCCESS','assignments:'+operation,id,null,{
            id:id,appId:appId,type:type
        });
        return {
            id:id
        };
    });

}));
