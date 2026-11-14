{
  "properties": {},
  "id": "22d26749951448b2875b1ea9e823b342",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "28.执行服务编排HTTP链路",
  "path": "/serviceFlowHttp",
  "description": "校验并执行触发器、登记HTTP服务与响应组成的有界线性流程，允许引用前置节点结果。",
  "parameters": [{"name":"options","required":true,"dataType":"Object","type":"java.lang.Object"}],
  "options": []
}
================================
import '@/common/serviceHttp' as serviceHttp
import '@/common/serviceHttpPreview' as preview
import org.springframework.core.env.Environment as environment
import cn.hutool.json.JSONUtil
import java.lang.System
import java.lang.String
import java.util.Map
import java.util.List
import dataScope
import tenantRuntime

// 功能：校验和执行受控HTTP编排。绑定只读取输入和上游结果，不执行客户端提交的脚本。
var text = value => value == null ? '' : value + ''
var flow = options.flow
if (!(flow instanceof Map) || !(flow.nodes instanceof List) || !(flow.edges instanceof List)
    || flow.nodes.size() < 3 || flow.nodes.size() > 10 || flow.edges.size() != flow.nodes.size()-1
    || JSONUtil.toJsonStr(flow).length() > 131072) { exit 400,'请配置包含HTTP节点的线性流程，最多10个节点' }
var sensitive = key => text(key).matches('(?i).*(authorization|token|password|passwd|secret|api.?key|credential|cookie).*')
var bound = value => value instanceof String && value.matches('(?:(?i:Bearer|Basic) )?\\{\\{[A-Za-z_][A-Za-z0-9_.]{0,159}\\}\\}')
var guardSecrets
guardSecrets = (value,depth) => {
    if (depth > 12) { exit 400,'请求模板层级过深' }
    if (value instanceof Map) {
        for (key in value.keySet()) {
            var item = value[key]
            if (sensitive(key) && !bound(item)) { exit 400,'认证字段请使用变量绑定或凭据引用' }
            guardSecrets(item,depth+1)
        }
    } else if (value instanceof List) {
        for (item in value) { guardSecrets(item,depth+1) }
    }
}
var nodes = {}
var next = {}
var incoming = {}
var trigger = null
var response = null
var ids = []
var outputs = {}
for (node in flow.nodes) {
    if (!(node instanceof Map) || !text(node.id).matches('[A-Za-z0-9_-]{1,128}') || nodes[node.id] != null
        || !(node.data instanceof Map)) { exit 400,'流程节点标识或配置无效' }
    nodes[node.id] = node
    if (node.type == 'trigger') {
        if (trigger != null) { exit 400,'流程只能有一个API触发器' }
        trigger = node
    } else if (node.type == 'response') {
        if (response != null || !['wrap','var'].contains(node.data.mode || 'wrap')) { exit 400,'流程只支持一个标准响应或变量响应' }
        response = node
    } else if (node.type == 'http-call') {
        var id = text(node.data.serviceId)
        var output = text(node.data.outputVar || 'httpResult')
        if (!id.matches('[A-Za-z0-9_-]{1,32}')) { exit 400,'HTTP节点请填写已登记服务ID' }
        if (!output.matches('[A-Za-z_][A-Za-z0-9_]{0,63}') || output == 'input' || outputs[output] == true) {
            exit 400,'每个HTTP节点须使用不同的输出变量名'
        }
        outputs[output] = true
        if (!ids.contains(id)) { ids.add(id) }
        var template = text(node.data.body).trim()
        if (template != '') {
            if (!JSONUtil.isTypeJSON(template)) { exit 400,'HTTP节点的请求体须使用JSON模板' }
            try { guardSecrets(JSONUtil.parse(template),0) } catch (e) { exit 400,'请求体模板无效或含明文凭据' }
        }
        for (header in node.data.headers || []) {
            if (!(header instanceof Map) || (sensitive(header.name || header.key) && !bound(header.value))) {
                exit 400,'认证请求头须使用变量绑定或凭据引用'
            }
        }
        var ref = text(node.data.credentialRef)
        if (ref != '' && !ref.matches('[A-Za-z0-9_-]{1,64}')) { exit 400,'凭据引用无效' }
    } else { exit 400,'当前服务执行支持API触发器、HTTP调用和响应节点' }
}
if (trigger == null || response == null) { exit 400,'需要API触发器和响应节点' }
for (edge in flow.edges) {
    if (!(edge instanceof Map) || nodes[edge.source] == null || nodes[edge.target] == null
        || next[edge.source] != null || incoming[edge.target] != null || edge.target == trigger.id
        || edge.source == response.id) { exit 400,'当前服务执行需要无分支的单一调用链' }
    next[edge.source] = edge.target
    incoming[edge.target] = edge.source
}
var ordered = []
var cursor = trigger.id
while (cursor != null && nodes[cursor] != null && ordered.size() < flow.nodes.size()) {
    ordered.add(nodes[cursor])
    cursor = next[cursor]
}
if (ordered.size() != flow.nodes.size() || ordered[ordered.size()-1].id != response.id || cursor != null) {
    exit 400,'请把所有节点依次连到响应'
}
// 关联服务一次批量读，逐ID验证服务记录的数据范围。
var tenantId = tenantRuntime.id()
var services = db.table('api_info_t').where().eq('tenant_id',tenantId).eq('is_del',0).in('tid',ids).select()
var visible = dataScope.visibleDataSources(services)
var byId = {}
for (service in visible) {
    byId[service.tid] = service
}
if (byId.size() != ids.size()) { exit 404,'流程引用的服务不存在或不在数据范围内' }
var method = text(trigger.data.method || 'GET').toUpperCase()
if (!['GET','POST','PUT','PATCH','DELETE'].contains(method)) { exit 400,'流程入口的请求方式无效' }
if (options.validateOnly == true) { return {method:method,serviceIds:ids} }

