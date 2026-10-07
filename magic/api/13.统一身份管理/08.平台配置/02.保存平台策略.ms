{
  "properties": {},
  "id": "b6989040ccbe588abe6c9169ad23ee82",
  "script": null,
  "groupId": "b131d8f0696755519b08adfc5918a7c7",
  "name": "02.保存平台策略",
  "createTime": 1790612165860,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/save",
  "description": "校验配置类别、允许字段和版本后写入控制库平台策略，记录操作者及审计；不自动覆盖租户配置。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：02.保存平台策略
// 路由：POST /idaas/settings/save
// 业务说明：校验配置类别、允许字段和版本后写入控制库平台策略，记录操作者及审计；不自动覆盖租户配置。
import db;
import '@/idaas/guard' as guard;
import '@/idaas/mutate' as mutate;
import '@/idaas/audit' as audit;
import cn.hutool.json.JSONUtil;
import java.lang.Number;
import identityApi;
import cn.hutool.core.util.IdUtil;
import identityRuntime;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    // 请求参数与本接口业务处理。
    var operation = 'save';
    var payload = body||{};

    // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
    var sql=db.normal();
    var p=payload||{};
    // 先校验当前会话的功能权限、身份域和应用管理范围。
    var ctx=guard(operation=='read'?'settings:read':'settings:write',null,'workforce');
    // 写入通过共享的幂等事务处理，并记录审计；版本冲突直接返回。
    return mutate(ctx,'settings:save',p,()=>{
        var category=p.category;
        var key=p.configKey;
        var values=p.values||{};
        if(!['SECURITY','BRANDING','API_ACCESS','SSO'].contains(category) || (category=='SECURITY' && key!='password') || (category=='BRANDING' && key!='brand') || (category=='API_ACCESS' && key!='access') || (category=='SSO' && key!='session')) {
            exit 400,'此策略尚未开通';
        }
        var old=sql.selectOne("""
select *,tid as id
from iam_setting_t
where scope_kind='GLOBAL'
    and scope_key='global'
    and category=#{category}
    and config_key=#{key}
    and is_del=0
    for update
""");
        // 以当前版本校验修改目标，阻止旧页面覆盖已经变更的记录。
        if(old==null && p.version!=null || old!=null && old.version!=p.version) {
            exit 409,'配置已被修改，请刷新';
        }
        var allowed=category=='SECURITY'?['minLength','lockThreshold','lockMinutes','forceChange','passwordBlacklist','mfaRequired']:category=='API_ACCESS'?['apiWhitelist','ipBlacklist','apiRateLimit']:category=='SSO'?['accessTtl','refreshTtl','idleMinutes','allowConcurrent']:['title','subtitle','logoUrl'];
        for(k in values.keySet()) {
            if(!allowed.contains(k)) {
                exit 400,'配置包含尚未开通的字段';
            }
        }
        if(category=='SECURITY') {
            if(!(values.minLength instanceof Number) || !(values.lockThreshold instanceof Number) || !(values.lockMinutes instanceof Number)
            || values.minLength.doubleValue()!=values.minLength.intValue() || values.lockThreshold.doubleValue()!=values.lockThreshold.intValue()
            || values.lockMinutes.doubleValue()!=values.lockMinutes.intValue()) {
                exit 400,'安全策略数值须填写完整整数';
            }
            if(values.minLength<8 || values.minLength>64 || values.lockThreshold<3 || values.lockThreshold>20 || values.lockMinutes<5 || values.lockMinutes>1440
            || values.passwordBlacklist==null || values.passwordBlacklist.length()>10000 || ![true,false].contains(values.forceChange)) {
                exit 400,'安全策略范围不正确';
            }
            if(values.mfaRequired==true && sql.selectOne("""
select count(*) n
from iam_operator_t o
where o.status='ACTIVE'
    and o.is_del=0
    and not exists
        (select 1
         from iam_credential_t c
         where c.owner_kind='OPERATOR'
             and c.owner_id=o.tid
             and c.credential_type='TOTP'
             and c.status='ACTIVE')
""").n>0) {
                exit 409,'请先为全部启用的平台操作账号绑定动态口令，再开启强制策略';
            }
        }
        else if(category=='API_ACCESS') {
            if(!(values.apiRateLimit instanceof Number) || values.apiRateLimit.doubleValue()!=values.apiRateLimit.intValue() || values.apiRateLimit<1 || values.apiRateLimit>100000 || (values.apiWhitelist||'').length()>4000 || (values.ipBlacklist||'').length()>4000) {
                exit 400,'接口配额或来源规则不正确';
            }
            for(list in [values.apiWhitelist||'',values.ipBlacklist||'']) {
                for(rule in list.split('\n')) {
                    if(rule.trim()!='') {
                        try {
                            identityApi.matchesAddress(rule.trim(),'127.0.0.1');
                        }
                        catch(e) {
                            exit 400,'接口来源须使用有效IP或CIDR';
                        }
                    }
                }
            }
        }
        else if(category=='SSO') {
            for(k in ['accessTtl','refreshTtl','idleMinutes']) {
                var value=values.get(k);
                if(!(value instanceof Number) || value.doubleValue()!=value.intValue()) {
                    exit 400,'会话有效期须为完整整数';
                }
            }
            if(values.accessTtl<60 || values.accessTtl>3600 || values.refreshTtl<values.accessTtl || values.refreshTtl>604800 || values.idleMinutes<5 || values.idleMinutes>30 || ![true,false].contains(values.allowConcurrent)) {
                exit 400,'会话策略范围不正确';
            }
        }
        else if(values.title==null || values.title.trim()=='' || values.title.length()>30 || (values.subtitle||'').length()>100
        || ((values.logoUrl||'')!='' && !values.logoUrl.matches('https://[^\\s]+'))) {
            exit 400,'品牌配置不正确';
        }
        var encoded=JSONUtil.toJsonStr(values);
        if(old==null) {
            var id=IdUtil.fastSimpleUUID();
            sql.update("""
insert into iam_setting_t(tid,
                scope_kind, scope_key, category, config_key, value_json, status, created_by)
values(#{id},'GLOBAL','global',#{category},#{key},#{encoded},'ACTIVE',#{ctx.userId})
""");
            old={
                id:id
            };
        }
        else {
            sql.update("""
update iam_setting_t
set value_json=#{encoded},version=version+1,updated_time=utc_timestamp(6),
        updated_by=#{ctx.userId}
where tid=#{old.id}
""");
        }
        // 记录操作主体、目标及结果，供审计与故障追踪。
        audit(ctx,'SUCCESS','settings:save',old.id,null,{
            category:category,key:key
        });
        return {
            id:old.id
        };
    });

}));
