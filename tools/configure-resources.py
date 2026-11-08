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


def resolve_configuration(value):
    """Materialize tenant connection references in the owner-only private file."""
    if isinstance(value, dict):
        return {key: resolve_configuration(item) for key, item in value.items()}
    if isinstance(value, list):
        return [resolve_configuration(item) for item in value]
    return resolve(value) if isinstance(value, str) else value


def main():
    import yaml
    from nacos_config import NacosConfigReader
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--nacos-url', default='http://' + os.environ.get('NACOS_HOST', '192.168.175.86')
                        + ':' + os.environ.get('NACOS_PORT', '8848') + '/nacos')
    parser.add_argument('--namespace', default=os.environ.get('NACOS_NAMESPACE', 'data-element'))
    parser.add_argument('--profile', default=os.environ.get('DATA_ELEMENT_PROFILE', 'dev'))
    parser.add_argument('--api-url', default='http://localhost:8088')
    parser.add_argument('--data-id', action='append', help='Explicit complete Nacos YAML Data ID; repeat for ordered overlays')
    parser.add_argument('--output', type=Path,
                        default=Path.home() / '.codex/private/data-elements/resources.local.json')
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    output = private_output(args.output, repo)
    base, api_url = service_url(args.nacos_url), service_url(args.api_url)
    username, password = os.environ.get('NACOS_USER'), os.environ.get('NACOS_PASSWORD')
    if not username or not password:
        raise ConfigurationError('Set NACOS_USER and NACOS_PASSWORD in the invoking environment')
    reader = NacosConfigReader(base, username, password)
    config = {}
    for data_id in (args.data_id or ['data-element.yml', 'data-element-' + args.profile + '.yml']):
        if not re.fullmatch(r'[A-Za-z0-9_.-]+\.ya?ml', data_id):
            raise ConfigurationError('Data ID must be a YAML filename')
        content = reader.read(data_id, 'DEFAULT_GROUP', args.namespace)
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
        'nacos_tenant_database': resolve_configuration(config['data-element']['tenant']['database']),
        'runtime': {'baseUrl': api_url},
        'origin': {'nacosUrl': base, 'namespace': args.namespace, 'profile': args.profile,
                   'dataIds': args.data_id or ['data-element.yml','data-element-'+args.profile+'.yml']},
    }
    if output.exists():
        previous=json.loads(output.read_text(encoding='utf-8-sig'))
        if 'project_databases' in previous:private['project_databases']=previous['project_databases']
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
