import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile
import json
import hashlib

spec = importlib.util.spec_from_file_location('overlay', Path(__file__).parents[1] / 'package-backend-overlay.py')
overlay = importlib.util.module_from_spec(spec)
spec.loader.exec_module(overlay)


class BackendOverlayTest(unittest.TestCase):
    def test_explicit_manifest_is_read_without_collecting_config(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)
            (root/'manifests/sinks').mkdir(parents=True)
            (root/'manifests/sinks/hive.json').write_text('{"id":"hive"}')
            name,content=overlay.manifest_entry(root,'manifests/sinks/hive.json')
            self.assertEqual('BOOT-INF/classes/manifests/sinks/hive.json',name)
            self.assertEqual({'id':'hive'},json.loads(content))
            for forbidden in ['application.yml','../secrets.json','manifests/../config.json']:
                with self.assertRaises(ValueError):overlay.manifest_entry(root,forbidden)

    def test_preserves_unselected_entries_and_refuses_concurrent_class_change(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            base, delta, out = root/'base.jar', root/'delta.zip', root/'out.jar'
            touched = 'BOOT-INF/classes/Mapping.class'
            with zipfile.ZipFile(base, 'w') as z:
                z.writestr(touched, b'old')
                z.writestr('BOOT-INF/classes/Unrelated.class', b'unchanged')
                z.writestr('BOOT-INF/classes/application.yml', b'unchanged-config')
            with zipfile.ZipFile(delta, 'w') as z:
                z.writestr(touched, b'new')
                z.writestr('overlay.json', json.dumps({'entries':[{'name':touched,'originalSha256':hashlib.sha256(b'old').hexdigest(),'sha256':hashlib.sha256(b'new').hexdigest()}]}))
            overlay.apply(base,delta,out)
            with zipfile.ZipFile(out) as z:
                self.assertEqual(b'new',z.read(touched))
                self.assertEqual(b'unchanged',z.read('BOOT-INF/classes/Unrelated.class'))
                self.assertEqual(b'unchanged-config',z.read('BOOT-INF/classes/application.yml'))
            with self.assertRaises(ValueError):
                overlay.apply(out,delta,root/'concurrent.jar')


if __name__ == '__main__':
    unittest.main()
