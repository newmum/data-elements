import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('audit_api', Path(__file__).parents[1] / 'audit-api.py')
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class ApiReferencesTest(unittest.TestCase):
    def test_retired_commented_request_is_not_evidence(self):
        _, values = audit.resolved_literals('// request("/old/list")\nrequest("/active/list")')
        self.assertEqual([v for _, v in values], ['/active/list'])

    def test_base_and_environment_prefix_preserve_request_path(self):
        _, values = audit.resolved_literals('const BASE = "/idaas/users"; get(BASE + "/list"); get(`${envBase}/ods/task/queryById`);')
        self.assertIn('/idaas/users/list', [v for _, v in values])
        self.assertIn('/ods/task/queryById', [v for _, v in values])

    def test_ternary_question_mark_is_not_query_separator(self):
        value = "/idaas/${isRole ? 'platform-roles' : 'operators'}/save?x=1"
        self.assertTrue(audit.template_matches('/idaas/operators/save', value, set()))
        self.assertFalse(audit.template_matches('/idaas/operators/remove', value, set()))
        self.assertFalse(audit.template_matches('/idaas/users/save', value, set()))

    def test_table_dispatch_only_matches_declared_values(self):
        self.assertTrue(audit.template_matches('/idaas/users/save', '/idaas/${path}/save', {'users', 'orgs'}))
        self.assertFalse(audit.template_matches('/idaas/operators/save', '/idaas/${path}/save', {'users', 'orgs'}))

    def test_registered_absolute_service_url_matches_route(self):
        self.assertEqual(audit.match_reference('/dws/published/one', 'http://localhost:8088/dws/published/one'), 'literal')
        self.assertIsNone(audit.match_reference('/dws/published/one', '/dws/published/one-copy'))

    def test_path_parameter_matches_one_segment(self):
        self.assertEqual(audit.match_reference('/flows/{id}/publish', '/flows/123/publish'), 'literal')
        self.assertIsNone(audit.match_reference('/flows/{id}/publish', '/flows/123/extra/publish'))

    def test_regex_quotes_do_not_hide_real_vue_request(self):
        resource = {'id': 'structured', 'file': 'magic/api/columns.ms', 'method': 'POST',
                    'route': '/dst/database/metadata/structuredColumns', 'section': 'api', 'script': ''}
        document = {'label': 'lowcode/register.vue', 'text': '''<script setup>
const pattern = /["']/;
request("/dst/database/metadata/structuredColumns");
</script>'''}
        with patch.object(audit, 'inventory', return_value=[resource]), patch.object(audit, 'corpus', return_value=[document]):
            report = audit.audit()
        self.assertEqual(report['resources'][0]['classification'], 'literalReference')

    def test_current_java_can_dispatch_by_metadata_id(self):
        resource = {'id': 'metadata-dispatch', 'file': 'magic/api/dispatch.ms', 'method': 'POST',
                    'route': '/dispatch', 'section': 'api', 'script': ''}
        document = {'label': 'runtime-java/Dispatch.class', 'text': '["metadata-dispatch"]'}
        with patch.object(audit, 'inventory', return_value=[resource]), patch.object(audit, 'corpus', return_value=[document]):
            report = audit.audit()
        self.assertEqual(report['resources'][0]['classification'], 'literalReference')


if __name__ == '__main__':
    unittest.main()
