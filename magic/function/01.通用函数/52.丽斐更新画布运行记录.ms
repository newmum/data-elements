{
  "properties": {},
  "id": "nifi_canvas_runtime_write_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "52.丽斐更新画布运行记录",
  "path": "/nifiWriteRuntime",
  "description": "为部署、候选切换、启动、停止和卸载按租户更新画布的运行记录。",
  "parameters": [
    {"name":"pipelineId","description":"当前流程标识","required":true,"dataType":"String","type":"java.lang.String"},
    {"name":"tenantId","description":"当前可信行业租户","required":true,"dataType":"String","type":"java.lang.String"},
    {"name":"changes","description":"允许更新的状态、运行组、部署记录及必要的映射设计变更","required":true,"dataType":"Object","type":"java.lang.Object"}
  ],
  "options": []
}
================================
import '@/common/nifiDecodeCanvas' as decodeCanvas
import com.fasterxml.jackson.databind.ObjectMapper as mapper
import com.linewell.dataelement.integration.nifi.canvas.pipeline.BatchSchedulePolicy
import com.linewell.dataelement.integration.nifi.canvas.pipeline.DslHasher
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline
import java.lang.IllegalStateException
import java.lang.Math
import java.lang.System
import java.util.ArrayList
import java.util.Objects
import org.springframework.http.HttpStatus
import org.springframework.transaction.support.TransactionTemplate as transactions
import org.springframework.web.server.ResponseStatusException
import tableWrite

// @/common/nifiWriteRuntime 为部署、候选切换和启停清理更新当前行业租户的画布运行记录。
// 调用方持有共享 NiFi 锁。单次数据库写入在当前事务内锁行，运行变更不会覆盖并发编辑的设计。

return transactions.execute(transaction => {
    var table = 'nifi_pipeline_t'
    var row = tableWrite.lockRow(table, tenantId, 'id', pipelineId,
        ['id', 'name', 'description', 'created_at', 'updated_at', 'dsl_json', 'nifi_process_group_id', 'status',
            'last_deployed_hash', 'last_deployed_at', 'last_stopped_at', 'node_mapping_json', 'last_bulletin_id'], {is_del: 0})
    var current = row == null ? null : decodeCanvas({id: row.id, name: row.name, description: row.description,
        createdAt: row.created_at, updatedAt: row.updated_at, dsl: row.dsl_json, processGroupId: row.nifi_process_group_id,
        status: row.status, deployedHash: row.last_deployed_hash, deployedAt: row.last_deployed_at,
        stoppedAt: row.last_stopped_at, mapping: row.node_mapping_json, lastBulletinId: row.last_bulletin_id})
    if (current == null) {
        throw new IllegalStateException('Pipeline not found: ' + pipelineId)
    }
    if (changes.containsKey('expectedDesignHash') && !Objects.equals(changes.expectedDesignHash, current.designHash())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, '流程已被其他入口修改，请重新加载后重试')
    }

    var requested = (key, existing) => changes.containsKey(key) ? changes.get(key) : existing
    var timestamp = current.updatedAt() == null ? System.currentTimeMillis()
        : Math.max(System.currentTimeMillis(), current.updatedAt() + 1L)
    var dsl = requested('dsl', current.dsl())
    var designChanged = !Objects.equals(dsl, current.dsl())
    if (designChanged) {
        dsl = BatchSchedulePolicy.normalize(dsl, current.dsl(), tenantId, current.id())
    }
    var updated = new Pipeline(current.id(), current.name(), current.description(), current.createdAt(), timestamp,
        dsl, requested('groupId', current.nifiProcessGroupId()), requested('status', current.status()),
        requested('deployedHash', current.lastDeployedHash()), requested('deployedAt', current.lastDeployedAt()),
        requested('stoppedAt', current.lastStoppedAt()), requested('mapping', current.nodeMapping()),
        requested('lastBulletinId', current.lastBulletinId()))
    var values = {updated_at: updated.updatedAt(), status: updated.status().name(),
        nifi_process_group_id: updated.nifiProcessGroupId(), last_deployed_hash: updated.lastDeployedHash(),
        last_deployed_at: updated.lastDeployedAt(), last_stopped_at: updated.lastStoppedAt(),
        node_mapping_json: updated.nodeMapping() == null ? null : mapper.writeValueAsString(updated.nodeMapping()),
        last_bulletin_id: updated.lastBulletinId()}
    if (designChanged && dsl != null) {
        values.dsl_json = mapper.writeValueAsString(dsl)
        values.dsl_hash = DslHasher.hash(dsl)
        values.dsl_version = dsl.version()
    }
    var columns = new ArrayList(values.keySet())
    values.id = current.id()
    if (tableWrite.batchUpdate(table, tenantId, columns, ['id'], {is_del: 0}, [values]) != 1) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, '流程已被其他入口修改，请重新加载后重试')
    }
    return updated
})
