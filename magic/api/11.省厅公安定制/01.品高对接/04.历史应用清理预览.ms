{
  "properties": {},
  "id": "ga_pinggao_legacy_preview_04",
  "script": null,
  "groupId": "4715f94aac854f9da269d34928f47c4e",
  "name": "04.历史应用清理预览",
  "path": "/legacy-purge-preview",
  "method": "GET",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "预览可清理和被数据源引用的历史应用（GET /ga/pinggao/legacy-purge-preview）。只读。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.历史应用清理预览
// 路由：GET /ga/pinggao/legacy-purge-preview
// 业务说明：预览可清理和被数据源引用的历史应用（GET /ga/pinggao/legacy-purge-preview）。只读。
import cn.hutool.extra.spring.SpringUtil
import com.linewell.dataelement.platform.integration.pingao.PingaoApplicationSyncService
var service = SpringUtil.getBean(PingaoApplicationSyncService.class)
return service.legacyPurgePreview()
