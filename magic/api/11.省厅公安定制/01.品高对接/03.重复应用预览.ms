{
  "properties": {},
  "id": "ga_pinggao_duplicate_03",
  "script": null,
  "groupId": "4715f94aac854f9da269d34928f47c4e",
  "name": "03.重复应用预览",
  "path": "/duplicate-preview",
  "method": "GET",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "预览历史应用与品高应用的同名重复项（GET /ga/pinggao/duplicate-preview）。只读。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.重复应用预览
// 路由：GET /ga/pinggao/duplicate-preview
// 业务说明：预览历史应用与品高应用的同名重复项（GET /ga/pinggao/duplicate-preview）。只读。
import cn.hutool.extra.spring.SpringUtil
import com.linewell.dataelement.platform.integration.pingao.PingaoApplicationSyncService
var service = SpringUtil.getBean(PingaoApplicationSyncService.class)
return service.duplicatePreview()
