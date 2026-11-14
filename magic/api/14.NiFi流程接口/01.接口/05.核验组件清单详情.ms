{
  "properties": {},
  "id": "65a9bf8cb3854fd483d7dc4cfc43169d",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "05.核验组件清单详情",
  "path": "/manifests/{key}",
  "method": "HEAD",
  "description": "按路径 key 为已认证会话核验内置的 NiFi 组件定义。沿用详情的 200 或 404 及响应头，按 HEAD 协议返回空体。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import com.linewell.dataelement.integration.nifi.canvas.manifest.ManifestRegistry as manifestRegistry
import org.springframework.http.ResponseEntity

// HEAD /nifi/api/manifests/{key} 按完整组件键核验内置定义是否存在。
// 沿用详情 GET 的认证、状态和响应头，无写入，传输体由 Spring 抑制。

// 缺失组件沿用 HTTP 404，方便画布区分配置失效和可用组件。
var manifest = manifestRegistry.get(path.key)
if (manifest == null) {
    return ResponseEntity.notFound().build()
}

return ResponseEntity.ok(manifest)
