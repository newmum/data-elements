package com.linewell.dataelement.metautil.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.feature.dataaccess.domain.RegisteredJdbcEndpoint;
import com.linewell.dataelement.feature.dataaccess.infrastructure.jdbc.RegisteredJdbcDataSourceResolver;
import com.linewell.dataelement.feature.dataquality.config.DataQualityProperties;
import com.linewell.dataelement.feature.dataquality.domain.DataQualityModels.*;
import com.linewell.dataelement.feature.dataquality.infrastructure.jdbc.JdbcDataQualityEngine;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Opt-in real JDBC fixture. Credentials are supplied by environment, never checked in.
 * DDL is limited to two freshly generated test tables; no business table is altered. */
@EnabledIfEnvironmentVariable(named="WX_DB_URL",matches=".+")
class MetadataMultiDatabaseLiveTest {
    @Test void allNineQualityRulesAndBoundedProfileUseRealDatabaseRows() throws Exception {
        String url=System.getenv("WX_DB_URL"),user=System.getenv("WX_DB_USER"),password=System.getenv("WX_DB_PASSWORD"),driver=System.getenv("WX_DB_DRIVER");
        Class.forName(driver);
        String key=UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT);
        String source="WX_DQ_S_"+key,target="WX_DQ_T_"+key;
        String schema=System.getenv("WX_DB_SCHEMA"),prefix=schema==null||schema.isBlank()?"":schema+".";
        if(!prefix.isEmpty())MetadataRelationJdbcService.identifier(schema);
        boolean sourceCreated=false,targetCreated=false;
        try(Connection c=DriverManager.getConnection(url,user,password);Statement s=c.createStatement()){
            s.setQueryTimeout(20);
            s.execute("CREATE TABLE "+prefix+source+" (ID INT PRIMARY KEY,A INT,B INT,LABEL VARCHAR(30),EVENT_TIME TIMESTAMP)");sourceCreated=true;
            s.execute("CREATE TABLE "+prefix+target+" (A INT NOT NULL,B INT NOT NULL,PRIMARY KEY(A,B))");targetCreated=true;
            s.executeUpdate("INSERT INTO "+prefix+target+" VALUES (1,10)");s.executeUpdate("INSERT INTO "+prefix+target+" VALUES (2,20)");
            java.time.LocalDateTime anchor=java.time.LocalDateTime.now().withNano(0);
            try(PreparedStatement insert=c.prepareStatement("INSERT INTO "+prefix+source+" VALUES (?,?,?,?,?)")){
                Object[][] rows={{1,1,10,"Alpha",Timestamp.valueOf(anchor.minusMinutes(1))},{2,1,10," ",Timestamp.valueOf(anchor.minusDays(3))},{3,2,20,null,null},{4,9,90,"beta",Timestamp.valueOf(anchor.plusDays(1))}};
                for(Object[] row:rows){for(int i=0;i<row.length;i++)insert.setObject(i+1,row[i]);insert.executeUpdate();}
            }
            var engine=new JdbcDataQualityEngine(new RegisteredJdbcDataSourceResolver(null,null),new DataQualityProperties(),new ObjectMapper());
            var endpoint=new RegisteredJdbcEndpoint("s","fixture","t",prefix+source,url,user,password,driver);
            var rules=List.of(
                    rule("NOT_NULL","LABEL",Map.of()),rule("NOT_EMPTY","LABEL",Map.of()),
                    rule("UNIQUE","A",Map.of("columns",List.of("A","B"))),rule("RANGE","A",Map.of("min",1,"max",2)),
                    rule("ENUM","A",Map.of("values",List.of(1,2))),rule("LENGTH","LABEL",Map.of("minLength",2,"maxLength",5)),
                    rule("REGEX","LABEL",Map.of("pattern","^[A-Za-z]+$")),
                    rule("TIMELINESS","EVENT_TIME",Map.of("evaluationTime",anchor.toString(),"maxAgeMinutes",60,"futureToleranceMinutes",0)),
                    rule("REFERENCE","A",Map.of("sourceColumns",List.of("A","B"),"targetColumns",List.of("A","B"),"resolvedTargetTable",prefix+target,"allowNull",false)));
            var plan=new Plan("tenant","run","task","ID",2,10,endpoint,rules,30);
            assertThat(engine.precheck(plan)).containsEntry("success",true);
            var first=engine.evaluate(plan,new Shard("first",0,"1","3",false));
            var second=engine.evaluate(plan,new Shard("second",1,"3","4",true));
            assertThat(first.rowCount()+second.rowCount()).isEqualTo(4);
            Map<String,Long> violations=new LinkedHashMap<>(),checked=new LinkedHashMap<>();
            for(var result:List.of(first,second))for(var metric:result.metrics()){
                violations.merge(metric.rule().type(),metric.violationCount(),Long::sum);checked.merge(metric.rule().type(),metric.checkedCount(),Long::sum);
            }
            assertThat(violations).containsExactlyInAnyOrderEntriesOf(Map.of("NOT_NULL",1L,"NOT_EMPTY",2L,"UNIQUE",1L,"RANGE",1L,"ENUM",1L,"LENGTH",2L,"REGEX",2L,"TIMELINESS",3L,"REFERENCE",1L));
            assertThat(checked.values()).allMatch(n->n==4L);assertThat(first.samples().size()+second.samples().size()).isLessThanOrEqualTo(10);
            var full=List.of(rule("PROFILE","LABEL",Map.of("scanMode","FULL","scanLimit",0)),rule("PROFILE","ID",Map.of("scanMode","FULL","scanLimit",0)));
            var profile=engine.evaluate(new Plan("tenant","run","task",null,1,0,endpoint,full,30),new Shard("full",0,null,null,true));
            assertThat(profile.metrics().getFirst().profile()).containsEntry("scannedRows",4L).containsEntry("nullCount",1L).containsEntry("blankCount",1L).containsEntry("distinctCount",3L);
            assertThat(profile.metrics().get(1).profile()).containsEntry("scannedRows",4L).containsEntry("distinctCount",4L).containsEntry("blankCount",null);
            assertThat(profile.samples()).isEmpty();
            var bounded=List.of(rule("PROFILE","LABEL",Map.of("scanMode","SAMPLE","scanLimit",2)),rule("PROFILE","ID",Map.of("scanMode","SAMPLE","scanLimit",2)));
            var sample=engine.evaluate(new Plan("tenant","run","task",null,1,0,endpoint,bounded,30),new Shard("sample",0,null,null,true));
            assertThat(sample.metrics()).allSatisfy(m->assertThat(m.profile()).containsEntry("scannedRows",2L).containsEntry("scanMode","SAMPLE"));
            var oneSided=List.of(rule("RANGE","A",Map.of("min","","max",2)),rule("LENGTH","LABEL",Map.of("minLength","","maxLength",5)));
            assertThat(engine.precheck(new Plan("tenant","run","task",null,1,0,endpoint,oneSided,30))).containsEntry("success",true);
            System.out.println("Live isolated nine-rule / two-shard / full and bounded profile PASS: "+c.getMetaData().getDatabaseProductName());
        }finally{
            try(Connection c=DriverManager.getConnection(url,user,password);Statement s=c.createStatement()){
                if(sourceCreated)s.execute("DROP TABLE "+prefix+source);if(targetCreated)s.execute("DROP TABLE "+prefix+target);
            }
        }
    }

    private static Rule rule(String type,String column,Map<String,Object> parameters){return new Rule(type,type,type,"PROFILE".equals(type)?"PROFILE":"VALIDITY",column,null,parameters,"HIGH",10);}

    @Test void realCompositeKeysForeignKeyEnforcementAndQualityAggregates() throws Exception {
        String url=System.getenv("WX_DB_URL"),user=System.getenv("WX_DB_USER"),password=System.getenv("WX_DB_PASSWORD"),type=System.getenv("WX_DB_TYPE"),driver=System.getenv("WX_DB_DRIVER");
        Class.forName(driver);
        String suffix=UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT);
        String parent="WX_ER_P_"+suffix,child="WX_ER_C_"+suffix,fk="WX_ER_F_"+suffix;
        String schema=System.getenv("WX_DB_SCHEMA");
        String prefix=schema==null||schema.isBlank()?"":schema+".";
        for(String id:List.of(parent,child,fk))MetadataRelationJdbcService.identifier(id);
        if(!prefix.isEmpty())MetadataRelationJdbcService.identifier(schema);
        var config=new LinkedHashMap<String,Object>();config.put("dbType",type);config.put("jdbcURL",url);config.put("username",user);config.put("password",password);config.put("showConnect",1);
        var resolver=mock(DataSourceConnectionPropertyResolver.class);when(resolver.resolve("fixture-tenant","fixture-source")).thenReturn(config);
        var service=new MetadataRelationJdbcService(resolver);
        boolean parentCreated=false,childCreated=false;
        try(var scope=TenantContext.use("fixture-tenant");Connection c=DriverManager.getConnection(url,user,password);Statement s=c.createStatement()) {
            s.setQueryTimeout(20);
            // Creation must succeed for these exact fresh identifiers. Never reuse a table after an error.
            s.execute("CREATE TABLE "+prefix+parent+" (A INT NOT NULL,B INT NOT NULL,PRIMARY KEY(A,B))");parentCreated=true;
            s.execute("CREATE TABLE "+prefix+child+" (ID INT PRIMARY KEY,A INT,B INT,LABEL VARCHAR(30),EVENT_TIME TIMESTAMP)");childCreated=true;
            s.executeUpdate("INSERT INTO "+prefix+parent+" VALUES (1,2)");
            s.executeUpdate("INSERT INTO "+prefix+child+" VALUES (1,1,2,'ok',CURRENT_TIMESTAMP)");
            s.executeUpdate("INSERT INTO "+prefix+child+" VALUES (2,1,3,' ',NULL)");
            s.executeUpdate("INSERT INTO "+prefix+child+" VALUES (3,NULL,2,NULL,CURRENT_TIMESTAMP)");
            var d=new LinkedHashMap<String,Object>();d.put("sourceId","fixture-source");d.put("targetId","fixture-source");d.put("sourceTable",prefix+child);d.put("targetTable",prefix+parent);d.put("sourceKind","TABLE");d.put("targetKind","TABLE");d.put("relationType","REFERENCE");d.put("mappings",List.of(Map.of("sourceName","A","targetName","A"),Map.of("sourceName","B","targetName","B")));d.put("conditions",List.of());d.put("targetsPerSource",Map.of("min","1","max","1"));
            var validated=service.validate(d,100);assertThat(validated.get("validationStatus")).isEqualTo("violations_found");assertThat(((Map<?,?>)validated.get("metrics")).get("orphanRows")).isEqualTo("1");
            var engine=new JdbcDataQualityEngine(new RegisteredJdbcDataSourceResolver(null,null),new DataQualityProperties(),new ObjectMapper());
            var endpoint=new RegisteredJdbcEndpoint("s","fixture","t",prefix+child,url,user,password,driver);
            var rules=List.of(new Rule("reference","reference","REFERENCE","CONSISTENCY","A",null,Map.of("sourceColumns",List.of("A","B"),"targetColumns",List.of("A","B"),"resolvedTargetTable",prefix+parent,"allowNull",false),"HIGH",10),new Rule("time","time","TIMELINESS","TIMELINESS","EVENT_TIME",null,Map.of("evaluationTime",java.time.LocalDateTime.now().plusSeconds(1).toString(),"maxAgeMinutes",60,"futureToleranceMinutes",1),"HIGH",10));
            var plan=new Plan("tenant","run","task",null,1,0,endpoint,rules,30);
            assertThat(engine.precheck(plan)).containsEntry("success",true);
            var result=engine.evaluate(plan,new Shard("one",0,null,null,true));assertThat(result.metrics().getFirst().violationCount()).isEqualTo(2);assertThat(result.metrics().get(1).violationCount()).isEqualTo(1);
            var profile=new Rule("profile","profile","PROFILE","PROFILE","LABEL",null,Map.of("scanMode","FULL","scanLimit",0),"LOW",1);
            var stats=engine.evaluate(new Plan("tenant","run","task",null,1,0,endpoint,List.of(profile),30),new Shard("one",0,null,null,true)).metrics().getFirst().profile();
            assertThat(stats).containsEntry("scannedRows",3L).containsEntry("nullCount",1L).containsEntry("blankCount",1L).containsEntry("distinctCount",2L);
            s.executeUpdate("DELETE FROM "+prefix+child+" WHERE ID IN (2,3)");
            var preview=service.preview(d,fk);assertThat(service.execute(d,fk,(String)preview.get("hash"))).containsEntry("executed",true);
            assertThatThrownBy(()->s.executeUpdate("INSERT INTO "+prefix+child+" VALUES (4,9,9,'bad',CURRENT_TIMESTAMP)")).isInstanceOf(SQLException.class);
            assertThat(service.preview(d,fk)).containsEntry("alreadyExists",true);
            s.execute((String)preview.get("rollbackSql"));
            assertThat(service.validate(d,100)).containsEntry("validationStatus","full_supported");
            System.out.println("Live isolated ER/quality fixture PASS: "+c.getMetaData().getDatabaseProductName());
        } finally {
            try(Connection c=DriverManager.getConnection(url,user,password);Statement s=c.createStatement()) {
                if(childCreated)s.execute("DROP TABLE "+prefix+child);
                if(parentCreated)s.execute("DROP TABLE "+prefix+parent);
            }
        }
    }
}
