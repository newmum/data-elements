{
  "properties": {},
  "id": "ga_pinggao_status_02",
  "script": null,
  "groupId": "4715f94aac854f9da269d34928f47c4e",
  "name": "02.同步状态",
  "path": "/sync-status",
  "method": "GET",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "查询当前租户品高应用同步状态（GET /ga/pinggao/sync-status）。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.同步状态
// 路由：GET /ga/pinggao/sync-status
// 业务说明：查询当前租户品高应用同步状态（GET /ga/pinggao/sync-status）。
import cn.hutool.extra.spring.SpringUtil
import com.linewell.dataelement.platform.integration.pingao.PingaoApplicationSyncService
var service = SpringUtil.getBean(PingaoApplicationSyncService.class)
return service.syncState()
