{
  "properties": {},
  "id": "cbcc1df7d1d14e748af883e9ed23105f",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "35.NiFi画布拉取规则绑定",
  "path": "/bindCanvasApiPullRules",
  "createTime": 1794627294923,
  "updateTime": null,
  "parameters": [
    {
      "name": "datasourceId",
      "description": "服务端从已登记来源解析的数据源 ID",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String"
    },
    {
      "name": "sourceTableId",
      "description": "服务端从目录解析的来源表 ID",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String"
    },
    {
      "name": "pipelineId",
      "description": "新建的 NiFi 流程 ID",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String"
    }
  ],
  "options": [],
  "description": "新建 NiFi 画布并关联接入任务后，在独立租户事务中绑定当前 API 拉取来源表的有效规则。返回是否适用及匹配行数，不启动 NiFi。"
}
================================
import tenantRuntime
import tableRead
import tableWrite
import java.lang.IllegalArgumentException
import java.time.LocalDateTime
import java.util.Locale
import java.util.Objects

// @/common/bindCanvasApiPullRules 是新建 NiFi 画布的内部绑定回调。
// 数据源、来源表和流程标识由调用方解析，租户从可信上下文取得。返回适用性、绑定行数与提示。
// 由画布入口启动独立事务，更新有效规则及审计字段。此函数不调用 NiFi。
var tenantId = tenantRuntime.id()
if (datasourceId == null || datasourceId.isBlank()) {
    throw new IllegalArgumentException('数据源标识不能为空')
}
if (sourceTableId == null || sourceTableId.isBlank()) {
    throw new IllegalArgumentException('来源表标识不能为空')
}
if (pipelineId == null || pipelineId.isBlank()) {
    throw new IllegalArgumentException('NiFi 流程标识不能为空')
}

// 按当前租户读取有效来源，保留旧回调的普通读取及历史接入方式文本识别。
var sources = tableRead.from('db_datasource_t', 's', tenantId)
var id = tableRead.column('s', 'tid', 'TEXT')
sources.where(tableRead.equal(id, datasourceId))
sources.where(tableRead.equal(tableRead.column('s', 'is_del', 'NUMBER'), 0))
var rows = sources.rows({
    dbType: tableRead.column('s', 'db_type', 'TEXT'),
    poolCfg: tableRead.column('s', 'pool_cfg', 'LOB')
}, id, 'asc', 1)
if (rows.isEmpty()) {
    throw new IllegalArgumentException('数据源不存在或不属于当前租户')
}
var source = rows[0]
var type = Objects.toString(source.dbType, '').trim().toLowerCase(Locale.ROOT)
var pool = Objects.toString(source.poolCfg, '').trim().replaceAll('\\s', '').toLowerCase(Locale.ROOT)
if (type != 'api' || (!pool.contains('"accessmode":"capture"') && !pool.contains('"accessmode":"pull"'))) {
    return {applicable: false, message: '当前来源不是 API 数据拉取，未绑定表规则'}
}

// 直接更新当时仍有效的规则。按原契约写入 system 和应用时钟，无需先读取规则。
var changed = tableWrite.batchUpdate('data_pull_rule_t', tenantId,
    ['nifi_task_id', 'updated_by', 'updated_time'], ['source_table_id'], {datasource_id: datasourceId, is_del: 0}, [{
        source_table_id: sourceTableId,
        nifi_task_id: pipelineId,
        updated_by: 'system',
        updated_time: LocalDateTime.now()
    }])
return {
    applicable: true,
    pipelineId: pipelineId,
    boundRules: changed,
    message: changed > 0
        ? '表级拉取规则已绑定 NiFi 任务；可在画布中部署、手动启动或按 Cron 调度'
        : '当前表尚未同步出 API 拉取规则，请返回第二步保存表标注后重试'
}
