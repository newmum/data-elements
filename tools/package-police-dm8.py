"""Package the confirmed police release for Kylin aarch64 with an existing DM8 instance."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import re
import shutil
import tarfile
from zipfile import ZipFile, ZIP_DEFLATED, ZIP_STORED, ZipInfo

from haitong_dm_deployment import dm_tenant, quote

ROOT = Path(__file__).resolve().parents[1]
TENANT = '2084109831682699264'
SEPARATOR = '\n-- DM STATEMENT --\n'


def control_statements(source):
    """Translate reviewed structural definitions; application SQL is never rewritten."""
    source = source.replace('\r\n','\n').replace('\r','\n')
    tables = re.findall(r"(?ms)^CREATE TABLE `([^`]+)` \(\n(.*?)\n\) ENGINE=[^\n]*? COMMENT='((?:''|[^'])*)';", source)
    if len(tables) != source.count('CREATE TABLE `'):
        raise ValueError('Unparsed control table definition')
    result, foreign = [], []
    for table, definition, comment in tables:
        columns, notes, indexes = [], [], []
        primary = None
        for raw in definition.splitlines():
            line = raw.strip().removesuffix(',').removesuffix(' USING BTREE')
            if line.startswith('PRIMARY KEY'):
                primary = line.removeprefix('PRIMARY KEY ').replace('`', '"').lower()
                columns.append('PRIMARY KEY ' + primary)
                continue
            key = re.fullmatch(r'(UNIQUE )?KEY `([^`]+)` (\(.*\))', line)
            if key:
                unique, name, fields = key.groups()
                indexes.append((unique, table + '_' + name, fields.replace('`', '"').lower()))
                continue
            if line.startswith('CONSTRAINT '):
                constraint = line.replace('`', '"').replace('_utf8mb4', '')
                if ' FOREIGN KEY ' in line:
                    foreign.append('ALTER TABLE "' + table + '" ADD ' + constraint)
                else:
                    columns.append(constraint)
                continue
            match = re.fullmatch(r"`([^`]+)` (.+?) COMMENT ('(?:''|[^'])*')", line)
            if not match:
                raise ValueError('Unsupported control column: ' + table + ' ' + line)
            name, spec, note = match.groups()
            spec = re.sub(r' (?:COLLATE|CHARACTER SET) \w+', '', spec)
            spec = re.sub(r'\b(?:longtext|mediumtext|text|json)\b', 'CLOB', spec)
            spec = re.sub(r'\bdatetime\b', 'TIMESTAMP', spec)
            spec = re.sub(r'\bCURRENT_TIMESTAMP\(\d+\)', 'CURRENT_TIMESTAMP', spec, flags=re.I)
            spec = re.sub(r'\(utc_timestamp\(\d+\)\)', 'CURRENT_TIMESTAMP', spec, flags=re.I)
            spec = re.sub(r'\btinyint\b', 'SMALLINT', spec)
            spec = re.sub(r'\bbigint unsigned\b', 'DECIMAL(20,0)', spec)
            spec = re.sub(r'\bint unsigned\b', 'BIGINT', spec)
            spec = spec.replace('_utf8mb4', '').replace('`', '"')
            spec = re.sub(r' GENERATED ALWAYS AS (.*?) STORED$', r' AS \1', spec)
            columns.append('"' + name.lower() + '" ' + spec)
            notes.append('COMMENT ON COLUMN "' + table + '"."' + name.lower() + '" IS ' + note)
        result.append('CREATE TABLE "' + table + '" (\n' + ',\n'.join(columns) + '\n)')
        result.extend(['COMMENT ON TABLE "' + table + '" IS ' + quote(comment.replace("''", "'")), *notes])
        for unique, name, fields in indexes:
            if unique and fields == primary:
                continue
            index = name[:38] + '_' + hashlib.sha1(name.encode()).hexdigest()[:10]
            result.append('CREATE ' + (unique or '') + 'INDEX "' + index + '" ON "' + table + '"' + fields)
    result.extend(foreign)
    views = re.findall(r'(?m)^CREATE ALGORITHM=.*? VIEW `([^`]+)` AS (.*);$', source)
    for name, select in views:
        result.append('CREATE OR REPLACE VIEW "' + name + '" AS ' + select.replace('`', '"'))
    return result, len(tables), len(views)


def tenant_statements():
    source = (ROOT/'db/baseline_beijing_gd/init.sql').read_text(encoding='utf8')
    names = set(re.findall(r'^CREATE TABLE "baseline_beijing_gd"\."([^"]+)"', source, re.M))
    ddl, tables = dm_tenant(ROOT, names, TENANT)
    ddl = ddl.replace('"baseline_haitong"', '"baseline_ga"')
    statements = [part.strip().removesuffix('\n/').rstrip().removesuffix(';')
                  for part in ddl.split('\n\n') if part.strip() and part.strip() != '/']
    create = {}
    for sql in statements:
        match = re.match(r'^CREATE TABLE "baseline_ga"\."([^"]+)"', sql)
        if match:
            create[match[1]] = sql
    ordered = {}
    while len(ordered) < len(create):
        previous = len(ordered)
        for name, sql in create.items():
            dependencies = set(re.findall(r'REFERENCES "baseline_ga"\."([^"]+)"', sql))
            if name not in ordered and dependencies - {name} <= ordered.keys():
                ordered[name] = sql
        if len(ordered) == previous:
            raise ValueError('Unresolved DM foreign-key ordering')
    other = [sql for sql in statements if sql not in create.values()]
    views = []
    for line in source.splitlines():
        if re.match(r'^CREATE OR REPLACE\s+VIEW\s+', line, re.I):
            views.append(re.sub('BASELINE_BEIJING_GD', 'baseline_ga', line, flags=re.I).rstrip(';'))
    return [*ordered.values(), *other, *views], len(tables), len(views)


def write_sql(path, statements):
    def terminate(sql):
        sql = sql.strip()
        if re.match(r'CREATE OR REPLACE\s+TRIGGER', sql, re.I):
            return sql.rstrip(';') + ';\n/'
        return sql.rstrip(';') + ';'
    path.write_text(SEPARATOR.join(map(terminate, statements)) + '\n', encoding='utf8', newline='\n')


def rows_from_mysql(sql):
    """Read the builder's hex-only seed serialization, without executing MySQL SQL."""
    owner = None
    for line in sql.splitlines():
        if line.startswith('USE '):
            owner = line[4:].strip(';')
        if not line.startswith('INSERT INTO '):
            continue
        match = re.fullmatch(r'INSERT INTO `([^`]+)` \((.*?)\) VALUES \((.*?)\);', line)
        if not match:
            raise ValueError('Unsupported seed record')
        table, columns, values = match.groups()
        if owner not in ('baseline','baseline_ga') or not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]*',table):
            raise ValueError('Unexpected seed owner/table')
        decoded = []
        for value in values.split(','):
            value = value.strip()
            if value == 'NULL':
                decoded.append(None)
            elif value == "''":
                decoded.append('')
            else:
                encoded = re.fullmatch(r'CONVERT\(0x([0-9a-f]+) USING utf8mb4\)', value)
                if not encoded:
                    raise ValueError('Non-hex seed field')
                decoded.append(bytes.fromhex(encoded[1]).decode('utf8'))
        names = [name.strip('`') for name in columns.split(',')]
        if any(not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]*',name) for name in names):
            raise ValueError('Unexpected seed column')
        if len(names) != len(decoded):
            raise ValueError('Seed column mismatch')
        yield owner, table, names, decoded


