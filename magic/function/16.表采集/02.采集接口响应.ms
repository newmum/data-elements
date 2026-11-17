{
  "id": "collect_fn_response_v1",
  "groupId": "collection_fn_group_v1",
  "name": "02.采集接口响应",
  "path": "/response",
  "parameters": [
    {
      "name": "action",
      "dataType": "Object",
      "required": false,
      "description": "相应入口的业务闭包"
    }
  ],
  "description": "保留采集接口原成功与异常响应格式。"
}
================================
import cn.hutool.extra.spring.SpringUtil
import com.linewell.dataelement.model.common.CommonResponse
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException

// /collect/response 保留登记页面和万象客户端使用的旧响应字段。
try {
    var result = action()
    return ResponseEntity.ok(result instanceof CommonResponse ? result : {code: 0, msg: 'success', data: result})
} catch (error) {
    var cause = error
    while (cause.getCause() != null && cause.getCause() != cause && !(cause instanceof IllegalArgumentException)
            && !(cause instanceof IllegalStateException) && !(cause instanceof HttpMessageNotReadableException)) {
        cause = cause.getCause()
    }
    var handler = SpringUtil.getBean('globalExceptionHandler')
    return ResponseEntity.ok(handler.handleException(cause instanceof Exception ? cause : error))
}
