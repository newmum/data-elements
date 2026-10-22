import collections
import copy
import importlib.util
import json
from pathlib import Path
import unittest

TOOLS=Path(__file__).resolve().parents[1]
def module(name,file):
    spec=importlib.util.spec_from_file_location(name,TOOLS/file)
    result=importlib.util.module_from_spec(spec);spec.loader.exec_module(result);return result
check=module('check_schema','check-db.py')
sync=module('layout_sync','resource-sync.py')


class SchemaGateTests(unittest.TestCase):
    def source(self):
        col={'COLUMN_NAME':'tid','COLUMN_TYPE':'varchar(32)','IS_NULLABLE':'NO','COLUMN_DEFAULT':None,
             'EXTRA':'','COLUMN_COMMENT':'标识','CHARACTER_SET_NAME':'utf8mb4','COLLATION_NAME':'utf8mb4_general_ci','GENERATION_EXPRESSION':''}
        return {'dialect':'mysql','tables':{'asset':'资产'},'columns':{'asset':{'tid':col}},
                'indexes':collections.defaultdict(set,{'asset':{(False,(('tid',None,'A'),))}}),
                'foreignKeys':collections.defaultdict(set),'primaryKeys':{'asset':('tid',)}}

    def test_equal_counts_do_not_hide_replaced_table(self):
        source=self.source();target=copy.deepcopy(source);target['tables']={'old_asset':'资产'}
        diffs=check.compare(source,target,'tenant','authority')
        self.assertEqual({d['difference'] for d in diffs},{'missing-table','extra-table'})

    def test_extra_unique_index_and_changed_primary_key_fail(self):
        source=self.source();target=copy.deepcopy(source)
        target['indexes']['asset'].add((False,(('another_id',None,'A'),)))
        target['primaryKeys']['asset']=('another_id',)
        diffs=check.compare(source,target,'tenant','authority')
        self.assertEqual({d['difference'] for d in diffs},{'index-semantics','primary-key-columns'})

    def test_target_tenant_default_must_be_actual_binding(self):
        source=self.source();col=source['columns']['asset']['tid'];col['COLUMN_NAME']='tenant_id';col['COLUMN_DEFAULT']='authority'
        source['columns']['asset']={'tenant_id':col};target=copy.deepcopy(source)
        self.assertIn('column-default',[d['difference'] for d in check.compare(source,target,'own-tenant','authority')])
        target['columns']['asset']['tenant_id']['COLUMN_DEFAULT']='own-tenant'
        self.assertEqual(check.compare(source,target,'own-tenant','authority'),[])

    def test_native_precision_and_numeric_defaults_are_semantic(self):
        self.assertTrue(check.native_type_matches({'DATA_TYPE':'DECIMAL','DATA_PRECISION':20,'DATA_SCALE':0},{'COLUMN_TYPE':'bigint unsigned'}))
        self.assertFalse(check.native_type_matches({'DATA_TYPE':'BIGINT'},{'COLUMN_TYPE':'bigint unsigned'}))

    def test_native_prefix_index_preserves_exact_length_and_direction(self):
        parts=check.index_parts('"tenant_id",SUBSTR("identifier_value", 1, 128),"status" DESC')
        self.assertEqual([check.native_index_part(p) for p in parts], [('tenant_id',None,'A'),('identifier_value',128,'A'),('status',None,'D')])
        with self.assertRaises(ValueError):check.native_index_part('SUBSTR("identifier_value",2,128)')

    def test_native_connection_generated_rule_compares_paths_and_fallback(self):
        source="coalesce(json_unquote(json_extract(`schema_json`,_utf8mb4'$.connectionIds.input')),json_unquote(json_extract(`schema_json`,_utf8mb4'$.connectionRoles.input')),_utf8mb4'INPUT')"
        native="CASE WHEN NOT (json_unquote(json_extract(schema_json,'$.connectionIds.input')) IS NULL) THEN json_unquote(json_extract(schema_json,'$.connectionIds.input')) WHEN NOT (json_unquote(json_extract(schema_json,'$.connectionRoles.input')) IS NULL) THEN json_unquote(json_extract(schema_json,'$.connectionRoles.input')) WHEN NOT (FALSE) THEN 'INPUT' ELSE NULL END"
        self.assertTrue(check.native_generated_matches('surveillance_resource_topic_t','input_connection_ref',source,native))
        self.assertFalse(check.native_generated_matches('surveillance_resource_topic_t','input_connection_ref',source,native.replace("'INPUT'","'OUTPUT'")))
        self.assertFalse(check.native_generated_matches('surveillance_resource_topic_t','input_connection_ref',source,native.replace('connectionIds.input','connectionIds.other')))


class MagicLayoutTests(unittest.TestCase):
    def entry(self,path,meta):return {'databasePath':'/magic-api/api/'+path,'path':'api/'+path,'content':json.dumps(meta)}

    def test_question_mark_storage_path_reports_real_editor_name(self):
        group=self.entry('09.系统管理/group.json',{'id':'system','name':'09.系统管理','parentId':'0'})
        file=self.entry('09.????/刷新.ms',{'id':'refresh','name':'刷新','groupId':'system'})
        warnings=sync.magic_layout_warnings([group,file])
        self.assertEqual(warnings,[{'magicEditorPathMismatch':'api/09.????/刷新.ms','expectedDatabasePath':'/magic-api/api/09.系统管理/刷新.ms'}])

    def test_null_root_and_deleted_parent_are_not_clean_baseline(self):
        group=self.entry('兼容/group.json',{'id':'compat','name':'兼容','parentId':None})
        file=self.entry('旧目录/查询.ms',{'id':'query','name':'查询','groupId':'deleted'})
        warnings=sync.magic_layout_warnings([group,file])
        self.assertTrue(any('invalidMagicRootParent' in w for w in warnings))
        self.assertTrue(any('brokenMagicParent' in w for w in warnings))


if __name__=='__main__':unittest.main()
