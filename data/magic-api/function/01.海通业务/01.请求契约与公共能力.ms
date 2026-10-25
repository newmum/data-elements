{"id":"haitong-function-common","groupId":"haitong-business-functions","name":"01.请求契约与公共能力","path":"/common","parameters":[],"description":"统一请求契约、身份获取、分页边界及通用技术组件引用；不从请求中取得租户或目标连接。"}
================================
import java.util.UUID
import java.util.List
// 公共边界：当前认证身份来自过滤器；SQL使用已绑定的海通库，连接配置不进入响应。
import magicHttp
import com.haitong.api.AuthenticationFilter
import com.haitong.core.RelayException
import com.haitong.core.Rules
import java.util.Map
import java.util.ArrayList
var bean = name => magicHttp.bean(name)
var fail = (status,code,message) => { throw new RelayException(status,code,message) }
var text = value => value == null ? '' : value + ''
var required = (value,label) => {
    if (!(value instanceof String) || value.trim() == '') { fail(422,'INVALID_BODY',label+' 必填') }
    return value
}
var integer = (value,fallback) => {
    if (value == null || text(value) == '') { return fallback }
    if (!text(value).matches('-?[0-9]+')) { fail(400,'INVALID_REQUEST','分页或数字参数不合法') }
    try { return text(value)::long } catch(e) { fail(400,'INVALID_REQUEST','数字参数超出范围') }
}
var query = (req,name,fallback) => {
    var values=req.getParameterValues(name)
    return values == null || values.length == 0 ? fallback : values[0]
}
var bounds=(limit,offset,max)=>{if(limit<1 || limit>max || offset<0 || offset>1000000){fail(422,'INVALID_BODY','分页参数超出范围')}}
var unknown=(input,allowed)=>{for(k in input.keySet()){if(!allowed.contains(k)){fail(422,'INVALID_BODY','请求包含未定义字段，租户和投递地址由服务端确定')}}}
var media=(req,expected)=>{
    var value=text(req.getContentType()).split(';')[0].trim().toLowerCase()
    if(value != expected && !(expected=='multipart/form-data' && value.startsWith('multipart/'))){fail(415,'UNSUPPORTED_MEDIA_TYPE','请检查 Content-Type')}
}
return {bean:bean,fail:fail,text:text,required:required,integer:integer,query:query,bounds:bounds,unknown:unknown,media:media,
    caller:req => AuthenticationFilter.identity(req),
    p:bean('relay-com.haitong.config.RelayProperties'),jdbc:bean('jdbcTemplate'),tx:bean('transactionTemplate'),
    json:bean('json'),http:bean('magicHttp'),dialect:bean('dialect'),
    now:()=>bean('relayRepository').now(),uuid:value=>{try{return UUID.fromString(text(value)).toString()}catch(e){fail(400,'INVALID_REQUEST','ID 不合法')}},
    list:value=>{if(!(value instanceof List)){fail(422,'INVALID_BODY','必须为数组')}return value},
    object:value=>{if(!(value instanceof Map)){fail(422,'INVALID_BODY','必须为JSON对象')}return value},
    key:value=>Rules.key(value),hash:value=>Rules.sha256(value),
    absolute:path=>bean('relay-com.haitong.config.RelayProperties').getGateway().getPublicBaseUrl().replaceAll('/+$','')+path}
