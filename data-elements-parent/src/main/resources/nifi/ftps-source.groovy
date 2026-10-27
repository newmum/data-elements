import org.apache.commons.net.ftp.FTPSClient
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPReply
import org.apache.nifi.components.state.Scope
import org.apache.nifi.processor.io.OutputStreamCallback
import javax.net.ssl.TrustManagerFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.time.Duration

// Secrets are sensitive dynamic properties, never interpolated into Script Body.
def option = { String key, String fallback -> context.getProperty('ftps.' + key)?.value ?: fallback }
int port = option('port', '990') as int
boolean implicit = option('implicit', 'auto') == 'auto' ? port == 990 : option('implicit', 'auto').toBoolean()
def client = new FTPSClient('TLS', implicit)
client.setConnectTimeout(30000)
client.setDefaultTimeout(30000)
client.setDataTimeout(Duration.ofSeconds(60))
client.setEndpointCheckingEnabled(true)
def trust = TrustManagerFactory.getInstance(TrustManagerFactory.defaultAlgorithm)
KeyStore store = null
if (option('trustStorePath', '')) {
    store = KeyStore.getInstance(KeyStore.defaultType)
    new File(option('trustStorePath', '')).withInputStream { input ->
        store.load(input, option('trustStorePassword', '').toCharArray())
    }
}
if (store == null) trust.init((KeyStore)null)
else trust.init(store)
client.setTrustManager(trust.trustManagers.find { it instanceof javax.net.ssl.X509TrustManager })
String root = option('path', '/').replaceAll('/+$', '') ?: '/'
if (!root.startsWith('/') || root.tokenize('/').contains('..') || root.contains('\r') || root.contains('\n'))
    throw new IllegalArgumentException('FTPS 根目录必须是有效绝对路径')
def joined = { String directory, String name -> (directory == '/' ? '' : directory) + '/' + name }
def safeName = { String name -> name && !['.', '..'].contains(name) && !name.contains('/') && !name.contains('\\') && !name.contains('\r') && !name.contains('\n') }
def stamp = { file -> file?.timestamp?.timeInMillis ?: 0L }
def identity = { String name, file -> MessageDigest.getInstance('SHA-256')
    .digest((name + ':' + file.size + ':' + stamp(file)).getBytes('UTF-8')).encodeHex().toString() }
def stat = { String path ->
    def file = client.mlistFile(path)
    if (file) return file
    def files = client.listFiles(path)
    if (!FTPReply.isPositiveCompletion(client.replyCode)) throw new IOException('FTPS 文件状态读取失败')
    files.find { it.name == path.substring(path.lastIndexOf('/') + 1) }
}

