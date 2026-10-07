{
  "properties": {},
  "id": "e2e6dfb77ad95241aba08dd2a4c3fb53",
  "script": null,
  "groupId": "60b5f490ea45578aa23c7d583cb788ce",
  "name": "02.保存目录字典",
  "createTime": 1790615423659,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/save",
  "description": "校验请求字段、所属范围和版本；新建或更新目录字典及必要关联记录，写入审计并返回保存结果。仅接受目录字典允许的字段与值；写入控制库并校验版本，不允许传入任意 SQL 或表名。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：02.保存目录字典
// 路由：POST /idaas/dictionaries/save
// 业务说明：校验请求字段、所属范围和版本；新建或更新目录字典及必要关联记录，写入审计并返回保存结果。仅接受目录字典允许的字段与值；写入控制库并校验版本，不允许传入任意 SQL 或表名。
import db;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import java.lang.Number;
import java.lang.Double;
import java.lang.String;
import '@/idaas/guard' as guard;
import '@/idaas/mutate' as mutate;
import '@/idaas/audit' as audit;
import '@/idaas/field-values' as fieldValues;
import identityRuntime;
import request;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    var payload=body;
    // 请求参数与本接口业务处理。
    var entity = 'dictionaries';
    var operation = 'save';

    // 字典、扩展定义只接受明确列和白名单类型，不允许任意表名、SQL或凭据字段。
    // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
    var sql=db.normal();
    var p=payload||{};
    var r=p.record||p;
    var domain=r.domain||p.domain||'workforce';
    if(!['dictionaries','field-definitions'].contains(entity)) {
        exit 400,'配置类型不正确';
    }
    // 先校验当前会话的功能权限、身份域和应用管理范围。
    var ctx=guard(operation=='list'?'directory:read':'directory:write',null,domain);
    var fields=entity=='field-definitions';
    var table=fields?'iam_field_definition_t':'iam_dict_item_t';
    // 写入通过共享的幂等事务处理，并记录审计；版本冲突直接返回。
    return mutate(ctx,entity+':'+operation,p,()=>{
        var id=r.id||p.id;
        var old=id==null?null:sql.selectOne('select *,tid as id from '+table+' where tid=#{id} and is_del=0 for update');
        if(old==null && p.creating!=true) {
            exit 404,'配置不存在';
        }
        // 以当前版本校验修改目标，阻止旧页面覆盖已经变更的记录。
        if(old!=null && (old.identity_domain!=domain || old.version!=(r.version||p.version))) {
            exit 409,'配置身份域或版本已变更';
        }
        if(operation=='remove') {
            if(!fields) {
                var code=old.code;
                var referenced=sql.selectOne("""
select count(*) n
from iam_appointment_t
where is_del=0
    and (post_code=#{code}
         or position_code=#{code}
         or rank_code=#{code}
         or title_code=#{code})
""").n;
                if(referenced>0 || sql.selectOne("""
select count(*) n
from iam_org_t
where identity_domain=#{domain}
    and line_code=#{code}
    and is_del=0
""").n>0 || sql.selectOne("""
select count(*) n
from iam_dict_item_t
where parent_id=#{id}
    and is_del=0
""").n>0) {
                    exit 409,'字典仍被资料或下级引用';
                }
            }
            else {
                if(old.entity_type=='APPLICATION') {
                    exit 409,'应用扩展字段已迁入应用配置，请先迁移历史定义';
                }
                var target=old.entity_type=='SUBJECT'?'iam_subject_t':old.entity_type=='ORG'?'iam_org_t':'iam_resource_t';
                var jsonPath='$.'+old.field_key;
                if(sql.selectOne('select count(*) n from '+target+' where is_del=0 and json_contains_path(ext_json,\'one\',#{jsonPath})').n>0) {
                    exit 409,'扩展字段已有数据，请先迁移或停用';
                }
            }
            sql.update('update '+table+" set status='DISABLED',is_del=1,version=version+1,updated_time=utc_timestamp(6) where tid=#{id}");
            // 记录操作主体、目标及结果，供审计与故障追踪。
            audit(ctx,'SUCCESS',entity+':remove',id,null,{
                id:id
            });
            return {
                id:id
            };
        }
        var name=(r.name||'').trim();
        var code=(r.code||'').trim();
        var status=r.status=='disabled'?'DISABLED':'ACTIVE';
        if(name=='' || name.length()>100 || !code.matches('[A-Za-z][A-Za-z0-9_]{1,63}')) {
            exit 400,'名称或编码不正确';
        }
        if(old!=null && code!=(fields?old.field_key:old.code)) {
            exit 400,'编码保持稳定';
        }
        if(fields) {
            var typeMap={
                用户:'SUBJECT',机构:'ORG',资源:'RESOURCE'
            };
            var valueMap={
                文本:'STRING',数字:'NUMBER',日期:'DATE',枚举:'ENUM',布尔:'BOOLEAN'
            };
            var entityType=typeMap.get(r.subjectType)||r.subjectType;
            var valueType=valueMap.get(r.value)||r.value;
            if(!['SUBJECT','ORG','RESOURCE'].contains(entityType) || !['STRING','NUMBER','DATE','BOOLEAN','ENUM'].contains(valueType) || ['password','secret','token','credential','permissions','roles','tenant_id','is_admin'].contains(code.toLowerCase())) {
                exit 400,'扩展对象、类型或字段键不允许';
            }
            if(old!=null && (entityType!=old.entity_type || valueType!=old.value_type)) {
                exit 409,'已有字段类型和对象保持稳定';
            }
            var validation=r.validation||{};
            for(k in validation.keySet()) {
                if(!['maxLength','min','max','options','default'].contains(k)) {
                    exit 400,'校验规则不允许';
                }
            }
            if(validation.maxLength!=null && (!(validation.maxLength instanceof Number) || validation.maxLength<1 || validation.maxLength>4096 || validation.maxLength.doubleValue()!=validation.maxLength.longValue())) {
                exit 400,'文本长度须为1至4096的整数';
            }
            for(k in ['min','max']) {
                var value=validation.get(k);
                if(value!=null && (!(value instanceof Number) || !Double.isFinite(value.doubleValue()))) {
                    exit 400,'数值边界须为有限数字';
                }
            }
            if(validation.min!=null && validation.max!=null && validation.min>validation.max) {
                exit 400,'数值范围不正确';
            }
            if(validation.options!=null && (validation.options.size()>100 || validation.options.filter(o=>!(o instanceof String) || o.length()>256).size()>0)) {
                exit 400,'枚举选项须为文本';
            }
            if(valueType=='ENUM' && (validation.options==null || validation.options.size()==0 || validation.options.size()>100)) {
                exit 400,'枚举字段需要1至100项选项';
            }
            var encoded=JSONUtil.toJsonStr(validation);
            var required=r.required==true?1:0;
            var sensitive=r.sensitive==true?1:0;
            if(sensitive==1 && validation.containsKey('default')) {
                exit 400,'敏感字段不能在配置中保存默认值';
            }
            if(old==null) {
                if(sql.selectOne("""
select tid as id
from iam_field_definition_t
where identity_domain=#{domain}
    and entity_type=#{entityType}
    and field_key=#{code}
""")!=null) {
                    exit 409,'字段键已使用';
                }
                if(required==1 && !validation.containsKey('default')) {
                    var target=entityType=='SUBJECT'?'iam_subject_t':entityType=='ORG'?'iam_org_t':'iam_resource_t';
                    if(sql.selectOne('select count(*) n from '+target+' where is_del=0').n>0) {
                        exit 409,'已有资料时新增必填字段须提供迁移默认值';
                    }
                }
                id=IdUtil.fastSimpleUUID();
                sql.update("""
insert into iam_field_definition_t(tid,
                identity_domain, entity_type, field_key, label, value_type, required_flag, validation_json,
                sensitive_flag, status)
values(#{id},#{domain},#{entityType},#{code},#{name},#{valueType},#{required},#{encoded},#{sensitive},#{status})
""");
            }
            else {
                sql.update("""
update iam_field_definition_t
set label=#{name},required_flag=#{required},validation_json=#{encoded},
    sensitive_flag=#{sensitive},status=#{status},version=version+1,
        updated_time=utc_timestamp(6)
where tid=#{id}
""");
            }
            if(status=='ACTIVE') {
                var target=entityType=='SUBJECT'?'iam_subject_t':entityType=='ORG'?'iam_org_t':'iam_resource_t';
                var records=sql.select('select tid as id,ext_json from '+target+' where is_del=0'+(['SUBJECT','ORG'].contains(entityType)?' and identity_domain=#{domain}':" and app_id in (select tid from iam_application_t where kind='BUSINESS' and is_del=0)"));
                for(item in records) {
                    var existing=item.ext_json==null?{}:JSONUtil.parseObj(item.ext_json);
                    var adjusted=fieldValues('encode',entityType,domain,item.id,existing,existing);
                    var text=JSONUtil.toJsonStr(adjusted);
                    if(text!=JSONUtil.toJsonStr(existing)) {
                        sql.update('update '+target+' set ext_json=#{text},version=version+1,updated_time=utc_timestamp(6) where tid=#{item.id}');
                    }
                }
            }
        }
        else {
            var kindMap={
                岗位:'POST',职务:'POSITION',职级:'RANK',职称:'TITLE'
            };
            var category=r.category=='line'?'LINE':kindMap.get(r.value)||r.value;
            if(!['LINE','POST','POSITION','RANK','TITLE'].contains(category)) {
                exit 400,'字典类型不正确';
            }
            if(old!=null && category!=old.category) {
                exit 400,'字典类型保持稳定';
            }
            var parentId=r.parentId||null;
            var parent=parentId==null?null:sql.selectOne("""
select tid as id,parent_id
from iam_dict_item_t
where tid=#{parentId}
    and category=#{category}
    and identity_domain=#{domain}
    and is_del=0
    and status='ACTIVE'
""");
            if(parentId!=null && parent==null) {
                exit 400,'父字典无效';
            }
            var visited=[id];
            var cursor=parent;
            while(cursor!=null) {
                if(visited.contains(cursor.id)) {
                    exit 400,'字典层级不能循环';
                }
                visited.add(cursor.id);
                var next=cursor.parent_id;
                cursor=next==null?null:sql.selectOne("""
select tid as id,parent_id
from iam_dict_item_t
where tid=#{next}
""");
            }
            if(old==null) {
                if(sql.selectOne("""
select tid as id
from iam_dict_item_t
where identity_domain=#{domain}
    and category=#{category}
    and code=#{code}
""")!=null) {
                    exit 409,'字典编码已使用';
                }
                id=IdUtil.fastSimpleUUID();
                sql.update("""
insert into iam_dict_item_t(tid,
                identity_domain, category, code, name, parent_id, status)
values(#{id},#{domain},#{category},#{code},#{name},#{parentId},#{status})
""");
            }
            else {
                sql.update("""
update iam_dict_item_t
set name=#{name},parent_id=#{parentId},status=#{status},version=version+1,
        updated_time=utc_timestamp(6)
where tid=#{id}
""");
            }
        }
        audit(ctx,'SUCCESS',entity+':save',id,null,{
            id:id
        });
        return {
            id:id
        };
    });

}));
