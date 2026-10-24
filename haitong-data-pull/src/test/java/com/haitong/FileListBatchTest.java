package com.haitong;


import com.haitong.core.FileService;
import com.haitong.core.RelayException;
import com.haitong.core.Models.*;
import com.haitong.store.DatabaseDialect;
import com.haitong.store.RelayRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class FileListBatchTest {
    private static final class CountingJdbcTemplate extends JdbcTemplate {
        private int selects;
        CountingJdbcTemplate(DriverManagerDataSource dataSource) { super(dataSource); }
        @Override public <T> List<T> query(String sql,RowMapper<T> mapper,Object... args) {
            if(sql.startsWith("SELECT ")) selects++;
            return super.query(sql,mapper,args);
        }
        @Override public void query(String sql,RowCallbackHandler handler,Object... args) {
            if(sql.startsWith("SELECT ")) selects++;
            super.query(sql,handler,args);
        }
        void resetSelects() { selects=0; }
        int selects() { return selects; }
    }

    @Test void adminFilePagesUseThreeSelectsForTwentyAndOneHundredRows() {
        String database="jdbc:h2:mem:file-list-batch-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1";
        DriverManagerDataSource dataSource=new DriverManagerDataSource(database,"sa","");
        new ResourceDatabasePopulator(new ClassPathResource("db/h2.sql")).execute(dataSource);
        CountingJdbcTemplate jdbc=new CountingJdbcTemplate(dataSource);
        RelayRepository repo=new RelayRepository(jdbc,DatabaseDialect.H2,null,null);
        for(int i=0;i<100;i++) {
            String id="file-"+i;
            repo.insertFile(new FileRow(id,"owner","HTTP_FILE","http-file:owner",id,"demo",id+".jsonl",
                "spool/"+id,"hash",10,"JSONL","{}","READY",0,0,false,null,0,0,null,100-i));
        }
        repo.insertMessage(new Message("message-1","owner","HTTP_FILE","file:file-0","file-0:1","demo","hash",
            "{}","{}",null,null,"file-0",1L,"ACCEPTED",null,"{}",null,1));
        repo.insertMessage(new Message("message-2","owner","HTTP_FILE","file:file-0","file-0:2","demo","hash",
            "{}",null,null,null,"file-0",2L,"QUARANTINED","INVALID","{}",null,2));
        repo.insertDelivery("message-1",new TargetPlan("target",Kind.LOG,"","",null,null,null),"{}",3,1);
        jdbc.update("UPDATE ht_file_checkpoint SET status='INGESTED',eof_reached=1,total_records=2 WHERE id='file-0'");

        FileService files=new FileService(null,null,repo,null,null,null,null);

        try {
            for(int limit:List.of(20,100)) {
                jdbc.resetSelects();
                List<Map<String,Object>> page=files.statusesForAdminList(repo.listFiles(limit,0));
                assertEquals(limit,page.size());
                assertEquals(3,jdbc.selects(),"查询数不得随文件行数增长");
                Map<String,Object> first=page.stream().filter(row->row.get("fileId").equals("file-0")).findFirst().orElseThrow();
                assertEquals("PARTIAL_FAILURE",first.get("status"));
                assertEquals(Map.of("recordsAccepted",2L,"quarantined",1L,"PENDING",1L),first.get("counts"));
                assertEquals(files.status("file-0","owner",false),first,"批量响应须与单文件详情保持一致");
            }
            jdbc.resetSelects();
            assertTrue(files.statusesForAdminList(List.of()).isEmpty());
            assertEquals(0,jdbc.selects());

            assertThrows(IllegalArgumentException.class,()->repo.fileStatsBatch(Collections.nCopies(201,"file-0")));
        } finally { files.close(); }
    }
}
