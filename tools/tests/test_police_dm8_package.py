"""Portable checks for DM8 installer serialization, architecture and initialization boundaries."""
import ast
import base64
import importlib.util
import json
from pathlib import Path
import sys
import tarfile
import tempfile
import unittest
from types import SimpleNamespace
from io import BytesIO

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
spec=importlib.util.spec_from_file_location('police_dm8_package',ROOT/'tools/package-police-dm8.py')
package=importlib.util.module_from_spec(spec)
spec.loader.exec_module(package)


class Dm8PackageTests(unittest.TestCase):
    def test_control_structure_has_native_types_constraints_and_views(self):
        statements,tables,views=package.control_statements((ROOT/'db/baseline/init.sql').read_text(encoding='utf8'))
        self.assertEqual(55,tables)
        self.assertEqual(2,views)
        sql='\n'.join(statements)
        self.assertNotIn('`',sql)
        self.assertNotIn(' unsigned',sql)
        self.assertNotIn('ENGINE=',sql)
        self.assertIn('CLOB',sql)
        self.assertIn('FOREIGN KEY',sql)
        self.assertIn('CHECK',sql)
        self.assertIn('CREATE OR REPLACE VIEW "iam_operator_profile_v"',sql)

    def test_unknown_control_definition_is_rejected(self):
        with self.assertRaises(ValueError):package.control_statements('CREATE TABLE `unparsed` (unexpected);')

    def test_native_tenant_preserves_all_tables_and_objects(self):
        statements,count,views=package.tenant_statements()
        self.assertEqual(212,count)
        self.assertEqual(4,views)
        tables=[s for s in statements if s.startswith('CREATE TABLE')]
        self.assertEqual(count,len(tables))
        self.assertTrue(any('CREATE OR REPLACE TRIGGER' in s for s in statements))
        self.assertTrue(any('FOREIGN KEY' in s for s in statements))
        self.assertNotIn('baseline_beijing_gd','\n'.join(statements).lower())

    def test_seed_encoding_preserves_null_empty_unicode_and_code(self):
        text="中文与长脚本\n'a,b' / \"quoted\" "*2000
        literal=lambda value:'CONVERT(0x'+value.encode().hex()+' USING utf8mb4)'
        rows=list(package.rows_from_mysql('USE baseline;\nINSERT INTO `api_file_t` (`tid`,`file_content`,`file_kind`,`is_del`) VALUES ('+literal('validation')+','+literal(text)+",NULL,'');"))
        self.assertEqual(['validation',text,None,''],rows[0][3])
        for raw in ["USE other;\nINSERT INTO `api_file_t` (`tid`) VALUES ('');",
                    "USE baseline;\nINSERT INTO `api_file_t` (`tid`) VALUES (NOW());"]:
            with self.assertRaises(ValueError):list(package.rows_from_mysql(raw))

    def test_arm_vendor_verification_checks_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'vendor.tar.gz'
            for machine,valid in [(183,True),(62,False)]:
                data=b'\x7fELF\x02\x01'+bytes(12)+machine.to_bytes(2,'little')
                with tarfile.open(path,'w:gz') as tar:
                    info=tarfile.TarInfo('runtime/bin/java');info.size=len(data);tar.addfile(info,BytesIO(data))
                if valid:package.check_vendor(path,'bin/java')
                else:
                    with self.assertRaises(ValueError):package.check_vendor(path,'bin/java')

    def initializer(self,path,invoke):
        source=(ROOT/'tools/police-deployment/dm8/standalone.py').read_text(encoding='utf8')
        tree=ast.parse(source)
        # This package also supports the Python supplied with Kylin V10 installations.
        ast.parse(source,feature_version=(3,6))
        function=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='initialize')
        def private(target,content):target.write_text(content,encoding='utf8')
        context={'STATE':path,'json':json,'database':invoke,'rt':SimpleNamespace(private=private)}
        exec(ast.get_source_segment(source,function),context)
        return context['initialize']

    def test_nonempty_precheck_leaves_no_partial_import_marker(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory);calls=[]
            def reject(action,_):calls.append(action);raise ValueError('schema contains objects')
            initialize=self.initializer(path,reject)
            with self.assertRaises(ValueError):initialize({},'validation-target',{})
            self.assertEqual(['check-empty'],calls)
            self.assertFalse((path/'initializing.json').exists())

    def test_repeated_initialized_start_checks_connection_without_import(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory);(path/'initialized.json').write_text(json.dumps({'target':'validation-target'}));calls=[]
            initialize=self.initializer(path,lambda action,_:calls.append(action))
            initialize({},'validation-target',{})
            self.assertEqual(['check'],calls)
            with self.assertRaises(ValueError):initialize({},'different-target',{})
            self.assertEqual(['check'],calls)

    def test_partial_initialization_cannot_blindly_repeat(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory);(path/'initializing.json').write_text('{}');calls=[]
            initialize=self.initializer(path,lambda action,_:calls.append(action))
            with self.assertRaises(RuntimeError):initialize({},'validation-target',{})
            self.assertEqual([],calls)

    def test_other_deployments_environment_cannot_override_database_or_search(self):
        source=(ROOT/'tools/police-deployment/dm8/standalone.py').read_text(encoding='utf8')
        tree=ast.parse(source)
        function=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='clean_environment')
        values={'PATH':'validation-path','LANG':'zh_CN.UTF-8','SPRING_APPLICATION_JSON':'validation-other-binding',
                'SPRING_DATASOURCE_URL':'validation-other-source','NACOS_HOST':'validation-nacos',
                'ELASTICSEARCH_HOST':'validation-search','JAVA_TOOL_OPTIONS':'validation-overrides'}
        context={'os':SimpleNamespace(environ=values)}
        exec(ast.get_source_segment(source,function),context)
        self.assertEqual({'PATH':'validation-path','LANG':'zh_CN.UTF-8'},context['clean_environment']())


if __name__=='__main__':unittest.main()
