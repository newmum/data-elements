package com.linewell.dataelement.platform.magic;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.*;
import org.ssssssss.magicapi.modules.db.provider.*;
import org.ssssssss.script.*;
import static org.junit.jupiter.api.Assertions.*;

class LoggingMagicTransactionTest {
    JdbcTemplate jdbc;SQLModule db;
    @BeforeEach void setup(){
        var source=new DriverManagerDataSource("jdbc:h2:mem:logging_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc=new JdbcTemplate(source);jdbc.execute("create table validation_business_t(tid varchar(32) primary key,name varchar(100))");jdbc.execute("create table log_system_t(tid varchar(32) primary key,target_name varchar(4))");
        var sources=new MagicDynamicDataSource();sources.setDefault(source);db=new SQLModule(sources);db.setDataSourceNode(sources.getDataSource());
        var columns=new ColumnMapperAdapter();columns.add(new DefaultColumnMapperProvider());columns.add(new CamelColumnMapperProvider());columns.setDefault("camel");db.setColumnMapperProvider(columns);db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());db.setRowMapColumnMapper(value->value);db.setSqlInterceptors(List.of());db.setNamedTableInterceptors(List.of());
    }
    public static class Audit {
        public Map<String,Object> prepare(String category,String action,String result,String type,String id,String name,Object details){return Map.of("table","log_system_t","row",Map.of("tid",UUID.randomUUID().toString().replace("-",""),"target_name",name));}
        public List<Object> batchRows(Map<String,Object> parent,Map<String,Object> details){return List.of();}
    }
    Object execute(String target)throws Exception{
        String body=Files.readString(Path.of("../magic/function/01.通用函数/22.记录业务操作.ms")).split("================================",2)[1].replaceAll("(?m)^import[^\\r\\n]*[\\r\\n]+","");
        String script="var log=(action,targetType,targetId,targetName,details)=>{"+body+"};return db.transaction(()=>{db.normal().table('validation_business_t').primary('tid').insert({tid:'validation',name:'验证对象'});log('VALIDATION_CREATED','OBJECT','validation',target,{});return true;});";
        return MagicScript.create(script,null).execute(new MagicScriptContext(Map.of("db",db,"auditEvents",new Audit(),"target",target)));
    }
    @Test void successfulBusinessAndEventCommitTogether()throws Exception{assertEquals(true,execute("验证对象"));assertEquals(1,jdbc.queryForObject("select count(*) from validation_business_t",Integer.class));assertEquals(1,jdbc.queryForObject("select count(*) from log_system_t",Integer.class));}
    @Test void failedAuditInsertRollsBackBusinessChange(){assertThrows(Exception.class,()->execute("validation-audit-too-long"));assertEquals(0,jdbc.queryForObject("select count(*) from validation_business_t",Integer.class));assertEquals(0,jdbc.queryForObject("select count(*) from log_system_t",Integer.class));}
}