def seed_files(stage, source):
    groups = {}
    for file in ('db/03-resources.sql','db/04-police-defaults.sql'):
        for owner, table, columns, values in rows_from_mysql(source.read(file).decode('utf8')):
            record = dict(zip(columns,values))
            columns = sorted(columns)
            values = [record[column] for column in columns]
            key = (owner, table, tuple(columns))
            if key not in groups:
                groups[key] = (columns, [])
            if columns != groups[key][0]:
                raise ValueError('Inconsistent seed columns')
            groups[key][1].append(values)
    manifest = []
    for (owner, table, _), (columns, rows) in groups.items():
        name = owner + '-' + table + '-' + str(len(manifest)+1) + '.tsv'
        content = '\t'.join(columns) + '\n'
        content += ''.join('\t'.join('-' if value is None else '+' + base64.b64encode(value.encode()).decode()
                                     for value in row) + '\n' for row in rows)
        (stage/'db'/name).write_text(content, encoding='ascii', newline='\n')
        manifest.append({'scope': 'control' if owner=='baseline' else 'tenant', 'table': table,
                         'file': name, 'count': len(rows), 'sha256': hashlib.sha256(content.encode()).hexdigest()})
    (stage/'db/seeds.json').write_text(json.dumps(manifest,indent=2),encoding='utf8')
    return manifest


