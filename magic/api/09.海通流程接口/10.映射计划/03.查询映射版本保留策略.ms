{
  "properties": {},
  "id": "nifi_map_retention_get_v1",
  "script": null,
  "groupId": "64f711bbc78e59fd86e03d6f0ba9a1c9",
  "name": "03.查询映射版本保留策略",
  "path": "/keep",
  "description": "读取映射版本保留策略。",
  "parameters": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    },
    {
      "name": "head",
      "value": "true"
    }
  ],
  "method": "GET",
  "headers": [],
  "paths": []
}
================================
import org.springframework.http.ResponseEntity

// GET /nifi/plan/keep。读取当前行业映射版本的保留窗口和清理配置。

return ResponseEntity.ok({policy: 'KEEP_ALL', automaticDeletion: false,
    reason: '保留运行、重试、错误队列及回退所需的不可变版本；当前接口不删除历史资源'})
