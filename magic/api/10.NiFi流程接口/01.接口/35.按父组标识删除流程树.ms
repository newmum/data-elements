{
  "properties": {},
  "id": "5d340458be2a074424507a72e8f13c6e",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "35.按父组标识删除流程树",
  "path": "/pipelines/deleteTreeByParentPgId",
  "description": "校验当前租户对指定子树的完整归属，按子组在前的顺序清理。",
  "parameters": [],
  "options": [],
  "method": "POST",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiDeleteNativeGroups' as withAuthorizedGroups
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifi
import org.springframework.http.ResponseEntity

// POST /nifi/api/pipelines/deleteTreeByParentPgId 按 parentPgId 清理本租户原生流程子树。
// 与单组删除共用 @/common/nifiDeleteNativeGroups 的锁、NiFi 节点绑定和全树租户校验。
// 返回原生清理状态，无业务库写入。

return withAuthorizedGroups('parentPgId', (groups, parentPgId) => {
    // 全树已校验。先清理子组，再清理父组，避免越权或未核验的部分删除。
    for (group in groups) {
        nifi.cleanupProcessGroup(group.id)
    }
    return ResponseEntity.ok({deleted: true, parentPgId: parentPgId})
})