def check_vendor(path, target):
    machines = set()
    with tarfile.open(path) as archive:
        match = next(member for member in archive if member.isfile() and
                     (member.name == target or member.name.endswith('/' + target)))
        with archive.extractfile(match) as file:
            header = file.read(20)
        if header[:6] != b'\x7fELF\x02\x01' or int.from_bytes(header[18:20], 'little') != 183:
            raise ValueError('An aarch64 executable is required: ' + path.name)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--base',type=Path,required=True,help='Confirmed police MySQL complete release; version and public seed source')
    p.add_argument('--arm-vendor',type=Path,required=True)
    p.add_argument('--search-vendor',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--prepare-only',action='store_true',help='Prepare native structures and public seeds for isolated validation')
    args = p.parse_args()
    stage = ROOT/'logs/police-kylin-dm8/stage'
    stage.mkdir(parents=True,exist_ok=True)
    for name in ('db','tools/lib','config','backend','vendor','www'):
        (stage/name).mkdir(parents=True,exist_ok=True)
    with ZipFile(args.base) as source:
        for name in source.namelist():
            if name.startswith('www/') or name=='backend/data-element.jar':
                target=stage/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(source.read(name))
        control, controls, views = control_statements(source.read('db/01-control.sql').decode())
        write_sql(stage/'db/01-control-dm8.sql',control)
        seeds=seed_files(stage,source)
        config=source.read('config/application.yml').decode()
        report=json.loads(source.read('release.json'))
    tenant, tenants, tenant_views=tenant_statements()
    write_sql(stage/'db/02-police-dm8.sql',tenant)
    template=ROOT/'tools/police-deployment/dm8'
    for name in ('standalone.py','Dm8Bootstrap.java'):
        shutil.copy2(template/name,stage/'tools'/name)
    shutil.copy2(template/'preflight.sql',stage/'db/00-preflight-dm8.sql')
    shutil.copy2(ROOT/'tools/police-deployment/runtime.py',stage/'tools/runtime.py')
    for name in ('start.sh','stop.sh','status.sh'):
        shutil.copy2(ROOT/'tools/police-deployment/mysql'/name,stage/name)
    for name in ('database.json.example','ports.json','Caddyfile','部署说明.txt'):
        target=stage/name if name=='部署说明.txt' else stage/'config'/name
        shutil.copy2(template/name,target)
    config=config.replace('com.mysql.cj.jdbc.Driver','dm.jdbc.driver.DmDriver')
    config=config.replace('jdbc:mysql://127.0.0.1:${POLICE_MYSQL_PORT}/baseline?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC','${POLICE_DM_URL}?schema=${POLICE_CONTROL_SCHEMA}')
    config=config.replace('jdbc:mysql://127.0.0.1:${POLICE_MYSQL_PORT}/baseline_ga?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC','${POLICE_DM_URL}?schema=${POLICE_TENANT_SCHEMA}')
    config=config.replace('username: police','username: ${POLICE_CONTROL_USERNAME}',1).replace('username: police','username: ${POLICE_TENANT_USERNAME}')
    config=config.replace('${POLICE_DB_PASSWORD}','${POLICE_CONTROL_PASSWORD}',1).replace('${POLICE_DB_PASSWORD}','${POLICE_TENANT_PASSWORD}')
    config=config.replace('validation-query: SELECT 1','validation-query: SELECT 1 FROM DUAL').replace('data-source-type: mysql','data-source-type: oracle')
    assert 'jdbc:mysql:' not in config
    (stage/'config/application.yml').write_text(config,encoding='utf8',newline='\n')
    with ZipFile(stage/'backend/data-element.jar') as backend:
        libraries=[name for name in backend.namelist() if re.match(r'BOOT-INF/lib/(DmJdbcDriver18-|bcprov-jdk18on-|jackson-(?:databind|core|annotations)-\d).*\.jar$',name,re.I)]
        if len(libraries)!=5:
            raise ValueError('DM JDBC, SM3 and Jackson providers are required')
        for name in libraries:
            (stage/'tools/lib'/Path(name).name).write_bytes(backend.read(name))
    if args.prepare_only:
        print(json.dumps({'stage':str(stage),'controlTables':controls,'controlViews':views,
                         'tenantTables':tenants,'tenantViews':tenant_views,'seedTables':len(seeds)}))
        return
    vendors={'java21.tar.gz':('bin/java',args.arm_vendor/'java21.tar.gz'),
             'redis.tar.gz':('usr/bin/valkey-server',args.arm_vendor/'redis.tar.gz'),
             'caddy.tar.gz':('caddy',args.arm_vendor/'caddy.tar.gz'),
             'elasticsearch.tar.gz':('jdk/bin/java',args.search_vendor)}
    for name,(executable,path) in vendors.items():
        check_vendor(path,executable);shutil.copy2(path,stage/'vendor'/name)
    check_vendor(args.arm_vendor/'redis.tar.gz','lib/ld-musl-aarch64.so.1')
    check_vendor(args.arm_vendor/'java21.tar.gz','lib/server/libjvm.so')
    report.update(architecture='aarch64',system='Kylin Linux',database='DM8 existing instance',tenantSchema='baseline_ga (configurable)',
                  controlTables=controls,controlViews=views,tenantTables=tenants,tenantViews=tenant_views,
                  sourceBundleSha256=hashlib.sha256(args.base.read_bytes()).hexdigest())
    (stage/'release.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
    files=[path for path in stage.rglob('*') if path.is_file() and path.name!='package-manifest.sha256']
    for path in files:
        relative=path.relative_to(stage)
        if relative.parts[0] in ('data','runtime','logs') or path.name=='database.json' or '.private' in path.name:
            raise ValueError('Runtime state/private configuration cannot enter the package')
    manifest=''.join(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.relative_to(stage).as_posix()+'\n' for path in sorted(files))
    (stage/'package-manifest.sha256').write_text(manifest,encoding='utf8',newline='\n')
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with ZipFile(args.output,'w',ZIP_DEFLATED) as archive:
        for path in stage.rglob('*'):
            if not path.is_file():continue
            name=path.relative_to(stage).as_posix()
            if path.suffix=='.sh':
                info=ZipInfo(name);info.create_system=3;info.external_attr=0o100755<<16
                archive.writestr(info,path.read_bytes().replace(b'\r\n',b'\n'))
            else:
                archive.write(path,name,compress_type=ZIP_STORED if path.suffix in ('.gz','.jar') else ZIP_DEFLATED)
    with ZipFile(args.output) as archive:
        if archive.testzip() is not None:raise ValueError('Package integrity failure')
    sha=hashlib.sha256(args.output.read_bytes()).hexdigest()
    (args.output.parent/'SHA256SUMS.txt').write_text(sha+'  '+args.output.name+'\n',encoding='ascii')
    print(json.dumps(report,ensure_ascii=False),str(args.output),sha)


if __name__=='__main__':main()
