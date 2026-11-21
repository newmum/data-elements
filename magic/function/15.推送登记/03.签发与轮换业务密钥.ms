{
  "id": "push_registry_credential_v1",
  "groupId": "push_registry_function_group_v1",
  "name": "03.签发与轮换业务密钥",
  "path": "/credential",
  "description": "签发和轮换入口的共用登记角色校验、控制库摘要写入与海通确认。仅生成当次密钥的请求可获得明文。",
  "returnType": "Object",
  "parameters": [
    {
      "name": "tenantId",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String"
    },
    {
      "name": "datasourceId",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String"
    },
    {
      "name": "rotate",
      "required": true,
      "dataType": "Boolean",
      "type": "java.lang.Boolean"
    }
  ],
  "options": []
}
================================
import '@/pushregistry/prepare' as prepare
import '@/pushregistry/persist' as persist
import cn.hutool.core.util.IdUtil
import cn.hutool.crypto.digest.DigestUtil
import cn.hutool.extra.spring.SpringUtil
import com.fasterxml.jackson.databind.ObjectMapper
import com.linewell.dataelement.model.common.BizException
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import java.util.Map
import org.springframework.dao.DuplicateKeyException
import dataScope
import identityRuntime
import tableRead
import tenantRuntime

// 签发和轮换共用登记权限、凭据摘要以及节点确认。调用方提供的租户字段没有归属选择权。
if (tenantRuntime.id() != tenantId) { throw new BizException(403, '登记数据源不存在或无权访问') }
var txt = (alias, name) => tableRead.column(alias, name, 'TEXT')
var num = (alias, name) => tableRead.column(alias, name, 'NUMBER')
var text = value => value == null ? '' : (value + '').trim()
var sourceQuery = tableRead.from('db_datasource_t', 'd', tenantId)
sourceQuery.where(tableRead.equal(txt('d', 'tid'), datasourceId))
sourceQuery.where(tableRead.equal(tableRead.coalesce([num('d', 'is_del'), 0]), 0))
var sources = sourceQuery.rows({tid: txt('d', 'tid'), org_id: txt('d', 'org_id'), created_by: txt('d', 'created_by'),
    pool: tableRead.column('d', 'pool_cfg', 'LOB')}, 1)
if (sources.isEmpty() || dataScope.visibleDataSourcesFor('MENU:2081000000000000002', sources).isEmpty()) {
    throw new BizException(403, '登记数据源不存在或无权访问')
}

var mapper = new ObjectMapper()
var object = value => {
    if (value instanceof Map) { return value }
    if (text(value) == '') { return {} }
    try {
        return mapper.readValue(text(value), Map) || {}
    } catch (error) {
        throw new IllegalArgumentException('数据源推送配置不是有效 JSON')
    }
}
var settings = object(sources[0].pool)
var modes = [settings.accessMode, settings.access_mode, settings.dataAccessMode, settings.data_access_mode]
    .map(item => text(item)).filter(item => item != '')
if (modes.size() == 0 || !['receive', 'push'].contains(modes[0].toLowerCase(Locale.ROOT))) {
    throw new IllegalArgumentException('该数据源不是数据推送方式')
}
var push = object(settings.pushDelivery)
if (push.containsKey('enabled') && !['true', '1', 'yes', 'on'].contains(text(push.enabled).toLowerCase(Locale.ROOT))) {
    throw new IllegalStateException('数据源推送功能尚未启用')
}
if (!datasourceId.matches('[A-Za-z0-9_-]{1,128}')) { throw new IllegalArgumentException('datasourceId 格式不正确') }
var clientId = 'push-' + datasourceId
if (clientId.length() > 128) { throw new IllegalArgumentException('relay client 格式不正确') }
var rootCause = error => {
    var cause = error
    while (!(cause instanceof DuplicateKeyException) && !(cause instanceof BizException)
        && cause.getCause() != null && cause.getCause() != cause) { cause = cause.getCause() }
    return cause
}
var bindings = () => db.table('data_push_relay_client_t').where()
    .eq('relay_client_id', clientId).eq('tenant_id', tenantId).eq('datasource_id', datasourceId)
