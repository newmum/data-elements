{
  "id": "log_component_bundle_v1",
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "65.读取现役日志组件",
  "path": "/logComponentBundle",
  "parameters": [],
  "options": [],
  "description": "供行业和 IDAAS 日志入口授权后读取同一共享日志组件，返回原编译包字段。"
}
================================
import identityRuntime

// /common/logComponentBundle 由行业日志组件和 IDAAS 日志查询共用。调用前须校验各自会话的日志权限。
return identityRuntime.control(() => db.lower().table('ui_component_t')
    .columns('tid', 'name', 'compile_js', 'compile_css').where()
    .eq('is_del', 0).eq('type', '1').eq('name', 'log-management').select())
