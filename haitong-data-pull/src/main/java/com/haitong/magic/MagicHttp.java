package com.haitong.magic;

import com.haitong.config.JsonBodyFilter;
import com.haitong.core.Json;
import com.haitong.core.PayloadFiles;
import com.haitong.core.RelayException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.channels.Channels;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;

/** HTTP encoding, bounded bodies and byte streams only. No business-table access or workflow decisions. */
@Component("magicHttp")
@MagicModule("magicHttp")
public final class MagicHttp {
    private final Json json;
    private final org.springframework.context.ApplicationContext context;
    public MagicHttp(Json json,org.springframework.context.ApplicationContext context) { this.json=json;this.context=context; }
    public Object bean(String name) { return context.getBean(name); }
    public HttpServletResponse response() {
        var attributes=(org.springframework.web.context.request.ServletRequestAttributes)org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes();
        return attributes.getResponse();
    }
    public byte[] raw(HttpServletRequest request,long limit) throws IOException {
        Object cached=request.getAttribute(JsonBodyFilter.RAW);
        byte[] bytes=cached instanceof byte[] b?b:PayloadFiles.readLimited(request.getInputStream(),Math.toIntExact(limit));
        if(bytes.length>limit)throw new RelayException(413,"PAYLOAD_TOO_LARGE","请求体超过限制");
        return bytes;
    }
    public Map<String,Object> object(HttpServletRequest request,long limit) throws IOException { return json.object(raw(request,limit)); }
    public ResponseEntity<?> reply(int status,Object value) { return ResponseEntity.status(status).body(value); }
    public ResponseEntity<?> located(int status,Object value,String location) {
        return ResponseEntity.status(status).header("Location",location).body(value);
    }
    public String randomKey() {
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public Path safePath(String root,String candidate) throws IOException {
        Path path=Path.of(candidate).toRealPath();
        if(!path.startsWith(Path.of(root).toRealPath())||!Files.isRegularFile(path))throw RelayException.notFound();
        return path;
    }
    public long usableBytes(String root) throws IOException { return Files.getFileStore(Path.of(root)).getUsableSpace(); }
    /** Standard HTTP Range/HEAD transfer; authorization and READY selection belong to the calling script. */
    public ResponseEntity<?> stream(String root,String candidate,String name,String type,String etag,
                                    HttpServletRequest request,HttpServletResponse response) throws IOException {
        Path file=safePath(root,candidate);long size=Files.size(file),start=0,end=size-1;
        response.setHeader("Accept-Ranges","bytes");response.setHeader("Cache-Control","private, no-store");
        response.setHeader("X-Content-Type-Options","nosniff");response.setHeader("ETag",etag);
        response.setHeader("Content-Disposition",ContentDisposition.attachment().filename(name,java.nio.charset.StandardCharsets.UTF_8).build().toString());
        String range=request.getHeader("Range"),ifRange=request.getHeader("If-Range");boolean partial=false;
        if(range!=null&&(ifRange==null||ifRange.equals(etag))) {
            try {
                if(!range.matches("bytes=\\d*-\\d*"))throw new IllegalArgumentException();
                String[] parts=range.substring(6).split("-",-1);
                if(parts[0].isEmpty()){long suffix=Long.parseLong(parts[1]);if(suffix<=0)throw new IllegalArgumentException();start=Math.max(0,size-suffix);}
                else{start=Long.parseLong(parts[0]);if(!parts[1].isEmpty())end=Math.min(end,Long.parseLong(parts[1]));}
                if(start>=size||end<start)throw new IllegalArgumentException();partial=true;
            } catch(IllegalArgumentException invalid) {
                response.setStatus(416);response.setHeader("Content-Range","bytes */"+size);
                return ResponseEntity.status(416).build();
            }
        }
        int status=partial?206:200;response.setStatus(status);
        if(partial)response.setHeader("Content-Range","bytes "+start+"-"+end+"/"+size);
        response.setContentType(type);response.setContentLengthLong(end-start+1);
        if(!request.getMethod().equals("HEAD"))try(var channel=Files.newByteChannel(file,StandardOpenOption.READ);var input=Channels.newInputStream(channel)) {
            channel.position(start);long remaining=end-start+1;byte[] buffer=new byte[65536];OutputStream output=response.getOutputStream();
            while(remaining>0){int count=input.read(buffer,0,(int)Math.min(remaining,buffer.length));if(count<0)throw new EOFException();output.write(buffer,0,count);remaining-=count;}
        }
        return ResponseEntity.status(status).build();
    }
}
