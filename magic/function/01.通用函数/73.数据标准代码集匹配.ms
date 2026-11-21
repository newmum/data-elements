{
  "properties": {},
  "id": "dwm_standard_code_resolution_01",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "73.数据标准代码集匹配",
  "path": "/standardCodeResolution",
  "description": "数据元分页和标准代码分页共用的租户内代码集匹配优先级。",
  "returnType": "Object",
  "parameters": [
    {
      "name": "tenantId",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String"
    }
  ],
  "options": []
}
================================
import tableRead

// 数据元分页和标准代码分页共用这一匹配规则，保证字典项数与引用数采用同一归属。
// 直接 ID 可以指向旧代码行；按字典编码或代码集编码匹配时，只选有效的头记录。
var sources = tableRead.from('da_metadata_t', 'original', tenantId)
var elementId = tableRead.column('original', 'tid', 'TEXT')
var valueDomain = tableRead.column('original', 'value_domain_id', 'TEXT')
var codeId = tableRead.column('candidate', 'tid', 'TEXT')
var direct = tableRead.equal(codeId, valueDomain)
var dictionary = tableRead.equal(tableRead.column('candidate', 'dict_code', 'TEXT'), valueDomain)
var codeSet = tableRead.equal(tableRead.column('candidate', 'code_set', 'TEXT'), valueDomain)
var header = tableRead.equal(tableRead.column('candidate', 'code_value', 'TEXT'), '__HEADER__')
sources.where(tableRead.equal(tableRead.column('original', 'is_del', 'NUMBER'), 0))
sources.leftJoinOn('da_metadata_code_t', 'candidate', [
    tableRead.equal(tableRead.column('candidate', 'is_del', 'NUMBER'), 0),
    tableRead.any([direct, tableRead.all([header, tableRead.any([dictionary, codeSet])])])
])
var priority = tableRead.when(direct, 0, tableRead.when(dictionary, 1, 2))
return sources.ranked('resolved', {element_id: elementId, code_id: codeId},
    [elementId], [priority, codeId], ['asc', 'asc'], true)
