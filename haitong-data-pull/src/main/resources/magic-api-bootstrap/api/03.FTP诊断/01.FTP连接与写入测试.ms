{"properties":{},"id":"haitong-ftp-diagnostics-probe-01","groupId":"haitong-ftp-diagnostics-03","name":"01.FTP连接与写入测试","path":"/probe","method":"POST","parameters":[],"options":[],"headers":[],"paths":[],"description":"仅管理员可调用。使用 application.yml 的 FTP 配置测试连接、逐级进入或创建目录、上传/回读/删除随机测试文件。不会接受外部 FTP 地址、账号或密码。","requestBodyDefinition":null,"responseBodyDefinition":null}
================================
// 仅管理员密钥可调用；具体权限由 ftpDiagnostics Java 模块校验。
// 默认只检查连接和目录。createDirectories/uploadTest 必须显式设为 true 才会产生写入操作。
import ftpDiagnostics
import request
return ftpDiagnostics.probe(body == null ? {} : body, request)
