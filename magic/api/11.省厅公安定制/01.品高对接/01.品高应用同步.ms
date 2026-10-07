{
  "properties": {},
  "id": "ga_pinggao_sync_01",
  "script": null,
  "groupId": "4715f94aac854f9da269d34928f47c4e",
  "name": "01.品高应用同步",
  "path": "/sync",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "品高应用全量同步（POST /ga/pinggao/sync）。同步由服务端适配器调用品高私网接口；凭据不返回、不记录。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.品高应用同步
// 路由：POST /ga/pinggao/sync
// 业务说明：品高应用全量同步（POST /ga/pinggao/sync）。同步由服务端适配器调用品高私网接口；凭据不返回、不记录。
import cn.hutool.extra.spring.SpringUtil
import cn.dev33.satoken.stp.StpUtil
import com.linewell.dataelement.platform.integration.pingao.PingaoApplicationProperties
var properties = SpringUtil.getBean(PingaoApplicationProperties.class)
StpUtil.checkPermission(properties.getManualSyncPermission())
import cn.hutool.extra.spring.SpringUtil
import com.linewell.dataelement.platform.integration.pingao.PingaoApplicationSyncService
var service = SpringUtil.getBean(PingaoApplicationSyncService.class)
return service.syncCurrentTenant()
