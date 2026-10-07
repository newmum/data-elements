{
  "properties": {},
  "id": "43af23446f825dd09c5c88784e8d0425",
  "script": null,
  "groupId": "07899fe85f4d59fd90de0507c7f4d188",
  "name": "11.分页查询可下发对象",
  "createTime": 1791193400395,
  "updateTime": 1791193400395,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/objects",
  "description": "按目标应用和 sync:write 权限分页列出中央人员或机构，供手动下发选择。",
  "parameters": [],
  "method": "GET",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：按应用和当前操作人权限分页查询可手动下发的对象
// 路由：GET /idaas/provision/objects
import db;
import identityRuntime;
import request;
import java.lang.Long;
import '@/idaas/api-call' as apiCall;
import '@/idaas/guard' as guard;
import '@/idaas/context' as context;

return identityRuntime.control(()=>apiCall(()=>{
    var param=(key,fallback)=>{
        var values=request.getValues(key);
        return values==null || values.size()==0?fallback:values[0];
    };
    var configId=param('configId',null);
    var kind=param('kind',null);
    var pageText=param('page','1');
    var sizeText=param('size','20');
    var q=(param('q','')||'').trim();
    if(configId==null || !['user','org'].contains(kind) || q.length()>100
        || !pageText.matches('[0-9]{1,5}') || !sizeText.matches('[0-9]{1,3}')) {
        exit 400,'对象查询参数不正确';
    }
    var page=Long.parseLong(pageText);
    var size=Long.parseLong(sizeText);
    if(page<1 || size<1 || size>100) {
        exit 400,'分页范围不正确';
    }
    var sql=db.normal();
    context();
    var config=sql.selectOne("""
select app_id from iam_sync_config_t
where tid=#{configId} and is_del=0
""");
    if(config==null) {
        exit 404,'下发配置不存在';
    }
    var appId=config.app_id;
    guard('sync:write',appId,'workforce');
    var keyword='%'+q+'%';
    var offset=(page-1)*size;
    var filter=kind=='user'?"""
from iam_subject_app_t sa
join iam_subject_t t on t.tid=sa.subject_id
where sa.app_id=#{appId} and t.identity_domain='workforce' and t.is_del=0
  and (t.name like #{keyword} or t.account_hint like #{keyword})
""":"""
from iam_org_t t
where t.identity_domain='workforce' and t.is_del=0 and t.name like #{keyword}
  and exists (
    select 1 from iam_app_org_t ao
    join iam_org_t root on root.tid=ao.org_id
    where ao.app_id=#{appId} and ao.status='ACTIVE'
      and (t.tid=root.tid or (ao.include_children=1 and t.path like concat(root.path,'%')))
  )
""";
    var total=sql.selectOne('select count(*) total '+filter).total;
    var rows=sql.select("""
select t.tid as id,t.name,t.version,t.status,
  """+(kind=='user'?'t.account_hint as account':'t.code')+"""
"""+filter+" order by t.tid limit #{size} offset #{offset}");
    return {list:rows.map(row=>({
        id:row.id,name:row.name,version:row.version,
        status:row.status=='ACTIVE'?'enabled':'disabled',
        account:kind=='user'?row.account:null,code:kind=='org'?row.code:null
    })),total:total,page:page,size:size};
}));
