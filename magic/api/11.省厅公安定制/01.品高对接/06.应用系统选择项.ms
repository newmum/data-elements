{
  "properties": {},
  "id": "ga_pinggao_options_06",
  "script": null,
  "groupId": "4715f94aac854f9da269d34928f47c4e",
  "name": "06.应用系统选择项",
  "path": "/options",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "返回当前租户可登记的数据源所属应用系统（POST /ga/pinggao/options）。首次同步前仅返回历史项；成功同步后只返回已上线品高应用。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：06.应用系统选择项
// 路由：POST /ga/pinggao/options
// 业务说明：返回当前租户可登记的数据源所属应用系统（POST /ga/pinggao/options）。首次同步前仅返回历史项；成功同步后只返回已上线品高应用。
import cn.hutool.extra.spring.SpringUtil
import com.linewell.dataelement.platform.integration.pingao.PingaoApplicationSyncService
var service = SpringUtil.getBean(PingaoApplicationSyncService.class)
return service.selectableOptions()
