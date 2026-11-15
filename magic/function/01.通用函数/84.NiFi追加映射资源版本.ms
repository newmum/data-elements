{
  "properties": {},
  "id": "nifi_mapping_save_version_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "84.NiFi追加映射资源版本",
  "path": "/nifiSaveMappingVersion",
  "description": "模板和字典快照共用租户授权、版本递增与并发重试。",
  "parameters": [
    {
      "name": "kind",
      "description": "资源类型",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "key",
      "description": "版本族名称",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "definition",
      "description": "授权定义",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "content",
      "description": "版本内容",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import cn.dev33.satoken.exception.SaTokenContextException
import cn.dev33.satoken.stp.StpUtil
import com.linewell.dataelement.mapping.core.MappingEngine
import com.linewell.dataelement.mapping.core.MappingException
import db
import java.nio.charset.StandardCharsets
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import org.springframework.dao.DuplicateKeyException
import org.ssssssss.script.exception.MagicScriptException
import org.ssssssss.script.exception.MagicScriptRuntimeException
import tableRead
import tenantRuntime

// @/common/nifiSaveMappingVersion 为模板及字典快照追加同租户下的不可变版本。
// 唯一约束决定版本并发顺序，冲突事务回滚后重新读取最大版本号。

if (!['TEMPLATE', 'DICTIONARY_SNAPSHOT'].contains(kind) || key == null || key.isBlank() || key.length() > 128) {
    throw new MappingException('INVALID_RESOURCE', '资源类型或名称无效')
}
if (content == null || !content.isObject()) {
    throw new MappingException('BUDGET_EXCEEDED', '版本内容无效或超过容量限制')
}
var encoded = content.toString()
if (encoded.getBytes(StandardCharsets.UTF_8).length > 1048576) {
    throw new MappingException('BUDGET_EXCEEDED', '版本内容无效或超过容量限制')
}
var tenantId = tenantRuntime.id()
var resourceId = UUID.randomUUID().toString().replace('-', '')
var hash = MappingEngine.hash(content)
var actor = 'system'
try {
    if (StpUtil.isLogin()) {
        actor = StpUtil.getLoginIdAsString()
    }
} catch (failure) {
    var cause = failure
    while ((cause instanceof MagicScriptException || cause instanceof MagicScriptRuntimeException) && cause.getCause() != null) {
        cause = cause.getCause()
    }
    if (!(cause instanceof SaTokenContextException)) {
        throw failure
    }
}

for (attempt in [1, 2, 3]) {
    try {
        return db.transaction(() => {
            // 使用可信租户、资源类型和名称读取版本号，不从请求读取归属。
            var query = tableRead.from('data_access_mapping_resource_t', 'r', tenantId)
            query.where(tableRead.equal(tableRead.column('r', 'resource_kind', 'TEXT'), kind))
            query.where(tableRead.equal(tableRead.column('r', 'resource_key', 'TEXT'), key))
            var maximum = query.grouped({version: tableRead.max(tableRead.column('r', 'version_no', 'NUMBER'))}, [])[0].version
            var version = maximum == null ? 1L : maximum.longValue() + 1L
            db.normal().table('data_access_mapping_resource_t').primary('tid').withBlank().insert({
                tid: resourceId, tenant_id: tenantId, resource_kind: kind, resource_key: key, version_no: version,
                resource_hash: hash, definition_json: definition.toString(), resource_content: encoded,
                created_by: actor, created_time: Timestamp.from(Instant.now())
            })
            return {id: resourceId, kind: kind, key: key, version: version, hash: hash}
        })
    } catch (failure) {
        var cause = failure
        while ((cause instanceof MagicScriptException || cause instanceof MagicScriptRuntimeException) && cause.getCause() != null) {
            cause = cause.getCause()
        }
        if (!(cause instanceof DuplicateKeyException) || attempt == 3) {
            throw failure
        }
    }
}
