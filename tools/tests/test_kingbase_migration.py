"""Migration invariants that protect typed comparisons and FK ordering."""
import importlib.util
import hashlib
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('kingbase_db',Path(__file__).parents[1]/'kingbase-db.py')
kb=importlib.util.module_from_spec(spec)
spec.loader.exec_module(kb)


class KingbaseMigrationTest(unittest.TestCase):
    def test_digest_accepts_native_serialization_without_ignoring_real_change(self):
        columns=[{'DATA_TYPE':t} for t in ['bigint','decimal','timestamp','json','char']]
        source=[['42','10.00','2026-11-05 16:05:00','{"a":1,"b":2}','A ']]
        native=[['42','10','2026-11-05T16:05:00.000000','{"b":2,"a":1}','A']]
        self.assertEqual(kb.data_digest(source,columns),kb.data_digest(native,columns))
        native[0][3]='{"b":3,"a":1}'
        self.assertNotEqual(kb.data_digest(source,columns),kb.data_digest(native,columns))

    def test_empty_string_remains_distinct_from_null(self):
        column=[{'DATA_TYPE':'varchar'}]
        self.assertNotEqual(kb.data_digest([['']],column),kb.data_digest([[None]],column))

    def test_native_text_fingerprints_compare_complete_unicode_and_null(self):
        columns=[{'DATA_TYPE':'text','DIGEST_TEXT':True}]
        text='业务内容\n"value"'
        native=[[hashlib.sha256(text.encode('utf-8')).hexdigest()],[None]]
        self.assertEqual(kb.data_digest([[text],[None]],columns),kb.data_digest(native,columns,text_fingerprints=True))
        self.assertNotEqual(kb.data_digest([['']],columns),kb.data_digest([[None]],columns))

    def test_parent_order_is_independent_of_table_name_and_cycles_fail(self):
        tables={'a_child':{'foreign':[{'REFERENCED_TABLE_NAME':'z_parent'}]},
                'z_parent':{'foreign':[]}}
        self.assertEqual(kb.dependency_order(tables),['z_parent','a_child'])
        tables['z_parent']['foreign']=[{'REFERENCED_TABLE_NAME':'a_child'}]
        with self.assertRaises(ValueError):kb.dependency_order(tables)

    def test_unsigned_type_does_not_lose_high_values(self):
        self.assertEqual(kb.column_type({'DATA_TYPE':'smallint','COLUMN_TYPE':'smallint unsigned'}),'integer')
        self.assertEqual(kb.column_type({'DATA_TYPE':'bigint','COLUMN_TYPE':'bigint unsigned'}),'numeric(20,0)')

    def test_identifier_validation_rejects_sql_and_normalizes_case(self):
        self.assertEqual(kb.ident('TID'),'"tid"')
        with self.assertRaises(ValueError):kb.ident('tid; DELETE FROM x')


if __name__=='__main__':unittest.main()
