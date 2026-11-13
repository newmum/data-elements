{
  "properties": {},
  "id": "log_parameter_condition_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "34.日志参数关键词条件",
  "path": "/log-parameter-keyword-condition",
  "description": "为行业操作日志和接口日志生成参数安全投影的字面包含条件；完整关键词最多200字符，与普通关键词按AND组合。不查询数据库、不接收客户端权限范围，也不支持IDAAS或离线归档检索。",
  "parameters": [
    { "name": "tenantId", "description": "Java日志权限服务已核验的行业租户；平台身份为null，不能取自请求参数", "required": false, "dataType": "String", "type": "java.lang.String" },
    { "name": "kind", "description": "日志类别，仅operations与requests允许非空关键词", "required": true, "dataType": "String", "type": "java.lang.String" },
    { "name": "keyword", "description": "单个完整字面关键词，最长200字符；不拆词、不作正则、不剥离两端空格", "required": false, "dataType": "String", "type": "java.lang.String" }
  ],
  "options": []
}
================================
import java.lang.IllegalArgumentException

// 函数：@/common/log-parameter-keyword-condition。校验行业日志参数关键词并返回纯结构化谓词。
// tenantId只由日志服务从可信LogScope传入；本函数不授予数据权限，也不接受scope或部门范围。
if (keyword == null) {
    return null
}
if (!(keyword instanceof String) || keyword.length() > 200) {
    throw new IllegalArgumentException('参数关键词必须是最长200字符的字符串')
}
if (keyword.length() == 0) {
    return null
}
if (tenantId == null || !(tenantId instanceof String) || tenantId.isBlank()
        || (kind != 'operations' && kind != 'requests')) {
    throw new IllegalArgumentException('参数关键词仅支持行业操作日志和接口日志')
}

// 条件在数据库分页、计数和统计之前按AND附加；值原样绑定，%_!和正则符号没有特殊含义。
return {operator: 'contains', column: 'parameter_search_text', value: keyword}