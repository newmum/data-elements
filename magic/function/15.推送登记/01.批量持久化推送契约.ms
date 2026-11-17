{
  "id": "push_registry_schema_write_v1",
  "groupId": "push_registry_function_group_v1",
  "name": "01.批量持久化推送契约",
  "path": "/persist",
  "description": "单源发布与节点同步共用的推送契约持久化。在租户事务内按历史版本与指纹批量更新，并维护字段有效状态。",
  "returnType": "Object",
  "parameters": [
    {
      "name": "tenantId",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String"
    },
    {
      "name": "publications",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import cn.hutool.core.util.IdUtil
import java.lang.Math
import java.util.ConcurrentModificationException
import tableWrite
import tenantRuntime

// 单源发布和节点同步共用此写入流程。收到预取的旧版本与字段定义后一次提交，冲突时整批回滚。
if (tenantRuntime.id() != tenantId) {
    throw new IllegalArgumentException('推送契约写入租户无效')
}
var str = value => value == null ? '' : (value + '').trim()
var value = (row, camel, snake) => row[camel] == null ? row[snake] : row[camel]
var schemaIds = publications.map(pub => pub.schemaId)
if (schemaIds.size() > 0) {
    // 已停用字段一并预取，重启登记时沿用历史字段 ID；全部写入共用一个租户事务。
    db.transaction(() => {
        var existingFields = db.table('data_push_schema_field_t').columns(['tid', 'schema_id', 'field_name']).where()
            .eq('tenant_id', tenantId).in('schema_id', schemaIds).select()
        var fieldsByKey = {}
        for (field in existingFields) {
            var fieldKey = str(value(field, 'schemaId', 'schema_id')) + ':' + str(value(field, 'fieldName', 'field_name'))
            fieldsByKey[fieldKey] = field
        }

        var now = new Date()
        var newSchemas = []
        var changedSchemas = []
        var expectedSchemas = []
        var newFields = []
        var changedFields = []
        for (pub in publications) {
            var record = {
                tid: pub.schemaId, datasource_id: pub.sourceId, table_id: pub.tableId,
                table_name: pub.tableName, schema_version: pub.version, schema_hash: pub.hash,
                status: 'PUBLISHED', delivery_config: pub.delivery, updated_time: now
            }
            if (pub.old == null) {
                record.tenant_id = tenantId
                record.is_del = 0
                record.published_time = now
                newSchemas.add(record)
            } else {
                changedSchemas.add(record)
                expectedSchemas.add({
                    tid: pub.schemaId, datasource_id: pub.sourceId, table_id: pub.tableId,
                    schema_version: value(pub.old, 'schemaVersion', 'schema_version'),
                    schema_hash: value(pub.old, 'schemaHash', 'schema_hash'), published_time: now
                })
            }
            for (field in pub.fields) {
                var old = fieldsByKey[pub.schemaId + ':' + field.name]
                var row = {
                    tid: old == null ? IdUtil.getSnowflakeNextIdStr() : old.tid,
                    schema_id: pub.schemaId, field_name: field.name, data_type: field.type,
                    required_flag: field.required ? 1 : 0, ordinal_position: field.ordinal, is_del: 0
                }
                if (old == null) {
                    row.tenant_id = tenantId
                    newFields.add(row)
                } else {
                    changedFields.add(row)
                }
            }
        }

        // 按关联键写入每批契约，任一记录失效即回滚；批次内不补查单条记录。
        if (newSchemas.size() > 0) {
            db.table('data_push_schema_t').batchInsert(newSchemas, 500)
        }
        if (changedSchemas.size() > 0) {
            // 以探查时的版本与指纹获取写锁，避免等待并发发布后覆盖其新版本。
            var guarded = tableWrite.batchUpdate('data_push_schema_t', tenantId, ['published_time'],
                ['tid', 'datasource_id', 'table_id', 'schema_version', 'schema_hash'], {is_del: 0}, expectedSchemas)
            if (guarded != expectedSchemas.size()) {
                throw new ConcurrentModificationException('已发布表契约发生变化，请刷新同步清单后重试')
            }
            var changed = tableWrite.batchUpdate('data_push_schema_t', tenantId,
                ['table_name', 'schema_version', 'schema_hash', 'status', 'delivery_config', 'updated_time'],
                ['tid', 'datasource_id', 'table_id'], {is_del: 0}, changedSchemas)
            if (changed != changedSchemas.size()) {
                throw new ConcurrentModificationException('已发布表契约发生变化，请刷新同步清单后重试')
            }
        }
        // 先停用旧字段，再批量恢复当前版本中仍存在的字段；保留被移除字段的历史 ID。
        db.table('data_push_schema_field_t').where()
            .eq('tenant_id', tenantId).in('schema_id', schemaIds).update({is_del: 1})
        if (newFields.size() > 0) {
            db.table('data_push_schema_field_t').batchInsert(newFields, 500)
        }
        if (changedFields.size() > 0) {
            // 字段上限为 10000，按有界 JDBC 批次提交，所有分块仍在同一事务内。
            var offset = 0
            while (offset < changedFields.size()) {
                var chunk = changedFields.subList(offset, Math.min(offset + 500, changedFields.size()))
                var changed = tableWrite.batchUpdate('data_push_schema_field_t', tenantId,
                    ['data_type', 'required_flag', 'ordinal_position', 'is_del'],
                    ['tid', 'schema_id', 'field_name'], {}, chunk)
                if (changed != chunk.size()) {
                throw new ConcurrentModificationException('已发布字段契约发生变化，请刷新同步清单后重试')
                }
                offset += 500
            }
        }
    })
}
return true
