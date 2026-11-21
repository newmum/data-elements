{
  "id": "collect_fn_pageargs_v1",
  "groupId": "collection_fn_group_v1",
  "name": "05.读取表清单分页参数",
  "path": "/pageargs",
  "parameters": [],
  "description": "已保存表和新增表共用旧查询参数与分页边界。"
}
================================
import com.linewell.dataelement.model.common.CommonResponse
import com.linewell.dataelement.model.common.StatusCodeEnum
import java.lang.Integer
import java.util.Locale
import request

// /collect/pageargs 保持采集清单原查询参数名称、缺省值和类型错误。
var values = name => {
    var raw = request.getValues(name)
    return raw == null ? request.getValues(name + '[]') : raw
}
var dbIds = values('dbId')
if (dbIds == null || dbIds.isEmpty()) {
    return {error: CommonResponse.fail(StatusCodeEnum.PARAMS_NOT_FOUND.getCode(), '缺少必要参数: dbId', '参数缺失')}
}
var page = {pageNo: 1, pageSize: 15}
for (field in ['pageNo', 'pageSize']) {
    var raw = values(field)
    if (raw != null && !raw.isEmpty() && raw[0] != '') {
        try {
            page[field] = Integer.valueOf(raw[0].replaceAll('(?U)\s', ''))
        } catch (error) {
            return {error: CommonResponse.fail(StatusCodeEnum.PARAMS_UNVALID.getCode(),
                '参数[' + field + ']类型错误，期望类型: int', '参数类型不匹配')}
        }
    }
}
var optional = name => {
    var raw = values(name)
    return raw == null ? '' : raw.join(',').trim()
}
page.source = dbIds.join(',').trim()
page.pageNo = Math.max(1, page.pageNo)
page.pageSize = Math.max(1, Math.min(100, page.pageSize))
page.keyword = optional('keyword').toLowerCase(Locale.ROOT)
page.sortField = optional('sortField')
page.sortDir = optional('sortDir').toLowerCase(Locale.ROOT)
return page