var input = options.input == null ? {} : options.input
if (!(input instanceof Map) || JSONUtil.toJsonStr(input).length() > 65536) { exit 400,'流程输入须为64 KiB以内的对象' }
var variables = {input:input}
var readPath = path => {
    var value = variables
    for (key in path.split('\\.')) {
        if (!(value instanceof Map) || !value.containsKey(key)) { return null }
        value = value[key]
    }
    return value
}
var resolve
resolve = (value,depth) => {
    if (depth > 12) { exit 400,'请求模板层级过深' }
    if (value instanceof Map) {
        var result = {}
        for (key in value.keySet()) { result[key] = resolve(value[key],depth+1) }
        return result
    }
    if (value instanceof List) {
        var result = []
        for (item in value) { result.add(resolve(item,depth+1)) }
        return result
    }
    if (bound(value)) {
        var start = value.indexOf('{{')
        var mapped = readPath(value.substring(start+2,value.length()-2))
        if (mapped == null) { exit 400,'变量绑定没有值，请检查输入及上游响应路径' }
        return start == 0 ? mapped : value.substring(0,start) + text(mapped)
    }
    return value
}
var started = System.currentTimeMillis()
var trace = []
var failure = ''
var last = null
var step = (node,status,elapsed,output) => {
    trace.add({nodeId:node.id,nodeType:node.type,name:node.name || node.type,status:status,ms:elapsed,input:{},output:output,log:[]})
}
step(trigger,'success',0,{})
for (node in ordered) {
    if (node.type != 'http-call') { continue }
    if (failure != '') { step(node,'skipped',0,{}); continue }
    var data = node.data
    var source = byId[data.serviceId]
    var headers = {}
    var query = {}
    for (header in data.headers || []) { headers[text(header.name || header.key)] = resolve(header.value,0) }
    for (param in data.query || []) { query[text(param.name || param.key)] = resolve(param.value,0) }
    var payload = text(data.body).trim() == '' ? null : resolve(JSONUtil.parse(data.body),0)
    var credentialRef = text(data.credentialRef)
    if (credentialRef != '') {
        var configured = text(environment.getProperty('flowserve.http.credentials.'+tenantId+'.'+credentialRef,''))
        if (configured == '') { exit 409,'服务凭据引用尚未配置' }
        var credential = JSONUtil.parseObj(configured)
        if (text(credential.serviceId) != text(source.tid)) { exit 403,'服务凭据与调用目标不匹配' }
        for (name in (credential.headers || {}).keySet()) { headers[name] = credential.headers[name] }
        if (credential.payload != null) {
            if (credential.payload instanceof Map && (payload == null || payload instanceof Map)) {
                payload = {...(payload || {}),...credential.payload}
            } else { payload = credential.payload }
        }
    }
    var request = {address:source.address || source.uri,method:source.method,headers:headers,query:query,payload:payload}
    if (System.currentTimeMillis() - started > 25000) {
        failure = '流程执行超时'
        step(node,'skipped',0,{})
        continue
    }
    var result = serviceHttp(request)
    variables[data.outputVar || 'httpResult'] = result
    var safe = preview({response:{...result},request:request})
    // 追踪只显示调用结果摘要，正文由响应节点显式返回。
    step(node,result.ok?'success':'fail',result.durationMs || 0,
        {httpStatus:safe.status,businessCode:safe.businessCode,kind:safe.kind})
    if (!result.ok || result.truncated == true) { failure = '上游接口调用未成功' }
    last = {raw:result,safe:safe}
}
var output = null
if (failure == '') {
    var source = text(response.data.source).trim()
    if (source == '') { output = last.safe.body }
    else {
        if (!source.matches('[A-Za-z_][A-Za-z0-9_.]{0,159}') || source.startsWith('input.')
            || source.split('\\.')[0] != text(ordered[ordered.size()-2].data.outputVar || 'httpResult')
            || sensitive(source)) { exit 400,'响应只允许引用最后一个HTTP节点的非认证结果' }
        variables[source.split('\\.')[0]] = last.safe
        output = readPath(source)
        if (output == null) { failure = '响应变量路径不存在' }
    }
}
step(response,failure==''?'success':'skipped',0,failure==''?{body:output}:{})
return {success:failure=='',totalMs:System.currentTimeMillis()-started,trace:trace,response:output,error:failure}
