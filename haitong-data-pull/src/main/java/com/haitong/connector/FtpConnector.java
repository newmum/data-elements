package com.haitong.connector;

import com.haitong.config.*;
import com.haitong.core.*;
import com.haitong.core.Models.*;
import org.apache.commons.net.ftp.*;
import com.jcraft.jsch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import javax.net.ssl.SSLContext;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.security.*;
import java.util.Base64;
import java.util.*;

@Component
public class FtpConnector {
    private static final Logger log=LoggerFactory.getLogger(FtpConnector.class);
    private final RelayProperties p;private final Secrets secrets;private final Json json;
    public FtpConnector(RelayProperties p,Secrets secrets,Json json) { this.p=p;this.secrets=secrets;this.json=json; }
    @FunctionalInterface private interface Operation<T> { T run(FTPClient ftp) throws Exception; }
    /** Mutable only within a single delivery thread; stores no credential or business-record data. */
    private static final class FtpTrace {
        final FtpAddress address;final String directory;final String remotePath;final String deliveryId;final List<Map<String,Object>> steps=new ArrayList<>();String stage="PREPARE";
        FtpTrace(FtpAddress address,String directory,String remotePath){this(address,directory,remotePath,"");}
        FtpTrace(FtpAddress address,String directory,String remotePath,String deliveryId){this.address=address;this.directory=directory;this.remotePath=remotePath;this.deliveryId=deliveryId;}
        void stage(String value){stage=value;}
    }
    /**
     * In relative mode the FTP session has already entered the target
     * directory, so upload commands must use a file name rather than an
     * absolute path. This supports FTP virtual roots that reject /path.
     */
    private record FtpDirectory(String configuredDirectory,boolean relativeMode) {
        String file(String name) { return relativeMode ? Rules.fileName(name) : configuredDirectory+"/"+Rules.fileName(name); }
        String mode() { return relativeMode ? "RELATIVE_VIRTUAL_ROOT" : "ABSOLUTE"; }
    }
    private <T> T session(FtpAddress address,Operation<T> action) throws Exception {
        return session(address,new FtpTrace(address,"",""),action);
    }
    private <T> T session(FtpAddress address,FtpTrace trace,Operation<T> action) throws Exception {
        FTPClient ftp;
        if(address.protocol().equals("FTPS")) {
            // Explicit JVM PKIX trust context; do not use a certificate-validity-only or trust-all manager.
            FTPSClient secure=new FTPSClient(address.implicitTls(),SSLContext.getDefault());
            secure.setEndpointCheckingEnabled(true);ftp=secure;
        } else ftp=new FTPClient();
        int timeout=address.timeoutSeconds()*1000;
        ftp.setControlEncoding(address.encoding());ftp.setConnectTimeout(timeout);ftp.setDefaultTimeout(timeout);
        ftp.setDataTimeout(Duration.ofSeconds(address.timeoutSeconds()));ftp.setControlKeepAliveTimeout(Duration.ofSeconds(10));
        ftp.setUseEPSVwithIPv4(true);ftp.setIpAddressFromPasvResponse(false);
        try {
            trace.stage("RESOLVE_CREDENTIALS");
            String password=secrets.resolve(p.getFtp().getPassword());
            log.info("FTP credentials loaded: deliveryId={}, username={}, password={}",trace.deliveryId,safeAccount(address.username()),maskedSecret(password));
            trace.stage("CONNECT");
            log.info("FTP connection begin: deliveryId={}, protocol={}, endpoint={}:{}, deliveryDirectory={}, remotePath={}",trace.deliveryId,address.protocol(),address.host(),address.port(),trace.directory,trace.remotePath);
            ftp.connect(address.host(),address.port());
            logFtpReply("CONNECT",trace,ftp);
            if(!FTPReply.isPositiveCompletion(ftp.getReplyCode())) throw ftpFailure("FTP_CONNECT_REJECTED",true,false,trace,ftp,null);
            trace.stage("LOGIN");
            if(!ftp.login(address.username(),password)) throw ftpFailure("FTP_AUTH_FAILED",true,false,trace,ftp,null);
            logFtpReply("LOGIN",trace,ftp);
            if(ftp instanceof FTPSClient secure) { trace.stage("FTPS_PROTECTION");secure.execPBSZ(0);secure.execPROT("P");logFtpReply("FTPS_PROTECTION",trace,ftp); }
            trace.stage("DATA_MODE");
            if(address.passive()) ftp.enterLocalPassiveMode();else ftp.enterLocalActiveMode();
            trace.stage("BINARY_MODE");
            if(!ftp.setFileType(FTP.BINARY_FILE_TYPE)) throw ftpFailure("FTP_BINARY_MODE_FAILED",false,false,trace,ftp,null);
            logFtpReply("BINARY_MODE",trace,ftp);
            log.info("FTP connection ready: deliveryId={}, protocol={}, endpoint={}:{}, username={}, passive={}, deliveryDirectory={}",trace.deliveryId,address.protocol(),address.host(),address.port(),safeAccount(address.username()),address.passive(),trace.directory);
            trace.stage("DELIVERY");
            return action.run(ftp);
        } catch(DeliveryFailure e) { throw e;
        } catch(Exception e) { throw ftpFailure("FTP_"+trace.stage+"_FAILED",false,false,trace,ftp,e);
        } finally {
            if(ftp.isConnected()) { try { ftp.logout();log.info("FTP disconnected: deliveryId={}, endpoint={}:{}",trace.deliveryId,address.host(),address.port()); } catch(IOException ignored) {} try { ftp.disconnect(); } catch(IOException ignored) {} }
        }
    }
    public Map<String,Object> deliver(TargetPlan target,Packet packet) throws Exception {
        boolean collectionFile=target.kind()==Kind.FILE_FTP;
        if(collectionFile&&!packet.isFile())throw new DeliveryFailure("FILE_REFERENCE_REQUIRED",true,false);
        String sourceId=metadataCode(packet,"datasourceId");
        String tableName=metadataCode(packet,collectionFile?"collectionId":"tableName");
        // Registered table-batch output is deliberately predictable: root / source datasource id / table name.
        // It contains only business rows; routing metadata remains in the sidecar completion manifest.
        String directory=Rules.remoteDirectory(target.directory())+"/"+sourceId+"/"+tableName;
        String name=packet.messageId()+(packet.isFile()?Rules.extension(packet.originalName()):".json");
        byte[] bytes=packet.isFile()?null:recordsJson(packet);
        Path file=packet.isFile()?safePacketPath(packet.fileRef()):null;
        long length=file==null?bytes.length:Files.size(file);
        String hash=file==null?Rules.sha256(bytes):PayloadFiles.sha256(file);
        String path=directory+"/"+name;
        LinkedHashMap<String,Object> manifest=new LinkedHashMap<>();
        manifest.put("fileId",collectionFile?packet.metadata().get("fileId"):packet.deliveryId());manifest.put("messageId",packet.messageId());manifest.put("fileName",name);
        manifest.put("format",packet.isFile()?"BINARY":"JSON");manifest.put("size",length);manifest.put("sha256",hash);
        manifest.put("datasourceId",sourceId);
        if(collectionFile){manifest.put("collectionId",tableName);manifest.put("deliveryId",packet.deliveryId());manifest.put("originalName",packet.originalName());
            for(String key:List.of("contractVersion","businessKey","metadata","mediaType","securityState","contentUrl"))if(packet.metadata().containsKey(key))manifest.put(key,packet.metadata().get(key));
        }else{manifest.put("tableName",tableName);manifest.put("schemaVersion",packet.metadata().get("schemaVersion"));}
        if(!packet.isFile())manifest.put("recordCount",recordCount(packet));
        byte[] done=json.bytes(manifest);
        FtpTrace trace=new FtpTrace(target.ftp(),directory,path,packet.deliveryId());
        log.info("FTP delivery begin: deliveryId={}, protocol={}, endpoint={}:{}, username={}, directory={}, remotePath={}, bytes={}, marker={}",packet.deliveryId(),target.ftp().protocol(),target.ftp().host(),target.ftp().port(),safeAccount(target.ftp().username()),directory,path,length,path+".done");
        if ("SFTP".equals(target.ftp().protocol())) {
            return deliverSftp(target.ftp(), directory, name, path, bytes, file, length, hash, done,packet.deliveryId(),trace);
        }
        return session(target.ftp(),trace,ftp->{
            trace.stage("DIRECTORY");
            FtpDirectory transferDirectory=ensureDirectory(ftp,directory,true,trace);
            String transferPath=transferDirectory.file(name);
            log.info("FTP directory ready: deliveryId={}, configuredDirectory={}, pathMode={}, transferPath={}",
                    packet.deliveryId(),directory,transferDirectory.mode(),transferPath);
            trace.stage("CHECK_PAYLOAD");
            if(exists(ftp,transferPath)) { trace.stage("VERIFY_EXISTING_PAYLOAD");verify(ftp,transferPath,length,hash); }
            else {
                String temp=transferPath+"."+UUID.randomUUID()+".tmp";
                try {
                    try(InputStream in=file==null?new ByteArrayInputStream(bytes):Files.newInputStream(file)) {
                        trace.stage("UPLOAD_PAYLOAD");
                        log.info("FTP payload upload begin: deliveryId={}, temporaryPath={}, bytes={}",packet.deliveryId(),temp,length);
                        if(!ftp.storeFile(temp,in)) throw new IOException("FTP_UPLOAD_FAILED");
                        logFtpReply("UPLOAD_PAYLOAD",trace,ftp);
                    }
                    trace.stage("VERIFY_TEMP_PAYLOAD");
                    verify(ftp,temp,length,hash);
                    // Stable UUID names and immutable payloads make concurrent recovery attempts converge.
                    trace.stage("CHECK_FINAL_PAYLOAD");
                    if(exists(ftp,transferPath)) { trace.stage("VERIFY_FINAL_PAYLOAD");verify(ftp,transferPath,length,hash); }
                    else { trace.stage("RENAME_PAYLOAD");if(!ftp.rename(temp,transferPath)) throw new IOException("FTP_RENAME_FAILED");logFtpReply("RENAME_PAYLOAD",trace,ftp); }
                } finally { try { ftp.deleteFile(temp); } catch(IOException ignored) {} }
            }
            trace.stage("PUBLISH_MARKER");
            publishBytes(ftp,transferPath+".done",done,trace);
            Map<String,Object> result=Map.of("fileName",name,"path",path,"sha256",hash,"bytes",length,"completionMarker",name+".done","completion","FILE_AND_MARKER_VERIFIED");
            log.info("FTP delivery verified: deliveryId={}, endpoint={}:{}, username={}, configuredRemotePath={}, transferPath={}, pathMode={}, bytes={}, marker={}",packet.deliveryId(),target.ftp().host(),target.ftp().port(),safeAccount(target.ftp().username()),path,transferPath,transferDirectory.mode(),length,path+".done");
            return result;
        });
    }
    /**
     * Privileged, configuration-only diagnostic used by the Magic-API FTP probe.
     * It never accepts a host, account or password from HTTP; the configured FTP
     * target is the only target it can reach. Test files have a generated name
     * and can be deleted immediately after verification.
     */
    public Map<String,Object> diagnose(String requestedDirectory,boolean createDirectories,boolean uploadTest,boolean cleanupTest) {
        var configured=p.getFtp();
        FtpAddress address=new FtpAddress(configured.getProtocol(),configured.getHost(),configured.getPort(),configured.getUsername(),configured.isPassive(),configured.isImplicitTls(),configured.getEncoding(),configured.getTimeoutSeconds());
        String base=Rules.remoteDirectory(configured.getOutputDir());
        String directory=Rules.remoteDirectory(requestedDirectory==null || requestedDirectory.isBlank()?base:requestedDirectory);
        if(!(directory.equals(base) || base.equals("/") || directory.startsWith(base+"/"))) throw RelayException.invalid("诊断目录只能是当前 FTP 输出目录或其下级目录");
        LinkedHashMap<String,Object> result=new LinkedHashMap<>();
        result.put("protocol",address.protocol());result.put("endpoint",address.host()+":"+address.port());result.put("username",safeAccount(address.username()));
        result.put("configuredOutputDir",base);result.put("directory",directory);result.put("createDirectories",createDirectories);result.put("uploadTest",uploadTest);result.put("cleanupTestFile",cleanupTest);
        if(!Set.of("FTP","FTPS").contains(address.protocol())) {
            result.put("passed",false);result.put("code","FTP_DIAGNOSTIC_PROTOCOL_NOT_SUPPORTED");
            result.put("message","当前诊断接口仅支持 FTP 或 FTPS；SFTP 请使用专用 SFTP 诊断流程");result.put("steps",List.of());return result;
        }
        FtpTrace trace=new FtpTrace(address,directory,"","magic-ftp-diagnostic-"+UUID.randomUUID());
        try {
            session(address,trace,ftp->{
                trace.stage("DIAGNOSTIC_DIRECTORY");
                FtpDirectory transferDirectory=diagnoseDirectory(ftp,directory,createDirectories,trace);
                result.put("pathMode",transferDirectory.mode());
                result.put("effectiveTransferDirectory",transferDirectory.relativeMode()?"<FTP current working directory>":directory);
                if(uploadTest) diagnoseUpload(ftp,transferDirectory,cleanupTest,trace);
                return null;
            });
            result.put("passed",true);result.put("code","FTP_DIAGNOSTIC_PASSED");result.put("message","FTP 连接、目录检查和已请求的测试操作均成功");
        } catch(DeliveryFailure e) {
            result.put("passed",false);result.put("code",e.code);result.put("message","FTP 诊断操作失败");result.put("diagnostics",e.diagnostics);
        } catch(Exception e) {
            result.put("passed",false);result.put("code","FTP_DIAGNOSTIC_FAILED");result.put("message","FTP 诊断操作失败");
        }
        result.put("steps",List.copyOf(trace.steps));return result;
    }
    private FtpDirectory diagnoseDirectory(FTPClient ftp,String directory,boolean createDirectories,FtpTrace trace) throws Exception {
        return ensureDirectory(ftp,directory,createDirectories,trace);
    }
    private void diagnoseUpload(FTPClient ftp,FtpDirectory directory,boolean cleanupTest,FtpTrace trace) throws Exception {
        String fileName="__haitong_ftp_probe_"+UUID.randomUUID()+".txt";
        String path=directory.file(fileName);
        byte[] body=("haitong-data-pull FTP diagnostic; timestamp="+java.time.Instant.now()).getBytes(StandardCharsets.UTF_8);
        trace.stage("DIAGNOSTIC_UPLOAD");
        boolean uploaded=ftp.storeFile(path,new ByteArrayInputStream(body));recordFtpStep(trace,"STOR",path,uploaded,ftp);
        if(!uploaded) throw ftpFailure("FTP_TEST_UPLOAD_FAILED",false,false,trace,ftp,new IOException("FTP_TEST_UPLOAD_FAILED"));
        trace.stage("DIAGNOSTIC_VERIFY");
        try { verify(ftp,path,body.length,Rules.sha256(body));recordFtpStep(trace,"RETR_VERIFY",path,true,ftp); }
        catch(Exception e) { recordFtpStep(trace,"RETR_VERIFY",path,false,ftp);throw e; }
        if(cleanupTest) {
            trace.stage("DIAGNOSTIC_CLEANUP");
            boolean deleted=ftp.deleteFile(path);recordFtpStep(trace,"DELE",path,deleted,ftp);
            if(!deleted) throw ftpFailure("FTP_TEST_CLEANUP_FAILED",false,false,trace,ftp,new IOException("FTP_TEST_CLEANUP_FAILED"));
        }
    }
    private Map<String,Object> deliverSftp(FtpAddress address,String directory,String name,String path,byte[] bytes,Path file,long length,String hash,byte[] done,String deliveryId,FtpTrace trace) throws Exception {
        return sftpSession(address,trace,sftp->{
            trace.stage("DIRECTORY");
            ensureDirectory(sftp,directory);
            trace.stage("CHECK_PAYLOAD");
            if(sftpExists(sftp,path)) { trace.stage("VERIFY_EXISTING_PAYLOAD");verify(sftp,path,length,hash); }
            else {
                String temp=path+"."+UUID.randomUUID()+".tmp";
                try {
                    trace.stage("UPLOAD_PAYLOAD");
                    if(file==null) sftp.put(new ByteArrayInputStream(bytes),temp,ChannelSftp.OVERWRITE);
                    else try(InputStream in=Files.newInputStream(file)) { sftp.put(in,temp,ChannelSftp.OVERWRITE); }
                    trace.stage("VERIFY_TEMP_PAYLOAD");
                    verify(sftp,temp,length,hash);
                    trace.stage("CHECK_FINAL_PAYLOAD");
                    if(sftpExists(sftp,path)) { trace.stage("VERIFY_FINAL_PAYLOAD");verify(sftp,path,length,hash); }
                    else { trace.stage("RENAME_PAYLOAD");sftp.rename(temp,path); }
                } finally { try { sftp.rm(temp); } catch(SftpException ignored) {} }
            }
            trace.stage("PUBLISH_MARKER");
            publishBytes(sftp,path+".done",done);
            Map<String,Object> result=Map.of("fileName",name,"path",path,"sha256",hash,"bytes",length,"completionMarker",name+".done","completion","FILE_AND_MARKER_VERIFIED");
            log.info("SFTP delivery verified: deliveryId={}, remotePath={}, bytes={}, marker={}",deliveryId,path,length,path+".done");
            return result;
        });
    }
    private byte[] recordsJson(Packet packet) {
        Map<String,Object> envelope=json.object(packet.bytes());
        Object records=envelope.get("records");
        if(!(records instanceof List<?>)) throw RelayException.invalid("登记数据推送记录不存在");
        return json.bytes(records);
    }
    private int recordCount(Packet packet) {
        Object records=json.object(packet.bytes()).get("records");
        return records instanceof List<?> rows?rows.size():0;
    }
    private String metadataCode(Packet packet,String key) {
        Object value=packet.metadata().get(key);
        String text=value==null?"":String.valueOf(value).trim();
        if(!text.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,254}")) throw RelayException.invalid("登记推送元数据无效："+key);
        return text;
    }
    /** Kept for the generic local/legacy routes; registered FTP batches use recordsJson instead. */
    public static byte[] jsonl(byte[] bytes) { byte[] out=Arrays.copyOf(bytes,bytes.length+1);out[out.length-1]='\n';return out; }
    private Path safePacketPath(String reference) throws IOException {
        Path root=Path.of(p.getWorkDir()).toRealPath();Path file=Path.of(reference).toRealPath();
        if(!file.startsWith(root)) throw new IOException("SPOOL_PATH_OUTSIDE_WORK_DIRECTORY");return file;
    }
    /**
     * FTP servers frequently expose a chroot/virtual root. Such servers may
     * accept "MKD child" but reject "MKD /child" with 550 Invalid directory
     * name. Try configured absolute semantics first, then safely fall back to
     * progressive relative CWD/MKD from the logged-in virtual root when the
     * very first absolute segment is rejected.
     */
    private FtpDirectory ensureDirectory(FTPClient ftp,String dir,boolean createDirectories,FtpTrace trace) throws IOException {
        String[] parts=Arrays.stream(Rules.remoteDirectory(dir).split("/")).filter(part->!part.isEmpty()).toArray(String[]::new);
        String current="";
        for(int index=0;index<parts.length;index++) {
            current+="/"+parts[index];
            trace.stage("DIRECTORY_CWD_ABSOLUTE");
            boolean entered=ftp.changeWorkingDirectory(current);directoryStep(trace,"CWD_ABSOLUTE",current,entered,ftp);
            if(entered) continue;
            if(!createDirectories) {
                if(index==0) return ensureRelativeDirectory(ftp,parts,createDirectories,trace);
                throw new IOException("FTP_DIRECTORY_UNAVAILABLE");
            }
            trace.stage("DIRECTORY_MKD_ABSOLUTE");
            boolean created=ftp.makeDirectory(current);directoryStep(trace,"MKD_ABSOLUTE",current,created,ftp);
            if(created) {
                trace.stage("DIRECTORY_CWD_AFTER_MKD_ABSOLUTE");
                boolean enteredAfterCreate=ftp.changeWorkingDirectory(current);directoryStep(trace,"CWD_AFTER_MKD_ABSOLUTE",current,enteredAfterCreate,ftp);
                if(!enteredAfterCreate) throw new IOException("FTP_DIRECTORY_UNAVAILABLE");
                continue;
            }
            if(index==0) {
                log.warn("FTP absolute path was rejected; retrying from the FTP virtual root: deliveryId={}, configuredDirectory={}, replyCode={}, replyText={}",
                        trace.deliveryId,dir,ftp.getReplyCode(),safeText(ftp.getReplyString(),240));
                return ensureRelativeDirectory(ftp,parts,true,trace);
            }
            throw new IOException("FTP_DIRECTORY_UNAVAILABLE");
        }
        return new FtpDirectory(Rules.remoteDirectory(dir),false);
    }
    private FtpDirectory ensureRelativeDirectory(FTPClient ftp,String[] parts,boolean createDirectories,FtpTrace trace) throws IOException {
        String visible="";
        for(String part:parts) {
            visible=visible.isEmpty()?part:visible+"/"+part;
            trace.stage("DIRECTORY_CWD_RELATIVE");
            boolean entered=ftp.changeWorkingDirectory(part);directoryStep(trace,"CWD_RELATIVE",visible,entered,ftp);
            if(entered) continue;
            if(!createDirectories) throw new IOException("FTP_DIRECTORY_UNAVAILABLE");
            trace.stage("DIRECTORY_MKD_RELATIVE");
            boolean created=ftp.makeDirectory(part);directoryStep(trace,"MKD_RELATIVE",visible,created,ftp);
            if(!created) throw new IOException("FTP_DIRECTORY_UNAVAILABLE");
            trace.stage("DIRECTORY_CWD_AFTER_MKD_RELATIVE");
            boolean enteredAfterCreate=ftp.changeWorkingDirectory(part);directoryStep(trace,"CWD_AFTER_MKD_RELATIVE",visible,enteredAfterCreate,ftp);
            if(!enteredAfterCreate) throw new IOException("FTP_DIRECTORY_UNAVAILABLE");
        }
        log.info("FTP virtual-root directory ready: deliveryId={}, directory={}",trace.deliveryId,visible);
        return new FtpDirectory(Rules.remoteDirectory(String.join("/",parts)),true);
    }
    @FunctionalInterface private interface SftpOperation<T> { T run(ChannelSftp sftp) throws Exception; }
    private <T> T sftpSession(FtpAddress address,SftpOperation<T> action) throws Exception { return sftpSession(address,new FtpTrace(address,"",""),action); }
    private <T> T sftpSession(FtpAddress address,FtpTrace trace,SftpOperation<T> action) throws Exception {
        Path knownHostFile=sftpKnownHostFile(address);
        boolean pinned=Files.isRegularFile(knownHostFile);
        JSch jsch=new JSch();
        if(pinned) jsch.setKnownHosts(knownHostFile.toString());
        Session ssh=jsch.getSession(address.username(),address.host(),address.port());
        String password=secrets.resolve(p.getFtp().getPassword());
        ssh.setPassword(password);
        // The first connection to a source explicitly selected in the data platform is pinned locally.
        // All later connections require that same host key; a configured fingerprint is always checked.
        ssh.setConfig("StrictHostKeyChecking",pinned?"yes":"no");
        int timeout=address.timeoutSeconds()*1000;
        ChannelSftp sftp=null;
        try {
            trace.stage("CONNECT");
            log.info("SFTP connection begin: deliveryId={}, endpoint={}:{}, username={}, password={}, deliveryDirectory={}, remotePath={}",trace.deliveryId,address.host(),address.port(),safeAccount(address.username()),maskedSecret(password),trace.directory,trace.remotePath);
            ssh.connect(timeout);
            trace.stage("HOST_KEY");
            verifyAndPinSftpHostKey(ssh,knownHostFile,pinned);
            trace.stage("SFTP_CHANNEL");
            sftp=(ChannelSftp)ssh.openChannel("sftp");
            sftp.connect(timeout);
            log.info("SFTP connection ready: deliveryId={}, endpoint={}:{}, username={}, deliveryDirectory={}",trace.deliveryId,address.host(),address.port(),safeAccount(address.username()),trace.directory);
            trace.stage("DELIVERY");
            return action.run(sftp);
        } catch(DeliveryFailure e) { throw e;
        } catch(Exception e) { throw ftpFailure("SFTP_"+trace.stage+"_FAILED",false,false,trace,null,e);
        } finally {
            if(sftp!=null && sftp.isConnected()) sftp.disconnect();
            if(ssh.isConnected()) ssh.disconnect();
        }
    }
    private Path sftpKnownHostFile(FtpAddress address) {
        String id=Rules.sha256((address.host()+":"+address.port()).getBytes(StandardCharsets.UTF_8)).substring(0,24);
        return Path.of(p.getWorkDir()).resolve("sftp-known-hosts").resolve(id+".known_hosts");
    }
    private void verifyAndPinSftpHostKey(Session ssh,Path knownHostFile,boolean pinned) throws Exception {
        HostKey hostKey=ssh.getHostKey();
        String fingerprint="SHA256:"+Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(hostKey.getKey())));
        String expected=p.getFtp().getSftpHostKeyFingerprint()==null?"":p.getFtp().getSftpHostKeyFingerprint().trim();
        if(!expected.isBlank() && !fingerprint.equals(expected)) throw new DeliveryFailure("SFTP_HOST_KEY_MISMATCH",true,false);
        if(!pinned) {
            Files.createDirectories(knownHostFile.getParent());
            Files.writeString(knownHostFile,hostKey.getHost()+" "+hostKey.getType()+" "+hostKey.getKey()+System.lineSeparator(),StandardCharsets.US_ASCII,StandardOpenOption.CREATE_NEW);
        }
    }
    private void ensureDirectory(ChannelSftp sftp,String dir) throws Exception {
        String current="";
        for(String part:Rules.remoteDirectory(dir).split("/")) if(!part.isEmpty()) {
            current+="/"+part;
            try { sftp.stat(current); }
            catch(SftpException missing) { if(missing.id!=ChannelSftp.SSH_FX_NO_SUCH_FILE) throw missing; sftp.mkdir(current); }
        }
    }
    private boolean sftpExists(ChannelSftp sftp,String path) throws Exception {
        try { SftpATTRS attrs=sftp.lstat(path); if(attrs.isDir()) throw new IOException("FTP_PATH_NOT_REGULAR_FILE"); return true; }
        catch(SftpException missing) { if(missing.id==ChannelSftp.SSH_FX_NO_SUCH_FILE) return false; throw missing; }
    }
    private void publishBytes(ChannelSftp sftp,String path,byte[] bytes) throws Exception {
        String hash=Rules.sha256(bytes);
        if(sftpExists(sftp,path)) { verify(sftp,path,bytes.length,hash); return; }
        String temp=path+"."+UUID.randomUUID()+".tmp";
        try {
            sftp.put(new ByteArrayInputStream(bytes),temp,ChannelSftp.OVERWRITE);
            verify(sftp,temp,bytes.length,hash);
            if(sftpExists(sftp,path)) verify(sftp,path,bytes.length,hash); else sftp.rename(temp,path);
        } finally { try { sftp.rm(temp); } catch(SftpException ignored) {} }
    }
    private void verify(ChannelSftp sftp,String path,long expected,String hash) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");long length=0;
        try(InputStream in=sftp.get(path)) {
            byte[] buffer=new byte[8192];int read;
            while((read=in.read(buffer))>=0) { length+=read;if(length>expected) throw new DeliveryFailure("FTP_CONTENT_CONFLICT",true,false);digest.update(buffer,0,read); }
        }
        if(length!=expected || !HexFormat.of().formatHex(digest.digest()).equals(hash)) throw new DeliveryFailure("FTP_CONTENT_CONFLICT",true,false);
    }
    private boolean exists(FTPClient ftp,String path) throws IOException {
        FTPFile[] files=ftp.listFiles(path);
        int replyCode=ftp.getReplyCode();
        String reply=ftp.getReplyString()==null?"":ftp.getReplyString().toLowerCase(Locale.ROOT);
        // Apache FtpServer and several FTP appliances use 450 (rather than
        // 550) for an absent file.  Treat only an explicit not-found reply as
        // absent; a transient/permission-related 450 must still fail safely.
        boolean notFound=replyCode==550 || replyCode==450 && (
                reply.contains("non-existing") || reply.contains("not found") || reply.contains("no such file"));
        if(replyCode>=400 && !notFound) throw new IOException("FTP_LIST_FAILED");
        if(files.length==0) return false;
        if(files.length!=1 || !files[0].isFile()) throw new IOException("FTP_PATH_NOT_REGULAR_FILE");
        return true;
    }
    private void publishBytes(FTPClient ftp,String path,byte[] bytes,FtpTrace trace) throws Exception {
        String hash=Rules.sha256(bytes);
        trace.stage("CHECK_MARKER");
        if(exists(ftp,path)) {trace.stage("VERIFY_EXISTING_MARKER");verify(ftp,path,bytes.length,hash);return;}
        String temp=path+"."+UUID.randomUUID()+".tmp";
        try {
            trace.stage("UPLOAD_MARKER");
            if(!ftp.storeFile(temp,new ByteArrayInputStream(bytes))) throw new IOException("FTP_MARKER_UPLOAD_FAILED");
            trace.stage("VERIFY_TEMP_MARKER");
            verify(ftp,temp,bytes.length,hash);
            trace.stage("CHECK_FINAL_MARKER");
            if(exists(ftp,path)) {trace.stage("VERIFY_FINAL_MARKER");verify(ftp,path,bytes.length,hash);}
            else {trace.stage("RENAME_MARKER");if(!ftp.rename(temp,path)) throw new IOException("FTP_MARKER_RENAME_FAILED");}
        } finally {try {ftp.deleteFile(temp);} catch(IOException ignored) {}}
    }
    private void verify(FTPClient ftp,String path,long expected,String hash) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");long[] length={0};
        OutputStream sink=new OutputStream() {
            @Override public void write(int b) throws IOException { write(new byte[]{(byte)b},0,1); }
            @Override public void write(byte[] b,int off,int n) throws IOException {
                length[0]+=n;if(length[0]>expected) throw new IOException("FTP_CONTENT_LENGTH_CONFLICT");digest.update(b,off,n);
            }
        };
        try {
            if(!ftp.retrieveFile(path,sink)) throw new IOException("FTP_VERIFY_READ_FAILED");
        } catch(IOException e) {
            if(length[0]>expected) throw new DeliveryFailure("FTP_CONTENT_CONFLICT",true,false,e);throw e;
        }
        if(length[0]!=expected || !HexFormat.of().formatHex(digest.digest()).equals(hash)) throw new DeliveryFailure("FTP_CONTENT_CONFLICT",true,false);
    }
    public List<String> markers(FtpAddress address,String dir) throws Exception {
        return session(address,ftp->{
            FTPFile[] listing=ftp.listFiles(Rules.remoteDirectory(dir));
            if(ftp.getReplyCode()>=400) throw new IOException("FTP_INPUT_DIRECTORY_UNAVAILABLE");
            return Arrays.stream(listing).filter(FTPFile::isFile).map(FTPFile::getName).filter(n->n.endsWith(".done"))
                .map(Rules::fileName).sorted().toList();
        });
    }
    public byte[] marker(FtpAddress address,String dir,String name) throws Exception {
        String path=Rules.remoteDirectory(dir)+"/"+Rules.fileName(name);
        return session(address,ftp->{
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            OutputStream bounded=new OutputStream() {
                private int count;
                @Override public void write(int b) throws IOException { if(++count>65536) throw new IOException("FTP_MARKER_TOO_LARGE");out.write(b); }
                @Override public void write(byte[] b,int off,int n) throws IOException {count+=n;if(count>65536) throw new IOException("FTP_MARKER_TOO_LARGE");out.write(b,off,n);}
            };
            if(!ftp.retrieveFile(path,bounded)) throw new IOException("FTP_MARKER_READ_FAILED");
            return out.toByteArray();
        });
    }
    public PayloadFiles.Saved download(FtpAddress address,String dir,String name,long maxBytes) throws Exception {
        return session(address,ftp->{
            InputStream input=ftp.retrieveFileStream(Rules.remoteDirectory(dir)+"/"+Rules.fileName(name));
            if(input==null) throw new IOException("FTP_DOWNLOAD_FAILED");
            PayloadFiles.Saved saved;
            try(input) { saved=PayloadFiles.save(Path.of(p.getWorkDir()).resolve("spool"),input,maxBytes,p.getLimits().getMinFreeDiskBytes()); }
            if(!ftp.completePendingCommand()) throw new IOException("FTP_DOWNLOAD_NOT_COMPLETED");
            return saved;
        });
    }
    public Map<String,Object> health(FtpAddress address) throws Exception {
        if("SFTP".equals(address.protocol())) return sftpSession(address,sftp->{sftp.pwd();return Map.of("connector","sftp","status","UP","test","host key, login and pwd; no upload/delete");});
        return session(address,ftp->{if(ftp.printWorkingDirectory()==null) throw new IOException("FTP_PWD_FAILED");return Map.of("connector","ftp","status","UP","test","login and pwd; no upload/delete");});
    }
    private DeliveryFailure ftpFailure(String code,boolean permanent,boolean unknown,FtpTrace trace,FTPClient ftp,Throwable cause) {
        Map<String,Object> detail=new LinkedHashMap<>();
        detail.put("connector",trace.address.protocol().equals("SFTP")?"sftp":"ftp");
        detail.put("stage",trace.stage);
        detail.put("endpoint",trace.address.host()+":"+trace.address.port());
        if(!trace.directory.isBlank()) detail.put("directory",trace.directory);
        if(!trace.remotePath.isBlank()) detail.put("remotePath",trace.remotePath);
        detail.put("username",safeAccount(trace.address.username()));
        if(!trace.steps.isEmpty()) detail.put("steps",List.copyOf(trace.steps));
        if(ftp!=null) { detail.put("ftpReplyCode",ftp.getReplyCode());detail.put("ftpReply",safeText(ftp.getReplyString(),240)); }
        if(cause!=null) {detail.put("exceptionClass",cause.getClass().getSimpleName());detail.put("exceptionMessage",safeText(cause.getMessage(),240));}
        return new DeliveryFailure(code,permanent,unknown,cause,detail);
    }
    private String safeText(String value,int max) {
        if(value==null) return "";
        String result=value.replaceAll("[\\p{Cntrl}]"," ").trim();
        result=result.replaceAll("(?i)(password|passwd|secret|token|api[-_]?key)\\s*[:=]\\s*\\S+","$1=[REDACTED]");
        return result.length()<=max?result:result.substring(0,max);
    }
    private String safeAccount(String account) {
        return safeText(account==null?"":account,120);
    }
    private String maskedSecret(String secret) {
        if(secret==null || secret.isBlank()) return "[NOT_CONFIGURED]";
        return "[REDACTED,length="+secret.length()+"]";
    }
    private void logFtpReply(String operation,FtpTrace trace,FTPClient ftp) {
        log.info("FTP server reply: deliveryId={}, stage={}, operation={}, replyCode={}, replyText={}",trace.deliveryId,trace.stage,operation,ftp.getReplyCode(),safeText(ftp.getReplyString(),240));
    }
    private void directoryStep(FtpTrace trace,String operation,String path,boolean passed,FTPClient ftp) {
        if(trace.deliveryId.startsWith("magic-ftp-diagnostic-")) {
            recordFtpStep(trace,operation,path,passed,ftp);
            return;
        }
        log.info("FTP directory step: deliveryId={}, operation={}, path={}, passed={}, replyCode={}, replyText={}",
                trace.deliveryId,operation,path,passed,ftp.getReplyCode(),safeText(ftp.getReplyString(),240));
    }
    private void recordFtpStep(FtpTrace trace,String operation,String path,boolean passed,FTPClient ftp) {
        LinkedHashMap<String,Object> step=new LinkedHashMap<>();
        step.put("operation",operation);step.put("path",path);step.put("passed",passed);
        step.put("replyCode",ftp.getReplyCode());step.put("replyText",safeText(ftp.getReplyString(),240));
        trace.steps.add(Collections.unmodifiableMap(step));
        log.info("FTP diagnostic step: deliveryId={}, operation={}, path={}, passed={}, replyCode={}, replyText={}",trace.deliveryId,operation,path,passed,ftp.getReplyCode(),safeText(ftp.getReplyString(),240));
    }
}
