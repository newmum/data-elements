{
  "id": "log_command_platform_v1",
  "groupId": "log_management_api_group_v1",
  "name": "02.日志管理操作",
  "path": "/command",
  "method": "POST",
  "parameters": [],
  "requestBody": "{}",
  "description": "显式提交平台日志导出、归档及策略变更，分别校验功能权限。"
}
================================
import identityRuntime;
import logManagement;
import '@/idaas/log-guard' as logGuard;
return identityRuntime.control(()=>{
 var p=body||{};var module=p.module;var operation=p.operation;
 var allowed={exports:['export'],archives:['archive','archive-action'],policies:['policy']};
 if(allowed[module]==null||!allowed[module].contains(operation)){exit 400,'日志管理操作无效';}
 var ctx=logGuard(module,p.domain||'workforce');
 return logManagement.query(ctx,module,operation,p.payload||{});
});
