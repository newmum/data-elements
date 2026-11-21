{
  "id": "collect_fn_historyargs_v1",
  "groupId": "collection_fn_group_v1",
  "name": "08.读取采集历史分页参数",
  "path": "/historyargs",
  "parameters": [
    {
      "name": "defaultSize",
      "dataType": "Object",
      "required": false,
      "description": "入口的默认页大小"
    },
    {
      "name": "withOffset",
      "dataType": "Object",
      "required": false,
      "description": "是否接受快照偏移"
    }
  ],
  "description": "差异与待标注快照共用旧 GET 参数绑定。"
}
================================
import com.linewell.dataelement.model.common.CommonResponse
import com.linewell.dataelement.model.common.StatusCodeEnum
import java.lang.Integer
import java.util.Locale
import org.springframework.util.NumberUtils
import request

// /collect/historyargs 保留 Spring 查询参数绑定、数组后缀和必填参数错误。
var values = name => {
    var supplied = request.getValues(name)
    return supplied == null ? request.getValues(name + '[]') : supplied
}
var asString = name => {
    var items = values(name)
    return items == null ? '' : items.join(',')
}
var sources = values('dbId')
if (sources == null || sources.isEmpty()) {
    return {error: CommonResponse.fail(StatusCodeEnum.PARAMS_NOT_FOUND.getCode(), '缺少必要参数: dbId', '参数缺失')}
}
var page = {source: sources.join(',').trim(), pageNo: 1, pageSize: defaultSize, offset: null}
var fields = withOffset ? ['pageNo', 'pageSize', 'offset'] : ['pageNo', 'pageSize']
for (field in fields) {
    var supplied = values(field)
    if (supplied != null && !supplied.isEmpty() && supplied[0] != '') {
        try {
            page[field] = NumberUtils.parseNumber(supplied[0], Integer.class)
        } catch (error) {
            var type = field == 'offset' ? 'Integer' : 'int'
            return {error: CommonResponse.fail(StatusCodeEnum.PARAMS_UNVALID.getCode(),
                '参数[' + field + ']类型错误，期望类型: ' + type, '参数类型不匹配')}
        }
    }
}
page.pageNo = Math.max(1, page.pageNo)
page.pageSize = Math.max(1, Math.min(withOffset ? 1000 : 100, page.pageSize))
var keyword = asString('keyword')
page.keyword = keyword.isBlank() ? '' : keyword.toLowerCase(Locale.ROOT)
page.businessType = asString('businessType').trim()
page.sortField = asString('sortField').trim()
page.sortDir = asString('sortDir').trim().toLowerCase(Locale.ROOT)
return page
