{
  "properties": {},
  "id": "nifi_candidate_bind_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "54.海通部署候选绑定",
  "path": "/nifiBindCandidate",
  "description": "首次部署和显式候选切换共用画布、映射版本及接入任务更新。",
  "parameters": [
    {"name":"canvas","description":"已验证租户的画布","required":true,"dataType":"Object","type":"java.lang.Object"},
    {"name":"candidate","description":"待切换的原生候选流程","required":true,"dataType":"Object","type":"java.lang.Object"},
    {"name":"tenantId","description":"当前可信行业租户","required":true,"dataType":"String","type":"java.lang.String"}
  ],
  "options": []
}
================================
import '@/common/nifiWriteRuntime' as writeRuntime
import '@/common/nifiTaskState' as syncTask
import com.linewell.dataelement.integration.nifi.canvas.mapping.MappingVersionStore as mappingVersions
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus
import java.lang.System
import org.springframework.transaction.support.TransactionTemplate as transactions

// @/common/nifiBindCandidate 为首次部署和显式候选切换绑定原生组。
// 画布、映射版本和接入任务在同一事务更新，任一步失败均回滚绑定。

return transactions.execute(transaction => {
    var id = canvas.id()
    var groupId = candidate.processGroupId()
    // 仅切换已核验的画布版本，任务同步使用同一可信租户的流程 ID。
    var updated = writeRuntime(id, tenantId, {expectedDesignHash: canvas.designHash(), groupId: groupId,
        status: PipelineStatus.STOPPED, deployedHash: candidate.deploymentHash(), deployedAt: System.currentTimeMillis(),
        mapping: candidate.mapping(), lastBulletinId: 0L})
    if (candidate.planVersionIds() != null) {
        mappingVersions.activate(canvas, candidate.planVersionIds(), groupId)
    }
    syncTask(id, 'group', groupId)
    syncTask(id, 'status', 0)
    return updated
})
