"""Read current Nacos API configuration into a private, untracked resource config."""
import argparse
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import urllib.parse
import urllib.request


class ConfigurationError(ValueError):
    """Deliberate credential-free validation messages safe for console output."""


def private_output(path, repo):
    logical = Path(os.path.abspath(path))
    if logical.is_relative_to(Path(os.path.abspath(repo))) or path.resolve().is_relative_to(repo.resolve()):
        raise ConfigurationError('Private configuration must be saved outside this Git workspace')
    return logical


def service_url(value):
    try:
        parsed = urllib.parse.urlsplit(value)
        if (parsed.scheme not in {'http', 'https'} or not parsed.hostname or parsed.username
                or parsed.password or parsed.query or parsed.fragment):
            raise ConfigurationError('Service URL requires HTTP(S), a host, and no embedded credentials/query/fragment')
        parsed.port
    except ValueError as error:
        if isinstance(error, ConfigurationError):
            raise
        raise ConfigurationError('Invalid service URL') from None
    return value.rstrip('/')


def resolve(value):
    value = str(value)
    def replace(match):
        name, default = match.groups()
        result = os.environ.get(name, default)
        if result is None:
            raise ConfigurationError('Missing environment setting: ' + name)
        return result
    return re.sub(r'\$\{([A-Za-z_][A-Za-z0-9_]*)(?::([^}]*))?\}', replace, value)


def merge(target, incoming):
    for key, value in incoming.items():
        if isinstance(value, dict) and isinstance(target.get(key), dict):
            merge(target[key], value)
        else:
            target[key] = value


def main():
    import yaml
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--nacos-url', default='http://' + os.environ.get('NACOS_HOST', '192.168.175.86')
                        + ':' + os.environ.get('NACOS_PORT', '8848') + '/nacos')
    parser.add_argument('--namespace', default=os.environ.get('NACOS_NAMESPACE', 'data-element'))
    parser.add_argument('--profile', default=os.environ.get('DATA_ELEMENT_PROFILE', 'dev'))
    parser.add_argument('--api-url', default='http://localhost:8088')
    parser.add_argument('--output', type=Path,
                        default=Path.home() / '.codex/private/data-elements/resources.local.json')
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    output = private_output(args.output, repo)
    base, api_url = service_url(args.nacos_url), service_url(args.api_url)
    username, password = os.environ.get('NACOS_USER'), os.environ.get('NACOS_PASSWORD')
    if not username or not password:
        raise ConfigurationError('Set NACOS_USER and NACOS_PASSWORD in the invoking environment')
    request = urllib.request.Request(base + '/v1/auth/login', data=urllib.parse.urlencode(
        {'username': username, 'password': password}).encode('utf-8'))
    token = json.load(urllib.request.urlopen(request, timeout=15))['accessToken']
    config = {}
    for data_id in ['data-element.yml', 'data-element-' + args.profile + '.yml']:
        query = urllib.parse.urlencode({'dataId': data_id, 'group': 'DEFAULT_GROUP',
                                        'tenant': args.namespace, 'accessToken': token})
        content = urllib.request.urlopen(base + '/v1/cs/configs?' + query, timeout=15).read().decode('utf-8')
        merge(config, yaml.safe_load(content) or {})
    datasource = config['spring']['datasource']
    url = resolve(datasource['url'])
    if not url.startswith('jdbc:mysql://'):
        raise ConfigurationError('Shared resource control datasource must use supported MySQL dialect')
    parsed = urllib.parse.urlsplit(url[5:])
    private = {
        'control': {'host': parsed.hostname, 'port': parsed.port or 3306,
                    'database': parsed.path.strip('/'), 'user': resolve(datasource['username']),
                    'password': resolve(datasource['password']), 'charset': 'utf8mb4'},
        'nacos_tenant_database': config['data-element']['tenant']['database'],
        'runtime': {'baseUrl': api_url},
        'origin': {'nacosUrl': base, 'namespace': args.namespace, 'profile': args.profile},
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    private_output(output, repo)
    # Temporary files inherit owner-only mode on POSIX; replace avoids following a leaf symlink.
    with tempfile.NamedTemporaryFile('w', encoding='utf-8', newline='\n', dir=output.parent,
                                     delete=False) as stream:
        stream.write(json.dumps(private, ensure_ascii=False, indent=2) + '\n')
    Path(stream.name).replace(output)
    print(json.dumps({'config': str(output), 'controlDatabase': private['control']['database'],
                      'tenantDatasourceCount': len(private['nacos_tenant_database']['data-sources'])}))


if __name__ == '__main__':
    try:
        main()
    except Exception as exc:
        print(str(exc) if isinstance(exc, ConfigurationError) else
              'Nacos configuration read failed (' + type(exc).__name__ + '); no credentials printed.',
              file=sys.stderr)
        sys.exit(1)
