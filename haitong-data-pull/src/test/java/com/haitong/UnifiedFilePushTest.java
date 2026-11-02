package com.haitong;

import com.haitong.core.RelayException;
import com.haitong.files.FileUploadService;
import java.io.ByteArrayInputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UnifiedFilePushTest {
    private static byte[] pdf(int length){byte[] data=new byte[length];byte[] prefix="%PDF-1.7\nvalidation\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);System.arraycopy(prefix,0,data,0,prefix.length);return data;}
    @Test void singleReceivesAndSubmitsOnceWhileEnglishAliasPreservesCanonicalIdentity() throws Exception {
        try(var f=new UnstructuredFileTest.Fixture()){
            f.properties.getCollections().get("cases").setFileCode("case_document");f.catalog.afterPropertiesSet();
            byte[] data=pdf(1024);
            var request=new FileUploadService.Push("police","case_document","validation-case",Map.of("caseNo","VALIDATION-1"));
            var first=f.service.singlePush(UnstructuredFileTest.ALICE,"validation-single",request,"材料.pdf",data.length,new ByteArrayInputStream(data));
            String id=first.get("uploadId").toString();
            assertEquals("case_document",first.get("fileCode"));assertEquals("/api/v1/data-push/uploads/"+id,first.get("statusUrl"));
            assertEquals("case-files",f.repository.raw(id,false).orElseThrow().text("collection_id"));
            assertFalse(f.repository.raw(id,false).orElseThrow().text("submission_hash").isEmpty());
            var second=f.service.singlePush(UnstructuredFileTest.ALICE,"validation-single",request,"材料.pdf",data.length,new ByteArrayInputStream(data));
            assertEquals(id,second.get("uploadId"));
            assertEquals(1,f.jdbc.queryForObject("SELECT COUNT(*) FROM ht_file_upload",Integer.class));
            assertTrue(f.service.runOne());assertEquals("READY",f.repository.raw(id,false).orElseThrow().state());
            assertEquals("case_document",f.service.status(UnstructuredFileTest.ALICE,id).get("fileCode"));
        }
    }
    @Test void missingMetadataForeignScopeAndOversizeFailBeforeCreatingUpload() throws Exception {
        try(var f=new UnstructuredFileTest.Fixture()){
            byte[] data=pdf(1024);
            var invalid=new FileUploadService.Push("police","case-files","validation",Map.of());
            assertEquals(422,assertThrows(RelayException.class,()->f.service.singlePush(UnstructuredFileTest.ALICE,"validation-bad",invalid,"x.pdf",data.length,new ByteArrayInputStream(data))).status());
            var foreign=new FileUploadService.Push("other","other-files","validation",Map.of());
            assertEquals(404,assertThrows(RelayException.class,()->f.service.singlePush(UnstructuredFileTest.ALICE,"validation-foreign",foreign,"x.pdf",data.length,new ByteArrayInputStream(data))).status());
            var valid=new FileUploadService.Push("police","case-files","validation",Map.of("caseNo","VALIDATION-1"));
            assertEquals(413,assertThrows(RelayException.class,()->f.service.singlePush(UnstructuredFileTest.ALICE,"validation-big",valid,"x.pdf",f.properties.getSingleUploadMaxBytes()+1,new ByteArrayInputStream(data))).status());
            assertEquals(0,f.jdbc.queryForObject("SELECT COUNT(*) FROM ht_file_upload",Integer.class));
        }
    }
    @Test void chunkCompletionAlsoSubmitsAndKeepsOriginalProtocolReadable() throws Exception {
        try(var f=new UnstructuredFileTest.Fixture()){
            byte[] data=pdf(70000);
            var result=f.service.initializePush(UnstructuredFileTest.ALICE,"validation-chunks",new FileUploadService.Init("police","case-files","large.pdf",data.length,null));
            String id=result.get("uploadId").toString();f.parts(id,data);
            f.service.completePush(UnstructuredFileTest.ALICE,id,"validation-finish",new FileUploadService.Submission("validation-case",Map.of("caseNo","VALIDATION-1")));
            assertFalse(f.repository.raw(id,false).orElseThrow().text("submission_hash").isEmpty());
            assertTrue(f.service.runOne());assertEquals("READY",f.service.status(UnstructuredFileTest.ALICE,id).get("state"));
        }
    }
}
