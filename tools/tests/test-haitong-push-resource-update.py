import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('push_resource_update',Path(__file__).resolve().parents[1]/'update-haitong-push-resources.py')
updater=importlib.util.module_from_spec(spec);spec.loader.exec_module(updater)


class PushResourceUpdateTest(unittest.TestCase):
    def setUp(self):
        self.entry={'kind':'function','previousScriptSha256':updater.digest('return old')}
        self.old={'id':'selected','path':'/selected','groupId':'domain','script':'return old\r\n'}
        self.new=dict(self.old,script='return current')

    def test_known_predecessor_and_idempotent_current_are_allowed(self):
        updater.verify_predecessor(self.old,self.new,self.entry)
        updater.verify_predecessor(self.new,self.new,self.entry)

    def test_custom_script_requires_merge(self):
        with self.assertRaisesRegex(ValueError,'custom changes'):
            updater.verify_predecessor(dict(self.old,script='return custom'),self.new,self.entry)

    def test_same_name_does_not_override_different_identity_or_group(self):
        for key in ('id','path','groupId'):
            with self.subTest(key=key),self.assertRaisesRegex(ValueError,'identity/path/group'):
                updater.verify_predecessor(dict(self.old,**{key:'other'}),self.new,self.entry)

    def test_missing_or_duplicate_api_header_is_rejected(self):
        self.entry['kind']='api'
        for headers in ([],[{'name':'X-Api-Key'},{'name':'X-Api-Key'}]):
            with self.subTest(headers=headers),self.assertRaisesRegex(ValueError,'header definition'):
                updater.verify_predecessor(dict(self.old,headers=headers),self.new,self.entry)
        updater.verify_predecessor(dict(self.old,headers=[{'name':'X-Api-Key','required':True}]),self.new,self.entry)


if __name__=='__main__':unittest.main()
