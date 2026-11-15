{
  "properties": {},
  "id": "nifi_mapping_retention_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "68.海通映射版本保留策略",
  "path": "/nifiMappingRetention",
  "description": "读取映射版本保留策略。",
  "parameters": [],
  "options": []
}
================================
import org.springframework.http.ResponseEntity

// @/common/nifiMappingRetention 为 GET 和 HEAD 返回字段映射不可变版本保留策略。

return ResponseEntity.ok({policy: 'KEEP_ALL', automaticDeletion: false,
    reason: '保留运行、重试、错误队列及回退所需的不可变版本；当前接口不删除历史资源'})
