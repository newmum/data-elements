{
  "properties": {},
  "id": "nifi_one_shot_sources_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "57.丽斐一次性来源处理器",
  "path": "/nifiOneShotSources",
  "description": "为部署和启动解析一次性来源处理器。",
  "parameters": [
    {
      "name": "canvas",
      "description": "已验证的画布",
      "required": false,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "primary",
      "description": "当前节点映射",
      "required": false,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import com.linewell.dataelement.integration.nifi.canvas.compile.DslCompiler as compiler
import com.linewell.dataelement.integration.nifi.canvas.compile.SyncPolicy
import java.util.LinkedHashSet

// @/common/nifiOneShotSources 为部署和启动解析一次性来源处理器。
// 仅使用已授权画布和当前编译或保存的节点映射。

var result = new LinkedHashSet()
if (primary != null) {
    for (node in canvas.dsl()?.nodes() || []) {
        if (node.category() == 'source' && (node.manifestKey() == 'source.api' && node.config()?.get('scheduleMode') == 'once'
            || compiler.supportsSyncSettings(node.manifestKey()) && SyncPolicy.mode(node.config()) == 'FULL')) {
            var processor = primary.get(node.id())
            if (processor != null) {
                result.add(processor)
            }
        }
    }
}
return result
