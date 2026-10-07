{
  "properties": {},
  "id": "5f940f70f14c5e8ba50ce9133a4f9a24",
  "script": null,
  "groupId": "66a86e6604e9528e80208f8083448b77",
  "name": "01.查询应用分组列表",
  "createTime": 1790612165860,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/list",
  "description": "读取符合请求筛选条件的应用分组，应用权限及身份域过滤后返回可展示字段；不发生写入。只操作控制库的应用分组，并维护排序和软删除状态；关联应用仍按独立应用记录管理。",
  "parameters": [],
  "method": "GET",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：01.查询应用分组列表
// 路由：GET /idaas/application-groups/list
// 业务说明：读取符合请求筛选条件的应用分组，应用权限及身份域过滤后返回可展示字段；不发生写入。只操作控制库的应用分组，并维护排序和软删除状态；关联应用仍按独立应用记录管理。
import db;
import '@/idaas/guard' as guard;
import '@/idaas/mutate' as mutate;
import '@/idaas/audit' as audit;
import cn.hutool.core.util.IdUtil;
import identityRuntime;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    // 请求参数与本接口业务处理。
    var operation = 'list';
    var payload = param||{};

    // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
    var sql=db.normal();
    var p=payload||{};
    var r=p.record||p;
    // 先校验当前会话的功能权限、身份域和应用管理范围。
    var ctx=guard(operation=='list'?'apps:read':'apps:write',null,'workforce');
    if(operation=='list') {
        return sql.select("""
select tid as id,code,name,sort_no,version
from iam_application_group_t
where is_del=0
order by sort_no,code
""");
    }
    // 写入通过共享的幂等事务处理，并记录审计；版本冲突直接返回。
    return mutate(ctx,'groups:'+operation,p,()=>{
        var id=r.id;
        var old=sql.selectOne("""
select *,tid as id
from iam_application_group_t
where tid=#{id}
    and is_del=0
    for update
""");
        // 以当前版本校验修改目标，阻止旧页面覆盖已经变更的记录。
        if(old!=null && old.version!=r.version) {
            exit 409,'分组已被修改，请刷新';
        }
        if(old==null && p.creating!=true) {
            exit 404,'分组不存在';
        }
        if(operation=='remove') {
            if(sql.selectOne("""
select tid as id
from iam_application_t
where group_id=#{id}
    and is_del=0
limit 1
""")!=null) {
                exit 409,'分组仍有关联应用';
            }
            sql.update("""
update iam_application_group_t
set is_del=1,version=version+1,updated_time=utc_timestamp(6)
where tid=#{id}
""");
        }
        else {
            var code=(r.code||'').trim();
            var name=(r.name||'').trim();
            var sort=r.sortNo||0;
            if(name=='' || name.length()>100 || !code.matches('[A-Za-z][A-Za-z0-9_-]{1,63}')) {
                exit 400,'分组名称或编码不正确';
            }
            if(old!=null && code!=old.code) {
                exit 400,'已登记分组编码保持稳定';
            }
            if(old==null) {
                id=IdUtil.fastSimpleUUID();
            }
            if(sql.selectOne("""
select tid as id
from iam_application_group_t
where code=#{code}
    and tid<>#{id}
""")!=null) {
                exit 409,'分组编码已使用';
            }
            if(old==null) {
                sql.update("""
insert into iam_application_group_t(tid, code, name, sort_no)
values(#{id},#{code},#{name},#{sort})
""");
            }
            else {
                sql.update("""
update iam_application_group_t
set name=#{name},sort_no=#{sort},version=version+1,
        updated_time=utc_timestamp(6)
where tid=#{id}
""");
            }
        }
        // 记录操作主体、目标及结果，供审计与故障追踪。
        audit(ctx,'SUCCESS','groups:'+operation,id,null,{
            id:id
        });
        return {
            id:id
        };
    });

}));
