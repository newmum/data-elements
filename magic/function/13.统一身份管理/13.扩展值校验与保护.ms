{
  "properties": {},
  "id": "8cbd7ac406ed58a2be17e3e76cbefad5",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "13.扩展值校验与保护",
  "createTime": 1790615423659,
  "updateTime": 1790949884838,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/field-values",
  "description": "按已登记字段定义验证扩展值格式和长度，过滤不允许写入的保留字段。",
  "parameters": [
    {
      "name": "operation",
      "value": null,
      "description": "本次执行的业务操作",
      "required": false,
      "dataType": "Object",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "entityType",
      "value": null,
      "description": "扩展字段所属对象类别",
      "required": false,
      "dataType": "Object",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "identityDomain",
      "value": null,
      "description": "人员或公众身份域",
      "required": false,
      "dataType": "Object",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "objectId",
      "value": null,
      "description": "被操作对象标识",
      "required": false,
      "dataType": "Object",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "values",
      "value": null,
      "description": "待校验的扩展字段值",
      "required": false,
      "dataType": "Object",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "prior",
      "value": null,
      "description": "修改前的扩展字段值",
      "required": false,
      "dataType": "Object",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ],
  "returnType": null,
  "mappingPath": null
}
================================
// 共享函数：13.扩展值校验与保护
// 业务说明：按已登记字段定义验证扩展值格式和长度，过滤不允许写入的保留字段。
// 函数输入：operation：本次执行的业务操作；entityType：扩展字段所属对象类别；identityDomain：人员或公众身份域；objectId：被操作对象标识；values：待校验的扩展字段值；prior：修改前的扩展字段值。
// 返回与副作用：按已登记字段定义验证扩展值格式和长度，过滤不允许写入的保留字段。
import db;
import identityTransport;
import cn.hutool.json.JSONUtil;
import java.lang.Number;
import java.lang.String;
import java.lang.Boolean;
import java.util.Map;
import java.time.LocalDate;

// 资料扩展只采用已定义字段。敏感值使用框架原语和对象绑定AAD，读取只返回掩码。
var sql=db.normal();
var domain=identityDomain;
var incoming=values||{};
var previous=prior||{};
var definitions=sql.select("""
select *,tid as id
from iam_field_definition_t
where identity_domain=#{domain}
    and entity_type=#{entityType}
    and status='ACTIVE'
    and is_del=0
""");
// 目录分页读取共享一次字段定义；只输出当前页已获授权对象的掩码值。
if(operation=='view-many') {
    var views={};
    for(item in incoming) {
        var view={};
        var source=item.values||{};
        for(d in definitions) {
            var value=source.get(d.field_key);
            if(value!=null) {
                view.put(d.field_key,d.sensitive_flag==1?'[受保护]':value);
            }
        }
        views.put(item.id,view);
    }
    return views;
}
var known=definitions.map(d=>d.field_key);
var result={};
for(k in incoming.keySet()) {
    if(operation!='view' && !known.contains(k) && (!previous.containsKey(k) || JSONUtil.toJsonStr(previous.get(k))!=JSONUtil.toJsonStr(incoming.get(k)))) {
        exit 400,'扩展字段未定义或已停用';
    }
}
for(d in definitions) {
    var key=d.field_key;
    var value=incoming.containsKey(key)?incoming.get(key):previous.get(key);
    var rule=d.validation_json==null?{}:JSONUtil.parseObj(d.validation_json);
    var aad=domain+'/'+entityType+'/'+objectId+'/'+key;
    if(operation=='view') {
        if(value!=null) {
            result.put(key,d.sensitive_flag==1?'[受保护]':value);
        }
        continue;
    }
    if(value=='[受保护]' || (value!=null && value instanceof Map)) {
        var original=previous.get(key);
        if(original==null || (value!='[受保护]' && JSONUtil.toJsonStr(value)!=JSONUtil.toJsonStr(original))) {
            exit 400,'不能提交受保护字段存储格式';
        }
        if(original instanceof Map && original.containsKey('encrypted')) {
            value=JSONUtil.parseObj(identityTransport.unprotectValue(original.encrypted,aad)).value;
        }
        else {
            value=original;
        }
    }
    if(value==null && rule.containsKey('default')) {
        value=rule.get('default');
    }
    if(value==null || value=='') {
        if(d.required_flag==1) {
            exit 400,'必填扩展字段缺失';
        }
        if(value!=null) {
            result.put(key,value);
        }
        continue;
    }
    if(d.value_type=='STRING' && !(value instanceof String)) {
        exit 400,'扩展字段须为文本';
    }
    if(d.value_type=='NUMBER' && !(value instanceof Number)) {
        exit 400,'扩展字段须为数字';
    }
    if(d.value_type=='BOOLEAN' && !(value instanceof Boolean)) {
        exit 400,'扩展字段须为布尔值';
    }
    if(d.value_type=='DATE') {
        try {
            LocalDate.parse(''+value);
        }
        catch(e) {
            exit 400,'扩展日期不正确';
        }
    }
    if(d.value_type=='ENUM' && (rule.options==null || !rule.options.contains(value))) {
        exit 400,'扩展字段不在枚举范围';
    }
    if((''+value).length()>(rule.maxLength||4096) || (d.value_type=='NUMBER' && ((rule.min!=null && value<rule.min) || (rule.max!=null && value>rule.max)))) {
        exit 400,'扩展字段值超出范围';
    }
    result.put(key,d.sensitive_flag==1?{
        encrypted:identityTransport.protectValue(JSONUtil.toJsonStr({
            value:value
        }),aad)
    }
    :value);
}
// 停用定义保留历史值，不对调用方开放写入或解密。
if(operation!='view') {
    for(k in previous.keySet()) {
        if(!known.contains(k)) {
            result.put(k,previous.get(k));
        }
    }
}
return result;
