package com.haitong;

import com.haitong.core.*;
import com.haitong.files.FileUploadService;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.haitong.UnstructuredFileTest.ALICE;
import static com.haitong.UnstructuredFileTest.BOB;
import static org.junit.jupiter.api.Assertions.*;

class SingleFileUploadTest {
    private byte[] pdf(){return ("%PDF-1.7\n"+"demo".repeat(40000)+"\n%%EOF").getBytes(java.nio.charset.StandardCharsets.US_ASCII);}
    private FileUploadService.Single input(byte[] bytes){return new FileUploadService.Single("police","case_record","document_id","案卷.pdf");}
    @Test void oneRequestStreamsBeyondNormalPartSizeAndRetriesWithoutDuplicatingFile() throws Exception {
        try(var f=new UnstructuredFileTest.Fixture()){
            byte[] bytes=pdf();var init=input(bytes);
            // The implementation must never request an entire file-sized read.
            InputStream bounded=new ByteArrayInputStream(bytes){@Override public synchronized int read(byte[] b,int off,int len){assertTrue(len<=65536);return super.read(b,off,len);}};
            var result=f.service.single(ALICE,"single-one",init,bytes.length,bounded);String id=result.get("fileId").toString();
            assertEquals("FINALIZING",result.get("state"));assertEquals("SINGLE",result.get("uploadMode"));assertEquals(1L,((Number)result.get("partCount")).longValue());
            assertEquals(id,f.service.single(ALICE,"single-one",init,-1,new ByteArrayInputStream(bytes)).get("fileId"));
            while(f.service.runOne()){}
            assertEquals("READY",f.service.status(ALICE,id).get("state"));assertArrayEquals(bytes,java.nio.file.Files.readAllBytes(f.service.content(ALICE,id)));
            assertEquals(id,f.service.single(ALICE,"single-one",init,bytes.length,new ByteArrayInputStream(bytes)).get("fileId"));
            byte[] corrupt=bytes.clone();corrupt[20]^=1;
            assertEquals(409,assertThrows(RelayException.class,()->f.service.single(ALICE,"single-one",init,bytes.length,new ByteArrayInputStream(corrupt))).status());
            var row=f.repository.raw(id,false).orElseThrow();assertEquals(bytes.length,row.size());assertEquals(Rules.sha256(bytes),row.text("final_sha256"));
            assertEquals(1,f.jdbc.queryForObject("select count(*) from ht_file_upload",Integer.class));
        }
    }
    @Test void rejectsLimitsForeignFieldsAndInterruptedBodyCanRetry() throws Exception {
        try(var f=new UnstructuredFileTest.Fixture()){
            byte[] bytes=pdf();var init=input(bytes);
            f.properties.setSingleUploadMaxBytes(bytes.length-1);
            assertEquals(413,assertThrows(RelayException.class,()->f.service.single(ALICE,"large",init,bytes.length,new ByteArrayInputStream(bytes))).status());
            assertEquals(0,f.jdbc.queryForObject("select count(*) from ht_file_upload",Integer.class));f.properties.setSingleUploadMaxBytes(20971520);
            assertEquals(404,assertThrows(RelayException.class,()->f.service.single(BOB,"foreign",init,bytes.length,new ByteArrayInputStream(bytes))).status());
            assertEquals(422,assertThrows(RelayException.class,()->f.service.single(ALICE,"length",init,bytes.length-1,new ByteArrayInputStream(bytes))).status());
            assertEquals(404,assertThrows(RelayException.class,()->f.service.single(ALICE,"field",new FileUploadService.Single("police","case_record","outside","x.pdf"),bytes.length,new ByteArrayInputStream(bytes))).status());
            assertEquals(404,assertThrows(RelayException.class,()->f.service.single(ALICE,"table",new FileUploadService.Single("police","outside","document_id","x.pdf"),bytes.length,new ByteArrayInputStream(bytes))).status());
            assertEquals(422,assertThrows(RelayException.class,()->f.service.single(ALICE,"ext",new FileUploadService.Single("police","case_record","document_id","x.exe"),bytes.length,new ByteArrayInputStream(bytes))).status());
            InputStream broken=new InputStream(){int count;public int read() throws IOException {if(count++>1000)throw new IOException("interrupted");return 0;}};
            assertThrows(IOException.class,()->f.service.single(ALICE,"retry",init,-1,broken));
            assertEquals(0,f.jdbc.queryForObject("select count(*) from ht_file_upload",Integer.class));
            var result=f.service.single(ALICE,"retry",init,bytes.length,new ByteArrayInputStream(bytes));assertEquals("FINALIZING",result.get("state"));
            assertEquals(409,assertThrows(RelayException.class,()->f.service.initialize(ALICE,"retry",new FileUploadService.Init("police","case-files","案卷.pdf",bytes.length,Rules.sha256(bytes)))).status());
        }
    }
    @Test void signatureValidationRemainsAsynchronousAndRejectsForgedPdf() throws Exception {
        try(var f=new UnstructuredFileTest.Fixture()){
            byte[] bytes="this is not a pdf".getBytes();var result=f.service.single(ALICE,"bad-signature",input(bytes),bytes.length,new ByteArrayInputStream(bytes));
            f.service.runOne();assertEquals("REJECTED",f.service.status(ALICE,result.get("fileId").toString()).get("state"));
        }
    }
    @Test void unknownLengthIsBoundedAndEmptyOrOverLimitBodiesLeaveNoStagingOrLedger() throws Exception {
        try(var f=new UnstructuredFileTest.Fixture()){
            byte[] bytes=pdf();f.properties.setSingleUploadMaxBytes(bytes.length-1);
            assertEquals(413,assertThrows(RelayException.class,()->f.service.single(ALICE,"unknown-large",input(bytes),-1,new ByteArrayInputStream(bytes))).status());
            assertEquals(422,assertThrows(RelayException.class,()->f.service.single(ALICE,"empty",input(bytes),-1,new ByteArrayInputStream(new byte[0]))).status());
            assertEquals(0,f.jdbc.queryForObject("select count(*) from ht_file_upload",Integer.class));
            try(var paths=java.nio.file.Files.list(java.nio.file.Path.of(f.relay.getWorkDir()).resolve("unstructured"))){assertTrue(paths.noneMatch(p->p.getFileName().toString().startsWith("incoming-")));}
            f.properties.setSingleUploadMaxBytes(20971520);var result=f.service.single(ALICE,"unknown-ok",input(bytes),-1,new ByteArrayInputStream(bytes));
            String id=result.get("fileId").toString();assertEquals(bytes.length,f.repository.raw(id,false).orElseThrow().size());
            f.service.runOne();assertEquals(Rules.sha256(bytes),f.service.status(ALICE,id).get("sha256"));
        }
    }
    @Test void exhaustedQuotaAndDiskGuardCleanStagingWithoutCreatingAnUpload() throws Exception {
        try(var f=new UnstructuredFileTest.Fixture()){
            byte[] bytes=pdf();f.jdbc.update("UPDATE ht_storage_quota SET limit_bytes=1 WHERE quota_key='GLOBAL'");
            assertEquals(503,assertThrows(RelayException.class,()->f.service.single(ALICE,"quota-full",input(bytes),-1,new ByteArrayInputStream(bytes))).status());
            assertEquals(0,f.jdbc.queryForObject("select count(*) from ht_file_upload",Integer.class));
            assertEquals(0L,f.jdbc.queryForObject("SELECT reserved_bytes FROM ht_storage_quota WHERE quota_key='GLOBAL'",Long.class));
            f.relay.getLimits().setMinFreeDiskBytes(Long.MAX_VALUE);
            assertEquals(503,assertThrows(RelayException.class,()->f.service.single(ALICE,"disk-full",input(bytes),-1,new ByteArrayInputStream(bytes))).status());
            try(var paths=java.nio.file.Files.list(java.nio.file.Path.of(f.relay.getWorkDir()).resolve("unstructured"))){assertTrue(paths.noneMatch(p->p.getFileName().toString().startsWith("incoming-")));}
        }
    }
}