try {
    client.connect(option('host', ''), port)
    if (!FTPReply.isPositiveCompletion(client.replyCode) || !client.login(option('username', ''), option('password', '')))
        throw new IOException('FTPS 连接或认证未通过')
    client.execPBSZ(0)
    client.execPROT('P')
    if (option('passive', 'true').toBoolean()) client.enterLocalPassiveMode()
    else client.enterLocalActiveMode()
    client.setFileType(FTP.BINARY_FILE_TYPE)

    if (option('operation', 'fetch') == 'acknowledge') {
        def flowFile = session.get()
        if (!flowFile) return
        String directory = flowFile.getAttribute('ingestion.file.path')
        String name = flowFile.getAttribute('ingestion.file.name')
        if (!safeName(name) || !(directory == root || directory?.startsWith(root == '/' ? '/' : root + '/')))
            throw new IOException('FTPS 文件确认范围与登记根目录不一致')
        String remote = joined(directory, name)
        def current = stat(remote)
        if (current && (current.size.toString() != flowFile.getAttribute('ingestion.file.size')
                || stamp(current).toString() != flowFile.getAttribute('ingestion.file.modified')))
            throw new IOException('FTPS 来源文件已变更，保留文件并停止删除确认')
        if (current && !client.deleteFile(remote)) throw new IOException('FTPS 数据文件删除确认失败')
        String marker = remote + '.done'
        def completion = stat(marker)
        if (completion && stamp(completion).toString() != flowFile.getAttribute('ingestion.marker.modified'))
            throw new IOException('FTPS 完成标记已变更，保留标记')
        if (completion && !client.deleteFile(marker)) throw new IOException('FTPS 完成标记删除确认失败')
        session.transfer(flowFile, REL_SUCCESS)
        return
    }

    def snapshot = context.stateManager.getState(Scope.CLUSTER)
    long watermark = (snapshot.toMap()['latest.timestamp'] ?: '0') as long
    Set<String> boundary = new HashSet<>((snapshot.toMap()['latest.identities'] ?: '').tokenize(','))
    boolean completedOnly = option('completedOnly', 'false').toBoolean()
    def filter = java.util.regex.Pattern.compile(option('filter', '(?i).*\\.(csv|json|xlsx|xls|txt)$'))
    def pending = [[path: root, depth: 0]]
    def candidates = []
    while (pending) {
        def directory = pending.remove(0)
        def files = client.listFiles(directory.path)
        if (!FTPReply.isPositiveCompletion(client.replyCode)) throw new IOException('FTPS 目录读取失败')
        files.each { file ->
            if (!safeName(file.name) || file.symbolicLink) return
            String remote = joined(directory.path, file.name)
            if (file.directory) {
                if (option('recursive', 'false').toBoolean()) {
                    if (directory.depth >= 32) throw new IOException('FTPS 目录层级超过32层')
                    pending.add([path: remote, depth: directory.depth + 1])
                }
                return
            }
            String dataName = completedOnly && file.name.endsWith('.done') ? file.name.substring(0,file.name.length()-5) : file.name
            if (!file.file || (completedOnly && !file.name.endsWith('.done')) || !filter.matcher(dataName).matches()) return
            long modified = stamp(file)
            if (modified <= 0) throw new IOException('FTPS 文件缺少可用修改时间，无法维护抽取状态')
            String id = identity(remote, file)
            if (modified > watermark || (modified == watermark && !boundary.contains(id)))
                candidates.add([path: directory.path, name: dataName, file: file, timestamp: modified, id: id])
            if (candidates.size() > 100000) throw new IOException('FTPS 待抽取文件超过扫描上限，请缩小登记目录范围')
        }
    }
    candidates.sort { a, b -> (a.timestamp <=> b.timestamp) ?: (a.name <=> b.name) }
    def batch = candidates.take(Math.max(1, Math.min(option('maxFiles', '100') as int, 100)))
    if (!batch) { context.yield(); return }
    long nextTimestamp = watermark
    Set<String> nextBoundary = new HashSet<>(boundary)
    batch.each { candidate ->
        String remote = joined(candidate.path, candidate.name)
        def data = completedOnly ? stat(remote) : candidate.file
        def marker = completedOnly ? stat(remote + '.done') : null
        if (!data?.file) throw new IOException('FTPS 数据文件不存在，保留完成标记等待重试')
        def flowFile = session.create()
        flowFile = session.putAllAttributes(flowFile, [
            filename: candidate.name, path: candidate.path, 'absolute.path': remote,
            'ingestion.file.name': candidate.name, 'ingestion.file.path': candidate.path,
            'ingestion.file.size': data.size.toString(), 'ingestion.file.modified': stamp(data).toString(),
            'ingestion.marker.modified': completedOnly ? stamp(marker).toString() : ''
        ])
        flowFile = session.write(flowFile, { out ->
            if (!client.retrieveFile(remote, out)) throw new IOException('FTPS 文件下载失败')
        } as OutputStreamCallback)
        session.transfer(flowFile, REL_SUCCESS)
        if (candidate.timestamp > nextTimestamp) { nextTimestamp = candidate.timestamp; nextBoundary.clear() }
        nextBoundary.add(candidate.id)
        if (nextBoundary.size() > 10000) throw new IOException('FTPS 同时间戳文件超过状态上限，请拆分登记目录')
    }
    def nextState = ['latest.timestamp': nextTimestamp.toString(), 'latest.identities': nextBoundary.sort().join(',')]
    // Advance discovery state only after FlowFiles are durable. A failed state CAS may
    // replay files, but cannot discard downloaded data or delete source files.
    session.commitAsync({
        if (!context.stateManager.replace(snapshot, nextState, Scope.CLUSTER))
            log.warn('FTPS 抽取状态发生并发变化，下次轮询可能重放已持久化文件')
    } as Runnable)
} finally {
    try { if (client.connected) client.logout() } catch (Exception ignored) { }
    try { if (client.connected) client.disconnect() } catch (Exception ignored) { }
}
