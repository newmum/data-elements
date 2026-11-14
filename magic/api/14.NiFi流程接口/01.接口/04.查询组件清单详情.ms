{
  "properties": {},
  "id": "371116c2d3de4fefa447494c7bf96d6b",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "04.查询组件清单详情",
  "path": "/manifests/{key}",
  "method": "GET",
  "description": "按路径 key 为已认证会话读取应用内置的 NiFi 组件定义。成功返回原始 JSON 对象，找不到该 key 返回空体 HTTP 404。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import com.linewell.dataelement.integration.nifi.canvas.manifest.ManifestRegistry as manifestRegistry
import org.springframework.http.ResponseEntity

// GET /nifi/api/manifests/{key} 按完整组件键读取内置清单，例如 source.postgresql。
// 现有 Web 拦截器验证会话。命中返回原始 JSON，缺失返回空体 HTTP 404，无写入。

// 保留键中的点号和大小写，以实际注册的完整键匹配组件。
var manifest = manifestRegistry.get(path.key)
if (manifest == null) {
    return ResponseEntity.notFound().build()
}

return ResponseEntity.ok(manifest)
