import importlib.util
import copy as deepcopy_module
import json
from pathlib import Path
import unittest
from unittest.mock import Mock,patch

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('copy_police',ROOT/'tools/copy-police-resources.py')
copy=importlib.util.module_from_spec(spec);spec.loader.exec_module(copy)

class CopyPoliceResourcesTest(unittest.TestCase):
    def source_fixture(self,count=1):
        prepared={'shadowDatasourceId':'shadow-db','shadow':{},'rows':{'nifi_pipeline_t':[],'data_access_agg_task_t':[]}}
        live=[]
        for i in range(count):
            table={'id':'source-'+str(i),'name':'ga_source_'+str(i)}
            prepared['shadow'][str(i)]=table
            task={'pipeline_id':'p'+str(i),'source_db_id':'shadow-db','target_db_id':'shadow-db','source_table_id':table['id'],'target_table_id':'target-'+str(i)}
            dsl={'nodes':[{'id':'source-node','category':'source','manifestKey':'source.dameng','label':'保持名称','x':27,'config':{'sourceTableId':table['id'],'sourceDbId':'old-db','registeredDatasourceId':'shadow-db','selectedDatabaseId':'shadow-db','schema':'OLD_OWNER','driverClassName':'old.Driver','filterCondition':'active = 1','incrementalField':'updated_at'}}], 'edges':[]}
            original={'id':task['pipeline_id'],'dsl_json':json.dumps(dsl)}
            prepared['rows']['nifi_pipeline_t'].append(original)
            prepared['rows']['data_access_agg_task_t'].append(task)
            live.append({**original,**task,'tenant_id':copy.DST,'status':'SAVED','actual_source_db_id':'shadow-db','actual_target_db_id':'shadow-db','updated_at':1})
        return prepared,live,{'tid':'shadow-db','jdbc_url':'jdbc:mysql://example.invalid/fixture','username':'synthetic','password':'synthetic'}

    def test_shadow_source_repairs_all_aliases_and_connector_without_changing_design(self):
        prepared,live,ds=self.source_fixture()
        changes=copy.copied_source_repair_candidates(prepared,live,ds)
        self.assertEqual(len(changes),1)
        node=changes[0][1]['nodes'][0];cfg=node['config']
        self.assertEqual(node['manifestKey'],'source.mysql')
        self.assertEqual((node['id'],node['label'],node['x']),('source-node','保持名称',27))
        for key in ['registeredDatasourceId','selectedDatabaseId','sourceDbId']:self.assertEqual(cfg[key],'shadow-db')
        self.assertEqual(cfg['sourceTableName'],'ga_source_0')
        self.assertEqual((cfg['filterCondition'],cfg['incrementalField']),('active = 1','updated_at'))
        self.assertNotIn('schema',cfg);self.assertNotIn('driverClassName',cfg)
        self.assertEqual(json.loads(live[0]['dsl_json'])['nodes'][0]['manifestKey'],'source.dameng')
        repaired=deepcopy_module.deepcopy(live);repaired[0]['dsl_json']=json.dumps(changes[0][1])
        self.assertEqual(copy.copied_source_repair_candidates(prepared,repaired,ds),[])

    def test_modified_or_deployed_copied_designs_are_not_overwritten(self):
        for mode in ['edited','deployed','task-moved','registration-moved']:
            prepared,live,ds=self.source_fixture()
            if mode=='edited':
                dsl=json.loads(live[0]['dsl_json']);dsl['nodes'][0]['x']=99;live[0]['dsl_json']=json.dumps(dsl)
            elif mode=='deployed':live[0]['nifi_process_group_id']='running-group'
            elif mode=='task-moved':live[0]['source_table_id']='another'
            else:live[0]['actual_source_db_id']='another'
            with self.assertRaises(RuntimeError):copy.copied_source_repair_candidates(prepared,live,ds)

    def test_repair_prefetches_twenty_and_hundred_pipelines_and_authorizes_one_batch(self):
        for count in [20,100]:
            prepared,live,ds=self.source_fixture(count)
            masters=[{'tid':tid,'created_by':'actor','org_id':'org'} for tid in ['shadow-db','old-db']]
            with patch.object(copy,'bundle',return_value=prepared),patch.object(copy,'load',return_value={'rows':{'nifi_pipeline_t':[r['id'] for r in live]}}),patch.object(copy,'dump'),patch.object(copy.m,'check_session'),patch.object(copy.m,'query',side_effect=[live,[ds],masters]) as query,patch.object(copy.m,'api',return_value={'scope':'OWNER','userId':'actor','organizationIds':[]}) as api,patch.object(copy.m,'execute') as execute,patch('builtins.print'):
                result=copy.repair_chain_sources(Mock())
                self.assertEqual(result['plannedChanges'],count)
                self.assertEqual(query.call_count,3)
                self.assertEqual(api.call_count,1)
                self.assertEqual(api.call_args.args,('/sym/auth/currentDataScope',{'resourceCode':'MENU:2081000000000000002'}))
                self.assertEqual(set(query.call_args.args[2][1:]),{'shadow-db','old-db'})
                execute.assert_not_called()

    def test_each_referenced_source_requires_current_role_scope(self):
        for scope,owner,org,allowed in [('ALL','other','other',True),('OWNER','actor','other',True),('OWNER','other','org',False),('ORG','other','org',True),('ORG','actor','other',False),('UNKNOWN','actor','org',False)]:
            prepared,live,ds=self.source_fixture()
            masters=[{'tid':'shadow-db','created_by':'actor','org_id':'org'},{'tid':'old-db','created_by':owner,'org_id':org}]
            with patch.object(copy,'bundle',return_value=prepared),patch.object(copy,'load',return_value={'rows':{'nifi_pipeline_t':['p0']}}),patch.object(copy,'dump'),patch.object(copy.m,'check_session'),patch.object(copy.m,'query',side_effect=[live,[ds],masters]),patch.object(copy.m,'api',return_value={'scope':scope,'userId':'actor','organizationIds':['org']}),patch.object(copy.m,'execute') as execute,patch('builtins.print'):
                if allowed:self.assertEqual(copy.repair_chain_sources(Mock())['plannedChanges'],1)
                else:
                    with self.assertRaises(RuntimeError):copy.repair_chain_sources(Mock(),True)
                execute.assert_not_called()

    def test_explicit_repair_selection_cannot_escape_import_snapshot(self):
        prepared,live,ds=self.source_fixture()
        with patch.object(copy,'bundle',return_value=prepared),patch.object(copy,'load',return_value={'rows':{'nifi_pipeline_t':['p0']}}),patch.object(copy.m,'check_session'),patch.object(copy.m,'query') as query:
            with self.assertRaises(RuntimeError):copy.repair_chain_sources(Mock(),True,['outside-import'])
            query.assert_not_called()

    def test_wrong_tenant_or_denied_scope_never_reaches_repair_writes(self):
        prepared,live,ds=self.source_fixture()
        for denied in ['missing-tenant-row','role-denied']:
            with patch.object(copy,'bundle',return_value=prepared),patch.object(copy,'load',return_value={'rows':{'nifi_pipeline_t':[r['id'] for r in live]}}),patch.object(copy,'dump'),patch.object(copy.m,'check_session'),patch.object(copy.m,'query',side_effect=[[] if denied=='missing-tenant-row' else live,[ds]]) as query,patch.object(copy.m,'api',side_effect=RuntimeError('scope denied')),patch.object(copy.m,'execute') as execute:
                with self.assertRaises(RuntimeError):copy.repair_chain_sources(Mock(),True)
                execute.assert_not_called()
                self.assertIn('p.tenant_id=%s',query.call_args_list[0].args[1])
                self.assertEqual(query.call_args_list[0].args[2][0],copy.DST)

    def test_ids_are_stable_distinct_and_fit_platform_columns(self):
        self.assertEqual(copy.uid('example'),copy.uid('example'))
        self.assertNotEqual(copy.uid('a'),copy.uid('b'))
        self.assertEqual(len(copy.uid('example')),32)
        self.assertNotEqual(copy.SRC,copy.DST)
        self.assertNotIn(copy.SHADOW,(copy.SOURCE,copy.TARGET,'baseline'))

    def test_references_change_without_rewriting_credentials(self):
        old='2099049046473453568';new=copy.uid(old)
        data={'sourceTableId':old,'password':old,'route':'/table/'+old,'nested':[{'id':old}]}
        result=copy.replace_refs(data,{old:new})
        self.assertEqual(result['sourceTableId'],new)
        self.assertEqual(result['password'],old)
        self.assertEqual(result['route'],'/table/'+new)
        self.assertEqual(result['nested'][0]['id'],new)

    def test_vendor_types_are_explicit_and_unknown_types_stop(self):
        self.assertEqual(copy.mysql_type({'column_type':'NUMBER(20,4)'}),'decimal(20,4)')
        self.assertEqual(copy.mysql_type({'column_type':'varchar2(60)'}),'varchar(60)')
        self.assertEqual(copy.mysql_type({'column_type':'NUMBER'}),'varchar(128)')
        self.assertEqual(copy.mysql_type({'column_type':'TIMESTAMP(6)'}),'datetime(6)')
        with self.assertRaises(ValueError):copy.mysql_type({'column_type':'geography'})

    def test_identifier_and_comment_quoting(self):
        self.assertEqual(copy.qname('a`b'),'`a``b`')
        with self.assertRaises(ValueError):copy.qname('a\0b')
        self.assertEqual(copy.literal("单位'说明"),"'单位''说明'")

    def test_legacy_es_dates_and_invalid_dates(self):
        self.assertEqual(copy.es_date('2024-01-02 03:04:05.0'),'2024-01-02T03:04:05+08:00')
        self.assertEqual(copy.es_date('1700000000000'),'2023-11-14T22:13:20+00:00')
        self.assertEqual(copy.es_date('2024-01-02T03:04:05Z'),'2024-01-02T03:04:05+00:00')
        self.assertIsNone(copy.es_date('0000-00-00'))
        self.assertIsNone(copy.es_date('null'))

if __name__=='__main__':unittest.main()
