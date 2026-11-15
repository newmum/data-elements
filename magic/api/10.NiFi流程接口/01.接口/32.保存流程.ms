{
  "properties": {},
  "id": "660b71dbe633aaa4bd79dd68e0baad70",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "32.保存流程",
  "path": "/pipelines/{id}",
  "description": "按版本保存当前租户画布设计，保留 NiFi 运行状态。",
  "parameters": [],
  "options": [],
  "method": "POST",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiCanvasWriteRequest' as withRequest
import '@/common/nifiWriteCanvas' as writeCanvas
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiMutationAspect as mutation
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline
import com.linewell.dataelement.model.common.CommonResponse
import com.linewell.dataelement.model.common.StatusCodeEnum
import java.lang.IllegalArgumentException
import java.lang.Long
import java.util.Objects
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.util.NumberUtils
import org.springframework.web.server.ResponseStatusException

// POST /nifi/api/pipelines/{id} 接收画布及可选的设计哈希或时间戳，返回保存后的画布。
// 持有共享 NiFi 锁后，在同一事务内校验版本并更新当前租户的字段规则投影和设计。
// 请求中的运行元数据不参与保存，画布找不到返回 404，版本冲突返回 409。

var pipelineType = Pipeline.getName()
var bodySignature = 'org.springframework.http.ResponseEntity<' + pipelineType
    + '> com.linewell.dataelement.integration.nifi.canvas.controller.PipelineController.update(java.lang.String,'
    + pipelineType + ',java.lang.Long,java.lang.String)'
return withRequest(bodySignature, body, (submitted, tenantId, parameterValues, textParameter, rootCause) => {
    // 兼容旧客户端的时间戳参数。写入时优先比较请求提供的设计哈希。
    var expectedTime = parameterValues('expectedUpdatedAt')
    var timestamp = null
    if (expectedTime != null && !expectedTime.isEmpty() && !(expectedTime.size() == 1 && expectedTime[0] == '')) {
        try {
            timestamp = NumberUtils.parseNumber(expectedTime[0], Long)
        } catch (failure) {
            if (!(rootCause(failure) instanceof IllegalArgumentException)) {
                throw failure
            }
            return ResponseEntity.ok(CommonResponse.fail(StatusCodeEnum.PARAMS_UNVALID.getCode(),
                '参数[expectedUpdatedAt]类型错误，期望类型: Long', '参数类型不匹配'))
        }
    }
    var hash = textParameter('expectedDesignHash')
    return mutation.withMutationLock(() => db.transaction(() => {
        var saved = writeCanvas(submitted, path.id, tenantId, current => {
            // 运行状态观测可以更新保存时间。新客户端只用设计哈希判定冲突。
            var conflict = hash != null ? !Objects.equals(current.designHash(), hash)
                : timestamp != null && !Objects.equals(current.updatedAt(), timestamp)
            if (conflict) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    '平台画布设计已被其他入口修改，请重新加载后再保存；NiFi原生修改不影响此校验')
            }
        })
        return saved == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(saved)
    }))
})
