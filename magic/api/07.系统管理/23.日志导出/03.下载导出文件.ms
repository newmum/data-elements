{
  "id": "tenant_logexport_download_get_v1",
  "groupId": "sys_logexport_v1",
  "name": "03.下载导出文件",
  "path": "/download",
  "method": "GET",
  "parameters": [],
  "options": [
    {"name": "access", "value": "TENANT_SESSION"},
    {"name": "head", "value": "true"}
  ],
  "description": "按当前行业会话下载本人的导出文件，复核现有数据范围及文件完整性。"
}
================================
import '@/common/logfile' as attachment
import cn.hutool.extra.spring.SpringUtil

// 使用当前行业会话的导出权限，再由任务服务核对本人、数据范围、有效期和文件摘要。
return attachment(id, selected => {
    var access = SpringUtil.getBean('logAccessService')
    return SpringUtil.getBean('logLifecycleService').download(access.current('exports'), selected)
})
