{
  "properties": {},
  "id": "ga_pinggao_legacy_purge_05",
  "script": null,
  "groupId": "4715f94aac854f9da269d34928f47c4e",
  "name": "05.清理历史应用",
  "path": "/legacy-purge",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "软删除未被数据源引用的历史应用（POST /ga/pinggao/legacy-purge）。可传 body.ids 指定范围；不会删除被引用记录。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.清理历史应用
// 路由：POST /ga/pinggao/legacy-purge
// 业务说明：软删除未被数据源引用的历史应用（POST /ga/pinggao/legacy-purge）。可传 body.ids 指定范围；不会删除被引用记录。
import cn.hutool.extra.spring.SpringUtil
import com.linewell.dataelement.platform.integration.pingao.PingaoApplicationSyncService
var service = SpringUtil.getBean(PingaoApplicationSyncService.class)

var rawIds = body == null ? null : body.ids
var ids = []
if (rawIds != null) {
    for (item in rawIds) {
        if (item != null && (item + '').trim() != '') { ids.add(item + '') }
    }
}
return service.purgeLegacy(ids)
