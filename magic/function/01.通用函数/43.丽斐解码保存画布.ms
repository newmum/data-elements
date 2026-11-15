{
  "properties": {},
  "id": "e06d4eb123494419e67f70f2fc03bceb",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "43.丽斐解码保存画布",
  "path": "/nifiDecodeCanvas",
  "description": "按历史存储规则解码结构化查询返回的画布字段，损坏的记录返回空值。",
  "parameters": [
    {
      "name": "row",
      "description": "已核对可信租户的画布记录",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper as mapper
import com.linewell.dataelement.integration.nifi.canvas.pipeline.NifiNodeMapping
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline$Dsl as CanvasDsl
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus
import org.ssssssss.script.exception.MagicScriptException
import org.ssssssss.script.exception.MagicScriptRuntimeException

// @/common/nifiDecodeCanvas 解码已经过租户检查的结构化查询结果。
// 旧记录允许空设计、空映射和 STOP 状态。损坏记录由调用方按缺失处理。
var dsl = null
var mapping = null
try {
    dsl = row.dsl == null || row.dsl.isBlank() ? null : mapper.readValue(row.dsl, CanvasDsl)
    mapping = row.mapping == null || row.mapping.isBlank() ? null : mapper.readValue(row.mapping, NifiNodeMapping)
} catch (failure) {
    var cause = failure
    while ((cause instanceof MagicScriptException || cause instanceof MagicScriptRuntimeException) && cause.getCause() != null) {
        cause = cause.getCause()
    }
    if (cause instanceof JsonProcessingException) {
        return null
    }
    throw failure
}
var status = PipelineStatus.SAVED
if ('STOP'.equalsIgnoreCase(row.status)) {
    status = PipelineStatus.STOPPED
} else {
    for (candidate in PipelineStatus.values()) {
        if (candidate.name().equalsIgnoreCase(row.status)) {
            status = candidate
            break
        }
    }
}
return new Pipeline(row.id, row.name, row.description, row.createdAt?.longValue(), row.updatedAt?.longValue(),
    dsl, row.processGroupId, status, row.deployedHash, row.deployedAt?.longValue(), row.stoppedAt?.longValue(),
    mapping, row.lastBulletinId?.longValue())
