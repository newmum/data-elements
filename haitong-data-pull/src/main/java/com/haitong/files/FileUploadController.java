package com.haitong.files;

import com.haitong.api.AuthenticationFilter;
import com.haitong.core.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/file-uploads")
@ConditionalOnProperty(prefix="relay.files",name="enabled",havingValue="true")
public class FileUploadController {
    private final FileUploadService service;private final Json json;private final FileStorage storage;
    public FileUploadController(FileUploadService service,Json json,FileStorage storage){this.service=service;this.json=json;this.storage=storage;}
    @PostMapping(path="/single",consumes=MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<?> single(@RequestHeader("Idempotency-Key") String key,@RequestParam String datasourceId,
            @RequestParam String tableName,@RequestParam String fieldName,@RequestParam String fileName,
            HttpServletRequest request) throws IOException {
        if(!Set.of("datasourceId","tableName","fieldName","fileName").containsAll(request.getParameterMap().keySet())
                ||request.getParameterMap().values().stream().anyMatch(values->values.length!=1))
            throw RelayException.invalid("请求包含未定义参数，租户和投递地址由服务端确定");
        return ResponseEntity.accepted().body(service.single(AuthenticationFilter.identity(request),key,
                new FileUploadService.Single(datasourceId,tableName,fieldName,fileName),request.getContentLengthLong(),request.getInputStream()));
    }
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE) public ResponseEntity<?> init(@RequestHeader("Idempotency-Key") String key,HttpServletRequest request) throws IOException {
        var body=body(request);rejectUnknown(body,Set.of("datasourceId","collectionId","fileName","size","sha256"));
        var input=new FileUploadService.Init(text(body,"datasourceId"),text(body,"collectionId"),text(body,"fileName"),number(body,"size"),optional(body,"sha256"));
        return ResponseEntity.accepted().body(service.initialize(AuthenticationFilter.identity(request),key,input));
    }
    @PutMapping(path="/{id}/parts/{part}",consumes=MediaType.APPLICATION_OCTET_STREAM_VALUE) public Map<String,Object> part(@PathVariable String id,@PathVariable int part,@RequestHeader("X-Part-SHA256") String hash,HttpServletRequest request) throws IOException {
        return service.putPart(AuthenticationFilter.identity(request),id,part,hash,request.getContentLengthLong(),request.getInputStream());
    }
    @GetMapping("/{id}/parts") public Map<String,Object> parts(@PathVariable String id,@RequestParam(defaultValue="0") int after,@RequestParam(defaultValue="100") int limit,HttpServletRequest request){return service.parts(AuthenticationFilter.identity(request),id,after,limit);}
    @PostMapping("/{id}/complete") public ResponseEntity<?> complete(@PathVariable String id,HttpServletRequest request){return ResponseEntity.accepted().body(service.complete(AuthenticationFilter.identity(request),id));}
    @PostMapping(path="/{id}/submit",consumes=MediaType.APPLICATION_JSON_VALUE) public ResponseEntity<?> submit(@PathVariable String id,@RequestHeader("Idempotency-Key") String key,HttpServletRequest request) throws IOException {
        var body=body(request);rejectUnknown(body,Set.of("businessKey","metadata"));Object metadata=body.get("metadata");if(!(metadata instanceof Map<?,?>))throw RelayException.invalid("metadata 必须为对象");
        @SuppressWarnings("unchecked") Map<String,Object> values=(Map<String,Object>)metadata;
        return ResponseEntity.accepted().body(service.submit(AuthenticationFilter.identity(request),id,key,new FileUploadService.Submission(text(body,"businessKey"),values)));
    }
    @DeleteMapping("/{id}") public Map<String,Object> abort(@PathVariable String id,HttpServletRequest request){return service.abort(AuthenticationFilter.identity(request),id);}
    @GetMapping("/{id}") public Map<String,Object> status(@PathVariable String id,HttpServletRequest request){return service.status(AuthenticationFilter.identity(request),id);}
    @GetMapping public Map<String,Object> page(@RequestParam(defaultValue="20") int limit,@RequestParam(defaultValue="0") int offset,HttpServletRequest request){return service.page(AuthenticationFilter.identity(request),limit,offset);}
    @PostMapping(path="/status-batch",consumes=MediaType.APPLICATION_JSON_VALUE) public List<Map<String,Object>> batch(HttpServletRequest request) throws IOException {
        var body=body(request);rejectUnknown(body,Set.of("uploadIds"));if(!(body.get("uploadIds") instanceof List<?> ids)||ids.stream().anyMatch(v->!(v instanceof String)))throw RelayException.invalid("uploadIds 必须为字符串数组");
        return service.statuses(AuthenticationFilter.identity(request),ids.stream().map(Object::toString).toList());
    }
    @PostMapping(path="/{id}/receipts",consumes=MediaType.APPLICATION_JSON_VALUE) public Map<String,Object> receipt(@PathVariable String id,@RequestHeader("Idempotency-Key") String key,HttpServletRequest request) throws IOException {
        var body=body(request);rejectUnknown(body,Set.of("deliveryId","sha256","status","businessRef"));
        return service.receipt(AuthenticationFilter.identity(request),id,key,new FileUploadService.Receipt(text(body,"deliveryId"),text(body,"sha256"),text(body,"status"),optional(body,"businessRef")));
    }
    @RequestMapping(path="/{id}/content",method={RequestMethod.GET,RequestMethod.HEAD}) public void content(@PathVariable String id,HttpServletRequest request,HttpServletResponse response) throws IOException {
        var caller=AuthenticationFilter.identity(request);var u=service.readable(caller,id);if(!u.state().equals("READY"))throw new RelayException(409,"FILE_NOT_READY","文件尚未就绪");
        String etag='"'+u.text("final_sha256")+'"';long size=u.size(),start=0,end=size-1;response.setHeader("ETag",etag);response.setHeader("Accept-Ranges","bytes");response.setHeader("Cache-Control","private, no-store");response.setHeader("X-Content-Type-Options","nosniff");
        response.setHeader("Content-Disposition",ContentDisposition.attachment().filename(u.text("file_name"),java.nio.charset.StandardCharsets.UTF_8).build().toString());
        String range=request.getHeader("Range"),ifRange=request.getHeader("If-Range");boolean partial=false;
        if(range!=null&&(ifRange==null||ifRange.equals(etag))){
            try{if(!range.matches("bytes=\\d*-\\d*"))throw new IllegalArgumentException();String[] values=range.substring(6).split("-",-1);
                if(values[0].isEmpty()){long suffix=Long.parseLong(values[1]);if(suffix<=0)throw new IllegalArgumentException();start=Math.max(0,size-suffix);}
                else{start=Long.parseLong(values[0]);if(!values[1].isEmpty())end=Math.min(end,Long.parseLong(values[1]));}
                if(start>=size||end<start)throw new IllegalArgumentException();partial=true;
            }catch(IllegalArgumentException e){response.setStatus(416);response.setHeader("Content-Range","bytes */"+size);return;}
        }
        response.setStatus(partial?206:200);if(partial)response.setHeader("Content-Range","bytes "+start+"-"+end+"/"+size);
        response.setContentType(u.text("media_type"));response.setContentLengthLong(end-start+1);if(request.getMethod().equals("HEAD"))return;
        storage.acquire();try{Path file=storage.checked(u.text("payload_path"));
            try(SeekableByteChannel channel=Files.newByteChannel(file,StandardOpenOption.READ);InputStream in=Channels.newInputStream(channel)){
                channel.position(start);long remaining=end-start+1;byte[] buffer=new byte[65536];OutputStream output=response.getOutputStream();
                while(remaining>0){int n=in.read(buffer,0,(int)Math.min(buffer.length,remaining));if(n<0)throw new EOFException();output.write(buffer,0,n);remaining-=n;}
            }
        }finally{storage.release();}
    }
    private Map<String,Object> body(HttpServletRequest request) throws IOException {return json.object(PayloadFiles.readLimited(request.getInputStream(),65536));}
    private static void rejectUnknown(Map<String,Object> body,Set<String> allowed){if(!allowed.containsAll(body.keySet()))throw RelayException.invalid("请求包含未定义字段，租户和投递地址由服务端确定");}
    private static String text(Map<String,Object> body,String key){Object v=body.get(key);if(!(v instanceof String s)||s.isBlank())throw RelayException.invalid("缺少字段："+key);return s;}
    private static String optional(Map<String,Object> body,String key){Object v=body.get(key);if(v==null)return null;if(!(v instanceof String s))throw RelayException.invalid("字段类型不正确："+key);return s;}
    private static long number(Map<String,Object> body,String key){Object v=body.get(key);if(!(v instanceof Number n)||!n.toString().matches("[0-9]+"))throw RelayException.invalid("字段必须为正整数："+key);try{return Long.parseLong(n.toString());}catch(NumberFormatException e){throw RelayException.invalid("数值超出范围");}}
}
