{
  "properties": {},
  "id": "nifi_map_dict_snapshot_post_v1",
  "script": null,
  "groupId": "e156354f7d79565aa8ed30f16bf35ed1",
  "name": "03.生成字典快照",
  "path": "/snapshot",
  "description": "读取授权字典并保存不可变快照。",
  "parameters": [],
  "options": [
    {
      "name": "jsonBody",
      "value": "EXACT_TREE"
    }
  ],
  "method": "POST",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiLifecycleBody' as readBody
import '@/common/nifiMappingReadHttp' as withHttp
import '@/common/nifiSaveMappingVersion' as saveVersion
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.linewell.dataelement.integration.nifi.canvas.mapping.UnifiedMappingService as mapping
import com.linewell.dataelement.mapping.core.MappingException
import java.util.LinkedHashMap
import org.springframework.http.ResponseEntity

// POST /nifi/dict/snapshot 生成字典快照。
// POST /nifi/dict/snapshot 基于当前已授权的字典配置写入不可变快照。
// 每次以登记源读取新快照，客户端传入的快照标识和摘要均不参与授权或定位。

return withHttp(() => {
    var type = 'com.fasterxml.jackson.databind.JsonNode'
    var signature = type + ' com.linewell.dataelement.integration.nifi.canvas.controller.MappingAssetController.snapshot(' + type + ')'
    var input = readBody(body, JsonNode, type, signature)
    if (!input.path('definition').isObject()) {
        throw new MappingException('INVALID_DICTIONARY', '快照须提供结构化字典定义')
    }
    var definition = input.path('definition').deepCopy()
    definition.remove(['snapshotId', 'snapshotHash', 'definitionHash'])
    definition.put('freshnessMode', 'LIVE_BATCH')
    var draft = JsonNodeFactory.instance.objectNode().put('schemaVersion', '2.0')
    draft.putArray('rules')
    draft.putObject('dictionaryBindings').set('requested', definition)
    var prepared = mapping.compile(draft)
    var dictionaries = new LinkedHashMap(prepared.plan().dictionaries())
    var dictionaryId = dictionaries.keySet().toArray()[0]
    var binding = prepared.plan().document().path('dictionaryBindings').path(dictionaryId)
    var content = mapping.readSnapshot(prepared, dictionaryId)
    return ResponseEntity.ok(saveVersion('DICTIONARY_SNAPSHOT', input.path('name').asText(), binding, content))
})
