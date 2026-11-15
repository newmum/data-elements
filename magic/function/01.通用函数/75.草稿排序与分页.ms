{
  "properties": {},
  "id": "preview_draft_order_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "75.草稿排序与分页",
  "path": "/previewDraftOrder",
  "parameters": [{"name": "input", "dataType": "Object", "type": "java.lang.Object"}],
  "options": [],
  "description": "为系统管理和统一身份管理工作区共用的草稿排序实现。调用方先校验各自会话。校验浏览器草稿项并分页返回标识和总数，无数据库读写。"
}
================================
import '@/common/nifiLifecycleBody' as readBody
import '@/common/nifiPipelineReadHttp' as withHttp
import com.linewell.dataelement.model.common.CommonResponse
import com.linewell.dataelement.platform.magic.TableSortMagicModule$PreviewRequest as PreviewRequest
import java.lang.IllegalArgumentException
import java.lang.Number
import java.lang.String
import java.math.BigDecimal
import java.time.Instant
import java.util.HashSet
import org.springframework.http.ResponseEntity
import tableSort

// @/common/previewDraftOrder 为行业草稿和平台工作区共用输入校验、内存排序与分页。
// 仅处理本次请求中的有界草稿，返回标识及总数，并保持原 MVC 响应格式。
return withHttp(() => {
    var oldType = 'com.linewell.dataelement.platform.persistence.TablePreviewOrderController$Query'
    var signature = 'com.linewell.dataelement.model.common.CommonResponse<java.util.Map<java.lang.String, java.lang.Object>> '
        + 'com.linewell.dataelement.platform.persistence.TablePreviewOrderController.order('
        + oldType + ',jakarta.servlet.http.HttpServletRequest)'
    var query = readBody(input, PreviewRequest, oldType, signature)
    var items = query.items()
    if (items == null || items.size() > 1000) {
        throw new IllegalArgumentException('预览排序最多支持1000项，请缩小视图范围')
    }
    var kind = query.kind() == null ? 'text' : query.kind()
    if (!['text', 'number', 'time'].contains(kind)) {
        throw new IllegalArgumentException('无效的预览排序类型')
    }
    var whole = query.wholeCollection() == true
    var page = whole || query.page() == null ? 1 : query.page()
    var size = whole ? 1000 : query.size() == null ? 20 : query.size()
    if (page < 1 || size < 1 || size > (whole ? 1000 : 100)) {
        throw new IllegalArgumentException('预览分页参数无效')
    }

    var ids = new HashSet()
    var rows = []
    for (item in items) {
        var id = item?.id()
        if (id == null || id.isBlank() || id.length() > 256 || !ids.add(id)) {
            throw new IllegalArgumentException('预览行标识无效或重复')
        }
        var value = item.value()
        if (value != null) {
            if (!(value instanceof String || value instanceof Number) || value.toString().length() > 1024) {
                throw new IllegalArgumentException('预览排序值无效')
            }
            var text = value.toString()
            if (text.isBlank()) {
                value = null
            } else if (kind == 'number') {
                value = new BigDecimal(text)
            } else if (kind == 'time') {
                value = Instant.parse(text).toEpochMilli()
            } else {
                value = text
            }
        }
        rows.add({id: id, value: value})
    }

    // 先对完整草稿排序，再按页截取，结果只包含已校验的输入标识。
    var direction = query.direction() == null ? 'asc' : query.direction()
    var sorted = tableSort.rows(rows, {sortDir: direction}, {value: 'value'}, 'value', 'id')
    var start = Math.min((page::long - 1L) * size, sorted.size())::int
    var end = Math.min(start + size, sorted.size())
    var keys = sorted.subList(start, end).map(row => row.id)
    return ResponseEntity.ok(CommonResponse.success({keys: keys, total: rows.size()}))
})
