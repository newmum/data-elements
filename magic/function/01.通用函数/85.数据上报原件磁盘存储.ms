{
  "properties": {},
  "id": "report_original_disk_storage_01",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "85.数据上报原件磁盘存储",
  "path": "/reportoriginal",
  "description": "上报原件上传、解析和下载共用的磁盘存储。接收可信租户、原件标识及写入或读取指令，按租户定位服务端目录，读取返回由调用方关闭的文件流。",
  "returnType": "Object",
  "parameters": [
    { "name": "operation", "required": true, "dataType": "String", "type": "java.lang.String" },
    { "name": "tenantId", "required": true, "dataType": "String", "type": "java.lang.String" },
    { "name": "storageKey", "required": true, "dataType": "String", "type": "java.lang.String" },
    { "name": "upload", "required": false, "dataType": "Object", "type": "java.lang.Object" }
  ],
  "options": []
}
================================
import org.springframework.core.env.Environment as environment
import java.io.BufferedInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption

// /common/reportoriginal 供上报上传、解析及下载复用。调用方先校验租户、文件记录及操作权限。
// 文件键只接受服务端生成的标识。上传流写完后才返回，读取流由调用方关闭。
if (tenantId == null || !tenantId.matches('[A-Za-z0-9_-]{1,32}') || storageKey == null || !storageKey.matches('report-local:[0-9a-f]{32}')) {
    throw new IllegalArgumentException('原件存储标识无效')
}
var root = new File(environment.getProperty('storage.root-dir', './data')).getCanonicalFile()
var writing = operation == 'write'
if (writing) { Files.createDirectories(root.toPath()) }
var directory = new File(root, 'report-originals')
var requireDirectory = candidate => {
    var path = candidate.toPath()
    if (writing) { Files.createDirectories(path) }
    // 租户目录及其父目录必须是存储根目录下的真实目录，拒绝符号链接和重解析目标。
    if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || !candidate.getCanonicalFile().equals(candidate)) {
        throw new IllegalStateException('原件存储目录无效')
    }
}
requireDirectory(directory)
directory = new File(directory, tenantId)
requireDirectory(directory)
var target = new File(directory, storageKey.substring('report-local:'.length()) + '.data').toPath()

if (writing) {
    var temporary = Files.createTempFile(directory.toPath(), 'original-', '.tmp')
    try {
        var stream = upload.getInputStream()
        try {
            Files.copy(stream, temporary, StandardCopyOption.REPLACE_EXISTING)
        } finally { stream.close() }
        if (Files.size(temporary) != upload.getSize()) {
            throw new IllegalStateException('原件写入长度不一致')
        }
        // 先完整落盘再移动，同名文件不可覆盖，数据库登记由上传接口执行。
        Files.move(temporary, target)
    } finally { Files.deleteIfExists(temporary) }
    return true
}
if (operation == 'discard') {
    // 仅供上传登记失败时清除本次写入的文件。
    return Files.deleteIfExists(target)
}
if (operation != 'open' || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
    throw new IllegalStateException('原件不可读取')
}
return new BufferedInputStream(Files.newInputStream(target, LinkOption.NOFOLLOW_LINKS))
