{
  "properties": {},
  "id": "7de6ec08dc2c43fdad58182334f49b38",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "02.查询组件清单",
  "path": "/manifests",
  "method": "GET",
  "description": "向已认证会话返回 NiFi 画布可用的组件清单，无请求参数。只读应用内置定义，响应为原始 JSON 数组。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import com.linewell.dataelement.integration.nifi.canvas.manifest.ManifestRegistry as manifestRegistry
import org.springframework.http.ResponseEntity

// GET /nifi/api/manifests 返回画布使用的全部内置组件清单，无请求参数。
// 现有 Web 拦截器验证登录和行业会话。清单来自应用资源，读取不访问业务库或 NiFi。

// 直接返回原始数组，保持画布和已有调用方使用的 HTTP 响应格式。
return ResponseEntity.ok(manifestRegistry.all())
