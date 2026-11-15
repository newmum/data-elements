{
  "properties": {},
  "id": "40fabd244d0a1e4572f9e8803b25be8d",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "46.NiFi删除校验原生组",
  "path": "/nifiDeletionGroupScope",
  "description": "持有共享锁时检查原生组归属、层级限制及子组范围，返回后序清理顺序。",
  "parameters": [
    {
      "name": "targets",
      "description": "待检查的原生组及已核验关联",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "rootId",
      "description": "当前租户配置的 NiFi 根组",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "owner",
      "description": "指定画布所属标识，原生组入口使用空值",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifi
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiException
import java.util.HashSet
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import org.ssssssss.script.exception.MagicScriptException
import org.ssssssss.script.exception.MagicScriptRuntimeException
import tenantRuntime

// @/common/nifiDeletionGroupScope 在已绑定的 NiFi 节点上校验删除范围。
// 对旧组允许使用已从本租户记录验证的绑定。显式外租户标记始终拒绝，子树先完整核验再返回后序清理列表。
var prefix = 'data-elements:v1:'
var tenantPrefix = prefix + tenantRuntime.id() + ':'
var ownerMarker = tenantPrefix + 'pipeline:' + owner
var seen = new HashSet()
var completed = new HashSet()
var postOrder = []
var missing = failure => {
    var cause = failure
    while ((cause instanceof MagicScriptException || cause instanceof MagicScriptRuntimeException) && cause.getCause() != null) {
        cause = cause.getCause()
    }
    return cause instanceof NifiException && cause.status() == 404
}
var denied = () => {
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, '流程组不存在或不属于当前租户')
}
var visit = entity => {
    var component = entity.path('component')
    var id = component.path('id').asText(entity.path('id').asText(''))
    var marker = component.path('comments').asText('')
    if (completed.contains(id)) {
        return
    }
    if (!id.matches('[A-Za-z0-9_-]{1,128}') || id == rootId || 'root'.equalsIgnoreCase(id)
        || !seen.add(id) || seen.size() > 1000) {
        denied()
    }
    if (marker.startsWith(prefix) && !marker.startsWith(tenantPrefix)) {
        denied()
    }
    if (owner != null && marker.startsWith(tenantPrefix + 'pipeline:') && marker != ownerMarker && marker != ownerMarker + ':candidate') {
        denied()
    }
    if (owner != null && marker.startsWith(tenantPrefix) && !marker.startsWith(tenantPrefix + 'pipeline:')) {
        denied()
    }
    for (child in nifi.listProcessGroups(id)) {
        visit(child)
    }
    postOrder.add({id: id, parentId: component.path('parentGroupId').asText('')})
    completed.add(id)
}
for (target in targets) {
    if (!target.id.matches('[A-Za-z0-9_-]{1,128}') || target.id == rootId || 'root'.equalsIgnoreCase(target.id)) {
        denied()
    }
    if (seen.contains(target.id)) {
        continue
    }
    var group = null
    try {
        group = nifi.getProcessGroup(target.id)
    } catch (failure) {
        if (missing(failure) && target.linked) {
            continue
        }
        throw failure
    }
    var component = group.path('component')
    var marker = component.path('comments').asText('')
    if (component.path('parentGroupId').asText('').isBlank() || (!target.linked && !marker.startsWith(tenantPrefix))) {
        denied()
    }
    // 即使组带本租户标记，也要沿原生父链归入配置根，且不得经过外租户容器。
    var parentId = component.path('parentGroupId').asText('')
    var ancestors = new HashSet()
    while (parentId != rootId) {
        if (!parentId.matches('[A-Za-z0-9_-]{1,128}') || !ancestors.add(parentId) || ancestors.size() > 100) {
            denied()
        }
        var parent = nifi.getProcessGroup(parentId).path('component')
        var parentMarker = parent.path('comments').asText('')
        if (parentMarker.startsWith(prefix) && !parentMarker.startsWith(tenantPrefix)) {
            denied()
        }
        parentId = parent.path('parentGroupId').asText('')
    }
    visit(group)
}
return postOrder
