package com.linewell.dataelement.platform.magic;

import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ssssssss.script.runtime.ExitValue;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Guards the mixed namespace of code_table_id using persisted registration metadata. */
class MaterializationDictionaryReferenceMagicTest {
    @ParameterizedTest @ValueSource(strings={"standard","enum"})
    void twentyAndHundredInlineReferencesDoNotResolveAsPhysicalTables(String type) throws Exception {
        List<Integer> reads=new ArrayList<>();
        for (int count:List.of(20,100)) {
            var f=new ExistingTargetMaterializationMagicTest.Fixture(count);
            List<Map<String,Object>> fields=new ArrayList<>();
            for(int i=0;i<count;i++) {
                f.jdbc.update("update db_table_column_t set code_table_id=? where tid=?","standard-id-"+i,"source-col-"+i);
                fields.add(Map.of("columnName","field_"+i,"dictionaryRelation",Map.of("enabled",true,"mode",type,
                    "sourceType",type,"forceStandardDictionaryId","standard-id-"+i,
                    "enumItems",List.of(Map.of("value","01","label","标准值")))));
            }
            saveGovernance(f,fields);
            Map<?,?> result=assertInstanceOf(Map.class,f.run(false,true));
            assertEquals(true,result.get("dictionaryValidationPassed"));
            assertEquals(false,result.get("sideEffectsApplied"));
            assertEquals(false,result.get("physicalTableChecked"));
            assertEquals(count*2+3,((List<?>)result.get("tableItems")).size());
            assertEquals(0,f.physicalCalls); assertEquals(0,f.base.writeCount());
            reads.add(f.base.readCount());
            System.out.println("Inline "+type+" fields="+count+", metadata SQL="+reads.getLast()+", physical reads/writes=0");
        }
        assertEquals(reads.getFirst(),reads.getLast());
    }

