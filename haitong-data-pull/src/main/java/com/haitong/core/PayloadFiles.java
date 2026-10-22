package com.haitong.core;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Durable local spool. The directory must be a private persistent volume, not a shared writable upload folder. */
public final class PayloadFiles {
    public record Saved(Path path, long size, String sha256) {}
    private PayloadFiles() {}
    public static byte[] readLimited(InputStream in, int maximum) throws IOException {
        byte[] bytes=in.readNBytes(maximum+1);
        if(bytes.length>maximum) throw new RelayException(413,"PAYLOAD_TOO_LARGE","请求体或记录超过大小限制");
        return bytes;
    }
    public static String readLine(Reader in, int maximum) throws IOException {
        StringBuilder b=new StringBuilder();
        for(int c;(c=in.read())!=-1;) {
            if(c=='\n') return b.toString();
            if(b.length()>=maximum) throw new IOException("JSONL 单行超过限制");
            b.append((char)c);
        }
        return b.isEmpty()?null:b.toString();
    }
    public static Reader utf8Reader(Path path) throws IOException {
        return new BufferedReader(new InputStreamReader(Files.newInputStream(path),StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));
    }
    public static String utf8(byte[] bytes) {
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch(CharacterCodingException e) { throw RelayException.invalid("必须使用合法的 UTF-8 编码"); }
    }
    public static Saved save(Path directory, InputStream in, long maxBytes, long minFreeBytes) throws IOException {
        Files.createDirectories(directory);
        Path tmp=directory.resolve(UUID.randomUUID()+".part"), dst=directory.resolve(UUID.randomUUID()+".payload");
        MessageDigest digest=digest(); long total=0;
        try {
            try(FileChannel out=FileChannel.open(tmp,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                byte[] buffer=new byte[65536]; int read; long nextCheck=0;
                while((read=in.read(buffer))!=-1) {
                    if(read==0) continue;
                    total+=read;
                    if(total>maxBytes) throw new RelayException(413,"FILE_TOO_LARGE","文件超过大小限制");
                    if(total>=nextCheck) {
                        if(Files.getFileStore(directory).getUsableSpace()<minFreeBytes) throw RelayException.capacity();
                        nextCheck=total+4*1024*1024;
                    }
                    digest.update(buffer,0,read); ByteBuffer b=ByteBuffer.wrap(buffer,0,read);
                    while(b.hasRemaining()) out.write(b);
                }
                out.force(true);
            }
            try { Files.move(tmp,dst,StandardCopyOption.ATOMIC_MOVE); }
            catch(AtomicMoveNotSupportedException e) { throw new IOException("工作目录不支持同卷原子移动",e); }
            syncDirectory(directory);
            return new Saved(dst,total,HexFormat.of().formatHex(digest.digest()));
        } finally { Files.deleteIfExists(tmp); }
    }
    public static String sha256(Path path) throws IOException {
        MessageDigest digest=digest();
        try(InputStream in=Files.newInputStream(path)) { byte[] b=new byte[65536]; int n; while((n=in.read(b))!=-1) digest.update(b,0,n); }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); } catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static void syncDirectory(Path directory) throws IOException {
        // Windows does not expose directory fsync through FileChannel; process-restart durability remains,
        // while power-loss guarantees must be validated against the actual volume and OS.
        if(System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win")) return;
        try(FileChannel c=FileChannel.open(directory,StandardOpenOption.READ)) { c.force(true); }
    }
}
