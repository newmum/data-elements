{
  "properties": {},
  "id": "nifi_canvas_template_mapping_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "38.海通模板字段映射",
  "path": "/nifiTemplateMapping",
  "description": "根据来源与目标字段生成旧画布映射 JSON，保留目标字段顺序和 ODS 系统字段。只转换已授权查询的字段。",
  "parameters": [
    {
      "name": "sourceColumns",
      "description": "来源字段列表",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "targetColumns",
      "description": "目标字段列表",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import com.fasterxml.jackson.databind.ObjectMapper as mapper
import java.util.HashSet
import java.util.Locale

// @/common/nifiTemplateMapping 从来源及目标字段组装模板映射，保留旧画布和 ODS 表模板规则。
var sources = new HashSet()
for (column in sourceColumns) {
    if (column.columnName != null && !column.columnName.isBlank()) {
        sources.add(column.columnName)
    }
}
var mappings = []
for (column in targetColumns) {
    var name = column.columnName
    if (name == null || name.isBlank()) {
        continue
    }
    var key = name.trim().toUpperCase(Locale.ROOT)
    // 使用秒级时间戳与随机尾数生成正数入库事件标识，避免 Calcite 毫秒差溢出。
    if (key == 'ODS_UUID') {
        mappings.add({expression: "CAST(TIMESTAMPDIFF(SECOND, TIMESTAMP '1970-01-01 00:00:00', LOCALTIMESTAMP) AS VARCHAR) || CAST(100000000 + RAND_INTEGER(900000000) AS VARCHAR)",
            to: '/ODS_UUID', comment: 'NiFi生成正数ODS主键（秒级时间戳加随机尾数）'})
    } else if (key == 'ODS_RKSJ' || key == 'ODS_GXSJ') {
        mappings.add({expression: 'LOCALTIMESTAMP', to: '/' + key,
            comment: key == 'ODS_RKSJ' ? 'NiFi写入当前入库时间' : 'NiFi写入当前更新时间'})
    } else if (sources.contains(name)) {
        mappings.add({from: '/' + name, to: '/' + name})
    }
}
// 老画布在无同名字段且无系统字段时，保留第一条可编辑示例。
if (mappings.isEmpty()) {
    for (column in sourceColumns) {
        var name = column.columnName
        if (name != null && !name.isBlank()) {
            mappings.add({from: '/' + name, to: '/' + name})
            break
        }
    }
}
var fields = mappings.map(pair => '    {\n' + pair.keySet().map(key => '      ' + mapper.writeValueAsString(key)
    + ': ' + mapper.writeValueAsString(pair[key])).join(',\n') + '\n    }')
return '{\n  "version": "1.0",\n  "passthroughUnmapped": false,\n  "passthroughCaseSensitive": true,\n'
    + '  "onMissingSource": "NULL",\n  "onTypeMismatch": "SKIP_ROW",\n  "mappings": [\n'
    + fields.join(',\n') + '\n  ]\n}'