    @Test
    void inlineIdCollisionCannotResolveOrAuthorizeAnUnrelatedPhysicalTable() throws Exception {
        var f=new ExistingTargetMaterializationMagicTest.Fixture(1);
        f.jdbc.update("update db_table_column_t set code_table_id='standard-gender' where table_id='source-1'");
        f.jdbc.update("insert into db_datasource_t(tid,tenant_id,db_type,is_del) values('hidden-db','police','mysql',0)");
        f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,is_del) values('standard-gender','police','hidden-db','unrelated_dictionary',0)");
        f.base.scope.deniedId="hidden-db";
        saveGovernance(f,List.of(Map.of("columnName","field_0","dictionaryRelation",Map.of("enabled",true,
            "sourceType","standard","forceStandardEnabled",true,"forceStandardDictionaryId","standard-gender",
            "enumItems",List.of(Map.of("value","01","label","男"))))));
        var result=assertInstanceOf(Map.class,f.run(false));
        String targetId=result.get("targetTableId").toString();
        assertEquals(0,f.jdbc.queryForObject("select count(*) from da_prop_t where tenant_id is null or tenant_id<>'police'",Integer.class));
        var governance=JSONUtil.parseObj(f.jdbc.queryForObject("select field_governance_config from db_table_t where tid=?",String.class,targetId));
        var binding=governance.getJSONArray("fieldTransformBindings").getJSONObject(0);
        assertEquals("MANDATORY_STANDARD",binding.getStr("ruleType"));
        assertEquals("field_0_cn",binding.getStr("targetField"));
        assertInstanceOf(Map.class,f.base.run(Map.of("tableId","source-1","targetTableIds",List.of(targetId))));
        String dsl=f.jdbc.queryForObject("select dsl_json from nifi_pipeline_t",String.class);
        assertTrue(dsl.contains("标准") || dsl.contains("男"));
        assertFalse(dsl.contains("unrelated_dictionary"));
        assertFalse(dsl.contains("hidden-db"));
    }

    @Test
    void governanceStoredOnCatalogPropertiesIsLoadedBeforeDictionaryIdClassification() throws Exception {
        var f=new ExistingTargetMaterializationMagicTest.Fixture(1);
        f.jdbc.update("update db_table_column_t set code_table_id='standard-gender' where table_id='source-1'");
        String config=JSONUtil.toJsonStr(Map.of("fields",List.of(Map.of("columnName","field_0","dictionaryRelation",
            Map.of("enabled",true,"mode","enum","enumItems",List.of(Map.of("value","1","label","男")))))));
        f.jdbc.update("insert into da_catalog_t(tid,tenant_id,is_del) values('catalog-1','police',0)");
        f.jdbc.update("insert into da_prop_t(tenant_id,parent_id,prop_name,prop_value,is_del) values('police','catalog-1','sourceTableId','source-1',0)");
        f.jdbc.update("insert into da_prop_t(tenant_id,parent_id,prop_name,prop_value,is_del) values('police','catalog-1','fieldGovernanceConfig',?,0)",config);
        Object probe=f.run(false,true);
        var result=assertInstanceOf(Map.class,probe,probe instanceof ExitValue exit?Arrays.toString(exit.getValues()):"catalog governance probe");
        assertEquals(5,((List<?>)result.get("tableItems")).size());
        assertEquals(0,f.physicalCalls); assertEquals(0,f.base.writeCount());
    }

    @Test
    void actualMissingForeignAndDeletedDictionaryReferencesStillRejectBeforeWriting() throws Exception {
        for(String scenario:List.of("missing","foreign","deleted")) {
            var f=new ExistingTargetMaterializationMagicTest.Fixture(2);
            f.jdbc.update("update db_table_column_t set code_table_id='real-dictionary' where tid='source-col-1'");
            if(!scenario.equals("missing")) {
                f.jdbc.update("insert into db_datasource_t(tid,tenant_id,db_type,is_del) values('dictionary-db',?,'mysql',0)",scenario.equals("foreign")?"other-tenant":"police");
                f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,is_del) values('real-dictionary',?,'dictionary-db','dictionary',0)",scenario.equals("foreign")?"other-tenant":"police");
            }
            if(scenario.equals("deleted")) f.jdbc.update("update db_datasource_t set is_del=1 where tid='dictionary-db'");
            saveGovernance(f,List.of(Map.of("columnName","field_0","dictionaryRelation",Map.of("enabled",true,
                "mode","enum","enumItems",List.of(Map.of("value","1","label","枚举值"))))));
            var denied=assertInstanceOf(ExitValue.class,f.run(false,true));
            assertEquals(403,denied.getValues()[0]);
            assertTrue(denied.getValues()[1].toString().contains("field_1"));
            assertEquals(0,f.physicalCalls); assertEquals(0,f.base.writeCount());
        }
    }

    @Test
    void explicitlyDisabledRelationDoesNotReactivateItsOldPhysicalReference() throws Exception {
        var f=new ExistingTargetMaterializationMagicTest.Fixture(1);
        f.jdbc.update("update db_table_column_t set code_table_id='retired-dictionary' where table_id='source-1'");
        saveGovernance(f,List.of(Map.of("columnName","field_0","dictionaryRelation",Map.of("enabled",false,
            "sourceType","dictionary","dictionaryTableId","retired-dictionary","dictionaryTable","retired_dictionary",
            "dictionaryKeyField","code","dictionaryLabelField","name"))));
        var result=assertInstanceOf(Map.class,f.run(false,true));
        assertEquals(4,((List<?>)result.get("tableItems")).size());
        assertEquals(0,f.physicalCalls); assertEquals(0,f.base.writeCount());
    }

    private static void saveGovernance(ExistingTargetMaterializationMagicTest.Fixture f,List<Map<String,Object>> fields) {
        f.jdbc.update("update db_table_t set field_governance_config=? where tid='source-1'",JSONUtil.toJsonStr(Map.of("fields",fields)));
    }
}
