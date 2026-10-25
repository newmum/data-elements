{
  "id": "log_request_guard_v1",
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "33.日志请求权限",
  "path": "/log-guard",
  "parameters": [
    {
      "name": "capability",
      "dataType": "Object",
      "required": false,
      "description": "已校验的日志业务参数"
    },
    {
      "name": "domain",
      "dataType": "Object",
      "required": false,
      "description": "已校验的日志业务参数"
    }
  ],
  "description": "当前真实平台会话的日志功能与全域范围检查。"
}
================================
import '@/idaas/guard' as guard;
var permissions={exports:'audit:export',policies:'audit:configure',archives:'audit:archive'};
if(!['auth','operations','requests','security','exports','archives','policies'].contains(capability)) {exit 400,'日志模块无效';}
return guard(permissions[capability]||'audit:read',null,domain||'workforce');
