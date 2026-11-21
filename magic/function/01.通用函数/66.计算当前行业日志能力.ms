{
  "id": "log_tenant_capabilities_v1",
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "66.计算当前行业日志能力",
  "path": "/logTenantCapabilities",
  "parameters": [],
  "options": [],
  "description": "供行业日志菜单及组件入口共用。同租户现有会话、有效角色和菜单祖先决定能力，归档与策略要求同一角色具备全量范围。"
}
================================
import cn.dev33.satoken.stp.StpUtil
import java.util.ArrayList
import '@/common/loggrants' as logGrants

// /common/logTenantCapabilities 为日志导航与组件加载计算可信会话的有效模块。
var actor = StpUtil.getLoginIdAsString()
var app = StpUtil.getTokenSession().get('appId')
var appId = app == null ? '' : app + ''
var decision = logGrants(actor, appId, null)
var modules = new ArrayList()

// 归档和策略入口要求该模块本身具备全量授权。
for (name in decision.modes.keySet()) {
    if (!['archives', 'policies'].contains(name) || decision.modes.get(name) == 'ALL') {
        modules.add(name)
    }
}
if (modules.contains('operations') || modules.contains('requests')) {
    modules.add('requestParameters')
}
return modules
