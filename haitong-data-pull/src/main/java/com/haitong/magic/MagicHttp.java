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
    public Map<String,Object> multipart(HttpServletRequest request,long fileLimit,List<String> fields) throws IOException {
        String type=request.getContentType();
        boolean valid=false;
        try{valid=type!=null&&MediaType.MULTIPART_FORM_DATA.isCompatibleWith(MediaType.parseMediaType(type));}catch(IllegalArgumentException ignored){}
        if(!valid)
            throw new RelayException(415,"UNSUPPORTED_MEDIA_TYPE","文件推送使用 multipart/form-data");
        if(request.getQueryString()!=null&&!request.getQueryString().isBlank())throw RelayException.invalid("文件推送参数应放在multipart请求体中");
        if(request.getContentLengthLong()>fileLimit+65536)throw new RelayException(413,"SINGLE_UPLOAD_TOO_LARGE","请求超过整文件上传上限，请使用分片上传");
        String encoding=request.getHeader("Content-Encoding");
        if(encoding!=null&&!encoding.equalsIgnoreCase("identity"))throw new RelayException(415,"ENCODING_NOT_ALLOWED","本接口不接收压缩请求体");
        Map<String,Object> result=new LinkedHashMap<>();
        try {
            var parts=request.getParts();
            if(parts.size()>fields.size()+1)throw RelayException.invalid("multipart包含重复或未定义参数");
            for(var part:parts){
                String name=part.getName();
                if((!name.equals("file")&&!fields.contains(name))||result.containsKey(name))throw RelayException.invalid("multipart包含重复或未定义参数");
                if(name.equals("file")){
                    if(part.getSubmittedFileName()==null||part.getSize()<1)throw RelayException.invalid("请上传一个有效文件");
                    if(part.getSize()>fileLimit)throw new RelayException(413,"SINGLE_UPLOAD_TOO_LARGE","文件超过整文件上传上限，请使用分片上传");
                    result.put(name,part);
                } else {
                    if(part.getSubmittedFileName()!=null||part.getSize()>65536)throw RelayException.invalid("业务参数必须为不超过64KiB的文本");
                    try(var stream=part.getInputStream()){result.put(name,new String(PayloadFiles.readLimited(stream,65536),java.nio.charset.StandardCharsets.UTF_8));}
                }
            }
        } catch(jakarta.servlet.ServletException e){throw RelayException.invalid("multipart请求格式不正确");}
        catch(IllegalStateException e){throw new RelayException(413,"SINGLE_UPLOAD_TOO_LARGE","multipart请求超过接收上限");}
        if(!result.containsKey("file"))throw RelayException.invalid("缺少file文件参数");
        return result;
    }
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
