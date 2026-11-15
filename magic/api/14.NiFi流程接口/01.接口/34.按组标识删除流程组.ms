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
import '@/common/nifiDeleteNativeGroups' as deleteGroup
import org.springframework.http.ResponseEntity

// POST /nifi/api/pipelines/deleteByPgId 按 processGroupId 删除本租户原生流程组。
// 与删除子树入口共用 @/common/nifiDeleteNativeGroups 的锁、归属与协议清理，返回原生删除结果。
return deleteGroup('processGroupId', (groups, processGroupId) => {
    // 单组入口要求目标没有子组。完整子树已经过租户检查。
    if (groups.size() > 1) {
        return ResponseEntity.status(409).body({deleted: false, processGroupId: processGroupId,
            error: '流程组包含子组，请使用删除流程树入口'})
    }
    return null
})
