{
  "properties": {},
  "id": "0f217152c7fa1555211a98ac7d1db9d4",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "34.按组标识删除流程组",
  "path": "/pipelines/deleteByPgId",
  "description": "仅在当前租户拥有原生组且无待清理子组时删除该组。",
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

// POST /nifi/api/pipelines/deleteByPgId 按 processGroupId 清理本租户的原生流程组。
// 与父组删除共用 @/common/nifiDeleteNativeGroups 的锁、NiFi 节点绑定和全树租户校验。
// 含子组时返回 409，无业务库写入。

return withAuthorizedGroups('processGroupId', (groups, processGroupId) => {
    if (groups.size() > 1) {
        return ResponseEntity.status(409).body({
            deleted: false,
            processGroupId: processGroupId,
            error: '流程组包含子组，请使用删除流程树入口'
        })
    }
    // 本租户绑定的旧组已经不存在时，同样返回删除成功。
    if (!groups.isEmpty()) {
        nifi.cleanupProcessGroup(groups[0].id)
    }
    return ResponseEntity.ok({deleted: true, processGroupId: processGroupId})
})
