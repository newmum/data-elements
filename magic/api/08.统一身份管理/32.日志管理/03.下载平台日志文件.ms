{
  "id": "platform_log_download_get_v1",
  "groupId": "log_management_api_group_v1",
  "name": "03.下载平台日志文件",
  "path": "/download",
  "method": "GET",
  "parameters": [],
  "options": [
    {"name": "access", "value": "IDAAS_PLATFORM_SESSION"},
    {"name": "head", "value": "true"}
  ],
  "description": "按平台操作员的导出权限和身份域下载本人任务，默认政企身份域。"
}
================================
import '@/common/logfile' as attachment
import '@/idaas/log-guard' as logGuard
import cn.hutool.extra.spring.SpringUtil
import identityRuntime
import java.lang.IllegalArgumentException

// 平台下载只使用控制库，以同一角色分配核对导出权限和身份域。
return identityRuntime.control(() => attachment(id, selected => {
    var selectedDomain = domain || 'workforce'
    if (!['workforce', 'public'].contains(selectedDomain)) { throw new IllegalArgumentException('身份域不正确') }
    var context = logGuard('exports', selectedDomain)
    var access = SpringUtil.getBean('logAccessService')
    return SpringUtil.getBean('logLifecycleService').download(access.platform(context, 'exports'), selected)
}))
