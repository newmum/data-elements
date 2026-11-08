"""Verify version compatibility, credentials transport, and namespace boundaries."""
import importlib.util
import io
import json
from pathlib import Path
import unittest
from unittest.mock import patch
import urllib.error
import urllib.parse

spec = importlib.util.spec_from_file_location('nacos_config', Path(__file__).resolve().parents[1] / 'nacos_config.py')
reader_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reader_module)
config_spec = importlib.util.spec_from_file_location('configure_resources', Path(__file__).resolve().parents[1] / 'configure-resources.py')
config_module = importlib.util.module_from_spec(config_spec)
config_spec.loader.exec_module(config_module)


class StubOpener:
    def __init__(self, routes):
        self.routes = routes
        self.requests = []

    def open(self, request, timeout):
        self.requests.append(request)
        path = urllib.parse.urlsplit(request.full_url).path
        response = self.routes.get(path, 404)
        if isinstance(response, int):
            raise urllib.error.HTTPError(request.full_url, response, 'failure', {}, None)
        return io.BytesIO(response.encode())


class NacosConfigReaderTest(unittest.TestCase):
    def reader(self, routes):
        opener = StubOpener(routes)
        return reader_module.NacosConfigReader('http://validation:8848/nacos', 'validation-user',
                                               'validation-password', opener), opener

    def test_v3_preserves_requested_namespace_and_keeps_tokens_out_of_urls(self):
        reader, opener = self.reader({
            '/nacos/v3/auth/user/login': json.dumps({'accessToken': 'validation-token'}),
            '/nacos/v3/admin/cs/config': json.dumps({'code': 0, 'data': {'content': 'server:\n  port: 8088\n'}}),
        })
        self.assertIn('8088', reader.read('chengtian-14.yml', 'DEFAULT_GROUP', 'public'))
        self.assertEqual(2, len(opener.requests))
        request = opener.requests[-1]
        params = urllib.parse.parse_qs(urllib.parse.urlsplit(request.full_url).query)
        self.assertEqual(['public'], params['namespaceId'])
        self.assertEqual(['chengtian-14.yml'], params['dataId'])
        for request in opener.requests:
            self.assertNotIn('validation-token', request.full_url)
            self.assertNotIn('validation-password', request.full_url)
        self.assertEqual('validation-token', opener.requests[-1].get_header('Accesstoken'))

    def test_v2_falls_back_without_changing_named_namespace(self):
        reader, opener = self.reader({
            '/nacos/v1/auth/login': json.dumps({'accessToken': 'validation-token'}),
            '/nacos/v1/cs/configs': 'server:\n  port: 8088\n',
        })
        self.assertIn('8088', reader.read('data-element.yml', 'DEFAULT_GROUP', 'data-element'))
        params = urllib.parse.parse_qs(urllib.parse.urlsplit(opener.requests[-1].full_url).query)
        self.assertEqual(['data-element'], params['tenant'])
        self.assertNotIn('accessToken', params)

    def test_authentication_rejection_does_not_retry_or_disclose_password(self):
        reader, opener = self.reader({'/nacos/v3/auth/user/login': 403})
        with self.assertRaises(reader_module.NacosConfigError) as caught:
            reader.read('private.yml', 'DEFAULT_GROUP', 'validation')
        self.assertEqual(1, len(opener.requests))
        self.assertNotIn('validation-password', str(caught.exception))

    def test_invalid_v3_response_is_not_silently_treated_as_legacy_content(self):
        reader, opener = self.reader({
            '/nacos/v3/auth/user/login': json.dumps({'accessToken': 'validation-token'}),
            '/nacos/v3/admin/cs/config': json.dumps({'code': 403, 'message': 'denied'}),
        })
        with self.assertRaises(reader_module.NacosConfigError):
            reader.read('private.yml', 'DEFAULT_GROUP', 'restricted')
        self.assertEqual(2, len(opener.requests))

    def test_private_tenant_connections_resolve_each_sources_own_credential(self):
        value = {'enabled': True, 'tenant-bindings': {'tenant-a': 'source-a', 'tenant-b': 'source-b'},
                 'data-sources': {'source-a': {'password': '${VALIDATION_SOURCE_A}'},
                                  'source-b': {'password': '${VALIDATION_SOURCE_B}'}}}
        with patch.dict('os.environ', {'VALIDATION_SOURCE_A': 'validation-a', 'VALIDATION_SOURCE_B': 'validation-b'}):
            resolved = config_module.resolve_configuration(value)
        self.assertIs(True, resolved['enabled'])
        self.assertEqual(value['tenant-bindings'], resolved['tenant-bindings'])
        self.assertEqual('validation-a', resolved['data-sources']['source-a']['password'])
        self.assertEqual('validation-b', resolved['data-sources']['source-b']['password'])

    def test_unresolved_private_credential_is_rejected(self):
        with patch.dict('os.environ', {}, clear=True):
            with self.assertRaises(config_module.ConfigurationError):
                config_module.resolve_configuration({'password': '${VALIDATION_MISSING_SECRET}'})


if __name__ == '__main__':
    unittest.main()
