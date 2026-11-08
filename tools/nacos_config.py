"""Read Nacos 2/3 configuration without putting authentication tokens in URLs."""
import json
import urllib.error
import urllib.parse
import urllib.request


class NacosConfigError(ValueError):
    """Credential-free operational error suitable for CLI output."""


class NacosConfigReader:
    def __init__(self, base, username, password, opener=None):
        self.base = base.rstrip('/')
        self.username = username
        self.password = password
        self.opener = opener or urllib.request.build_opener(urllib.request.ProxyHandler({}))
        self.token = None

    def _read(self, path, params=None, data=None):
        query = '?' + urllib.parse.urlencode(params) if params else ''
        headers = {'accessToken': self.token, 'Authorization': 'Bearer ' + self.token} if self.token else {}
        body = urllib.parse.urlencode(data).encode('utf-8') if data is not None else None
        request = urllib.request.Request(self.base + path + query, data=body, headers=headers)
        with self.opener.open(request, timeout=15) as response:
            return response.read().decode('utf-8')

    def login(self):
        for path in ('/v3/auth/user/login', '/v1/auth/users/login', '/v1/auth/login'):
            try:
                result = json.loads(self._read(path, data={'username': self.username, 'password': self.password}))
            except urllib.error.HTTPError as error:
                if error.code in (404, 405):
                    continue
                raise NacosConfigError('Nacos authentication failed; credentials were not printed') from None
            token = result.get('accessToken') or (result.get('data') or {}).get('accessToken')
            if not token:
                raise NacosConfigError('Nacos authentication response did not contain a token')
            self.token = token
            return
        raise NacosConfigError('No supported Nacos authentication endpoint found')

    def read(self, data_id, group, namespace):
        if not self.token:
            self.login()
        try:
            raw = self._read('/v3/admin/cs/config', {'dataId': data_id, 'groupName': group,
                                                   'namespaceId': namespace or 'public'})
        except urllib.error.HTTPError as error:
            if error.code not in (404, 405):
                raise NacosConfigError('Nacos configuration read failed with HTTP ' + str(error.code)) from None
        else:
            result = json.loads(raw)
            data = result.get('data')
            if result.get('code') != 0 or not isinstance(data, dict) or not isinstance(data.get('content'), str):
                raise NacosConfigError('Nacos configuration was not found or returned an invalid response')
            return data['content']
        # Nacos 2 retains the legacy resource API. Authentication remains in headers.
        try:
            return self._read('/v1/cs/configs', {'dataId': data_id, 'group': group,
                                              'tenant': '' if namespace == 'public' else namespace})
        except urllib.error.HTTPError as error:
            raise NacosConfigError('Nacos configuration read failed with HTTP ' + str(error.code)) from None
