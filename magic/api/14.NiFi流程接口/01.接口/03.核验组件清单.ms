{
  "properties": {},
  "id": "96dd5aefa9014a1b9b4a2218f17df25f",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "03.核验组件清单",
  "path": "/manifests",
  "method": "HEAD",
  "description": "向已认证会话返回组件清单的 HTTP 状态和响应头，无请求参数或响应体。只读应用内置定义。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import com.linewell.dataelement.integration.nifi.canvas.manifest.ManifestRegistry as manifestRegistry
import org.springframework.http.ResponseEntity

// HEAD /nifi/api/manifests 核验组件清单可用性，无请求参数。
// 沿用清单 GET 的认证和数据来源，返回相同状态和响应头。

// Spring 使用相同对象计算响应头，并按 HEAD 语义抑制传输体。
return ResponseEntity.ok(manifestRegistry.all())
