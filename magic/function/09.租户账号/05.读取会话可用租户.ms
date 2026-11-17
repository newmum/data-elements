{
  "id": "portal_fn_available_v1",
  "groupId": "ga_local_accounts_functions_01",
  "name": "05.读取会话可用租户",
  "path": "/available",
  "parameters": [
    {
      "name": "tenantId",
      "dataType": "Object",
      "required": false,
      "description": "当前可信租户ID"
    },
    {
      "name": "localSession",
      "dataType": "Object",
      "required": false,
      "description": "当前令牌是否为本地账号"
    },
    {
      "name": "currentProfile",
      "dataType": "Object",
      "required": false,
      "description": "入口已读取的当前租户投影"
    }
  ],
  "description": "会话详情和可用租户共用。LOCAL 仅返回本租户。旧共享会话保留开启条件、成员检查和账号来源配置校验。"
}
================================
import '@/tenant-account/profile' as profile
import cn.dev33.satoken.stp.StpUtil
import cn.hutool.extra.spring.SpringUtil
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException
import db
import identityRuntime

// /tenant-account/available 由当前会话与租户列表共同调用，使用已认证租户及令牌账号。
if (localSession) {
    return [currentProfile == null ? profile(tenantId, false) : currentProfile]
}

return identityRuntime.control(() => {
    // 旧共享账号开关保持现有门槛。依照成员关系预取相关租户配置，循环中只检查已读取的数据。
    SpringUtil.getBean('tenantAccessService').requireLegacyCompatibility()
    var relations = db.normal().table('rm_user_tenant_rela_t').columns('tenant_id', 'is_default').where()
        .eq('user_id', StpUtil.getLoginId() + '').eq('status', 1).eq('is_del', 0).select()
    if (relations.isEmpty()) {
        return []
    }
    var keys = relations.map(row => row.tenant_id).distinct()
    var byId = {}
    var offset = 0
    while (offset < keys.size()) {
        var rows = db.normal().table('sym_tenant_t')
            .columns('tid', 'code', 'name', 'status', 'init_flag', 'sort_no', 'created_time', 'json_config')
            .where().eq('is_del', 0).in('tid', keys.subList(offset, Math.min(offset + 500, keys.size()))).select()
        for (row in rows) {
            byId[row.tid] = row
        }
        offset = offset + 500
    }
    var compare = (left, right) => {
        if (left == null) {
            return right == null ? 0 : -1
        }
        return right == null ? 1 : left.compareTo(right)
    }
    var candidates = relations.filter(row => byId[row.tenant_id] != null).sort((left, right) => {
        var rank = compare(right.is_default, left.is_default)
        if (rank == 0) {
            var a = byId[left.tenant_id]
            var b = byId[right.tenant_id]
            rank = compare(a.sort_no, b.sort_no)
            return rank == 0 ? compare(a.created_time, b.created_time) : rank
        }
        return rank
    })
    var policy = SpringUtil.getBean('tenantAccountPolicy')
    var visible = []
    for (candidate in candidates) {
        var tenant = byId[candidate.tenant_id]
        if (tenant.status != 1) {
            throw new TenantAccessException('TENANT-NOT-AVAILABLE', '租户不可用')
        }
        if (!policy.isLocalConfiguration(tenant.tid, tenant.json_config)) {
            visible.add({tid: tenant.tid, code: tenant.code, name: tenant.name, status: tenant.status,
                init_flag: tenant.init_flag, is_default: candidate.is_default})
        }
    }
    return visible
})
