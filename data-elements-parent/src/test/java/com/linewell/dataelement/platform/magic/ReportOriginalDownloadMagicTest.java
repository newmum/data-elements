package com.linewell.dataelement.platform.magic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.linewell.dataelement.utils.MinioUtils;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;

class ReportOriginalDownloadMagicTest {
 public static class Response {
  final ByteArrayOutputStream stream=new ByteArrayOutputStream();int ends;
  public void setHeader(String key,String value){}
  public OutputStream getOutputStream(){return stream;}
  public String end(){ends++;return "stream-ended";}
 }
 public static class Config {public String legacyMinioPassword(){return "validation-secret";}}
 @Test void originalBytesEndWithoutAJsonEnvelopeAndForeignFileIsRejected() throws Exception {
  var f=new ReportRegistrationMagicTest.Fixture(1);f.base.jdbc.execute("alter table file_upload_t add file_name varchar(100)");f.base.jdbc.execute("alter table file_upload_t add file_path varchar(100)");
  f.base.jdbc.execute("insert into file_upload_t(tid,tenant_id,created_by,is_del,file_name,file_path) values('file','police','validation-user',0,'data.csv','attachment/file.csv'),('foreign','other','validation-user',0,'other.csv','attachment/other.csv')");
  Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath();while(!Files.isDirectory(root.resolve("magic")))root=root.getParent();
  String content=Files.readString(root.resolve("magic/api/10.通用数据操作/08.文件下载接口.ms"));
  String script=content.substring(content.indexOf("================================")+32).replaceAll("(?m)^import (?!cn\\.hutool\\.|com\\.linewell\\.)[^\\r\\n]*\\r?\\n","");
  script="var configData=()=>{minio_url:'https://validation.example.test',minio_user:'validation'}\n"+script;
  var response=new Response();var bindings=new HashMap<String,Object>(Map.of("db",f.base.db,"tenantRuntime",f.base.scope,"response",response,"systemConfig",new Config(),"fileId","file"));
  byte[] expected="id,name\n1,验证记录\n".getBytes(StandardCharsets.UTF_8);
  try(var minio=mockStatic(MinioUtils.class)){
   minio.when(()->MinioUtils.downloadFile(anyString(),anyString(),any(OutputStream.class))).thenAnswer(invocation->{((OutputStream)invocation.getArgument(2)).write(expected);return null;});
   assertEquals("stream-ended",MagicScript.create(script,null).execute(new MagicScriptContext(bindings)));assertEquals(1,response.ends);assertArrayEquals(expected,response.stream.toByteArray());
   bindings.put("fileId","foreign");assertInstanceOf(ExitValue.class,MagicScript.create(script,null).execute(new MagicScriptContext(bindings)));assertEquals(1,response.ends);
  }
 }
}