var currentBinding = () => identityRuntime.control(() => bindings().selectOne())
var generate = () => {
    var key = Base64.getUrlEncoder().withoutPadding().encodeToString(new SecureRandom().generateSeed(32))
    return {key: key, hash: DigestUtil.sha256Hex(key)}
}
var result = {clientId: clientId, datasourceId: datasourceId, revealed: false, managedBy: 'platform', ready: false}
var binding = currentBinding()
if (binding == null) {
    // 依赖控制库的唯一绑定键仲裁首次访问。落库的是摘要，冲突方重读胜出的绑定且不回显明文。
    var credential = generate()
    try {
        binding = identityRuntime.control(() => {
            var now = new Date()
            var record = {tid: IdUtil.getSnowflakeNextIdStr(), relay_client_id: clientId, tenant_id: tenantId,
                datasource_id: datasourceId, api_key_sha256: credential.hash, previous_api_key_sha256: '',
                is_enable: 1, is_del: 0, created_time: now, updated_time: now}
            db.table('data_push_relay_client_t').insert(record)
            return record
        })
        result.revealed = true
        result.apiKey = credential.key
    } catch (error) {
        if (!(rootCause(error) instanceof DuplicateKeyException)) { throw error }
        binding = currentBinding()
        if (binding == null) { throw error }
    }
}
var enabled = binding.is_enable == null ? binding.isEnable : binding.is_enable
var deleted = binding.is_del == null ? binding.isDel : binding.is_del
if (enabled::int != 1 || deleted::int != 0) {
    throw new IllegalStateException('当前数据源的推送凭据已停用，请联系管理员')
}
var hash = text(binding.api_key_sha256 || binding.apiKeySha256)
var previous = text(binding.previous_api_key_sha256 || binding.previousApiKeySha256)
result.managedBy = hash == '' ? 'relay' : 'platform'

try {
    // 先提交业务库契约。随后通过协议客户端传送登记快照，快照只附加摘要。
    var plan = prepare(tenantId, datasourceId)
    persist(tenantId, plan.publications)
    var snapshot = plan.snapshot
    var relay = SpringUtil.getBean('haitongStandaloneRegistryClient')
    var sync = (digest, predecessor) => {
        if (digest != '') {
            snapshot.credential = {clientId: clientId, apiKeySha256: digest, previousSha256: predecessor}
        }
        relay.synchronize(datasourceId, snapshot)
    }
    if (rotate && !result.revealed && hash != '') {
        // 已保存版本可能仍待同步。先确认它，再按摘要比较并交换，避免并发轮换跳过节点当前版本。
        sync(hash, previous)
        var replacement = generate()
        var changed = identityRuntime.control(() => bindings()
            .eq('tid', binding.tid).eq('api_key_sha256', hash).eq('is_enable', 1).eq('is_del', 0)
            .update({api_key_sha256: replacement.hash, previous_api_key_sha256: hash, updated_time: new Date()}))
        if (changed != 1) { throw new BizException(409, '推送凭据已变化，请刷新后重试') }
        result.revealed = true
        result.apiKey = replacement.key
        previous = hash
        hash = replacement.hash
    }
    sync(hash, previous)
    if (rotate && hash == '') {
        // 已登记的历史调用方仍由独立海通签发，平台只同步表契约。
        var legacy = SpringUtil.getBean('haitongPushCredentialClient').issue(clientId, true)
        result.revealed = legacy.revealed()
        if (result.revealed) { result.apiKey = legacy.apiKey() }
    }
    result.ready = true
} catch (error) {
    var cause = rootCause(error)
    if (cause instanceof BizException || cause instanceof IllegalArgumentException || cause instanceof IllegalStateException) {
        // 保留提交后的本地摘要。重复签发只重试同步，显示一次的明文留在首次返回中。
        result.syncError = cause.getMessage()
    } else {
        throw error
    }
}
return result
