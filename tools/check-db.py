#!/usr/bin/env python3
"""Read-only industry schema gate: GA authority, tenant defaults, native DM semantics.

Control-plane tables and deployment-owned views are separate. This command never
changes a database. Differences make its exit status nonzero.
"""
import argparse
import collections
from decimal import Decimal
import importlib.util
import json
from pathlib import Path
import re
import sys

spec = importlib.util.spec_from_file_location('export_db', Path(__file__).with_name('export-db.py'))
export = importlib.util.module_from_spec(spec)
spec.loader.exec_module(export)
AUTHORITY = 'baseline_ga_old'


def grouped(rows, table_key, name_key):
    result = collections.defaultdict(dict)
    for row in rows:
        name = row[name_key].lower()
        if name in result[row[table_key]]:
            raise ValueError('Case-insensitive column identity collision')
        result[row[table_key]][name] = row
    return result


def mysql_metadata(schema, options):
    import pymysql
    connection = pymysql.connect(**options, cursorclass=pymysql.cursors.DictCursor,
                                 connect_timeout=15, read_timeout=120)
    try:
        with connection.cursor() as q:
            def read(sql):
                q.execute(sql, [schema])
                return q.fetchall()
            tables = read("SELECT TABLE_NAME,TABLE_COMMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA=%s AND TABLE_TYPE='BASE TABLE'")
            columns = read('SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,EXTRA,COLUMN_COMMENT,CHARACTER_SET_NAME,COLLATION_NAME,GENERATION_EXPRESSION FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=%s')
            indexes = read('SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,COLUMN_NAME,SEQ_IN_INDEX,SUB_PART,COLLATION FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=%s ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX')
            foreign = read('SELECT TABLE_NAME,CONSTRAINT_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME,ORDINAL_POSITION FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=%s AND REFERENCED_TABLE_NAME IS NOT NULL ORDER BY TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION')
            primary = read("SELECT TABLE_NAME,COLUMN_NAME,ORDINAL_POSITION FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=%s AND CONSTRAINT_NAME='PRIMARY' ORDER BY TABLE_NAME,ORDINAL_POSITION")
        return {'tables': {t['TABLE_NAME']: t['TABLE_COMMENT'] for t in tables},
                'columns': grouped(columns, 'TABLE_NAME', 'COLUMN_NAME'),
                'indexes': index_sets(indexes), 'foreignKeys': foreign_sets(foreign), 'primaryKeys': primary_sets(primary),
                'dialect': 'mysql'}
    finally:
        connection.close()


def index_sets(rows):
    groups = collections.defaultdict(list)
    for r in rows:
        groups[(r['TABLE_NAME'], r['INDEX_NAME'])].append(r)
    result = collections.defaultdict(set)
    for (table, _), entries in groups.items():
        result[table].add((bool(entries[0]['NON_UNIQUE']), tuple(
            (r['COLUMN_NAME'].lower(), r.get('SUB_PART'), r.get('COLLATION', 'A')) for r in entries)))
    return result


def foreign_sets(rows):
    groups = collections.defaultdict(list)
    for row in rows:
        groups[(row['TABLE_NAME'], row['CONSTRAINT_NAME'])].append(row)
    result = collections.defaultdict(set)
    for (table, _), entries in groups.items():
        result[table].add(tuple((r['COLUMN_NAME'].lower(), r['REFERENCED_TABLE_NAME'], r['REFERENCED_COLUMN_NAME'].lower()) for r in entries))
    return result


def primary_sets(rows):
    result = collections.defaultdict(list)
    for row in rows:
        result[row['TABLE_NAME']].append(row['COLUMN_NAME'].lower())
    return {table:tuple(cols) for table,cols in result.items()}


def dameng_metadata(schema, options):
    import jaydebeapi
    connection = jaydebeapi.connect(options['driver-class-name'], options['url'],
                                   [options['username'], options['password']], str(export.dm_jar()))
    q = connection.cursor()
    try:
        def read(sql, args):
            q.execute(sql, args)
            names = [c[0] for c in q.description]
            return [dict(zip(names, row)) for row in q.fetchall()]
        tables = read('SELECT TABLE_NAME,COMMENTS FROM DBA_TAB_COMMENTS WHERE OWNER=? AND TABLE_TYPE=\'TABLE\'', [schema])
        columns = read('SELECT TABLE_NAME,COLUMN_NAME,DATA_TYPE,DATA_LENGTH,DATA_PRECISION,DATA_SCALE,NULLABLE,DATA_DEFAULT FROM DBA_TAB_COLUMNS WHERE OWNER=?', [schema])
        comments = grouped(read('SELECT TABLE_NAME,COLUMN_NAME,COMMENTS FROM DBA_COL_COMMENTS WHERE OWNER=?', [schema]), 'TABLE_NAME', 'COLUMN_NAME')
        for c in columns:
            c['COLUMN_COMMENT'] = comments[c['TABLE_NAME']][c['COLUMN_NAME'].lower()]['COMMENTS'] or ''
        kinds = {r['INDEX_NAME']: r['UNIQUENESS'] for r in read('SELECT INDEX_NAME,UNIQUENESS FROM DBA_INDEXES WHERE OWNER=?', [schema])}
        indexes = read('SELECT TABLE_NAME,INDEX_NAME,COLUMN_NAME,COLUMN_POSITION FROM DBA_IND_COLUMNS WHERE INDEX_OWNER=? ORDER BY TABLE_NAME,INDEX_NAME,COLUMN_POSITION', [schema])
        for r in indexes:
            r['NON_UNIQUE'] = 0 if kinds[r['INDEX_NAME']] == 'UNIQUE' else 1
        # DM expands virtual-column dependencies in DBA_IND_COLUMNS. Its actual
        # INDEX DDL records the logical indexed columns; verify that representation.
        generated_tables = {c['TABLE_NAME'] for c in columns if c['DATA_DEFAULT'] and ' CASE ' in str(c['DATA_DEFAULT']).upper()}
        for table in generated_tables:
            names = {r['INDEX_NAME'] for r in indexes if r['TABLE_NAME']==table}
            replacement = []
            for name in names:
                q.execute('SELECT DBMS_METADATA.GET_DDL(?,?,?) FROM DUAL', ['INDEX',name,schema])
                text = q.fetchone()[0]
                text = text.getSubString(1,int(text.length())) if hasattr(text,'getSubString') else str(text)
                if re.search(r'\bCLUSTER\s+INDEX\b', text, re.I):
                    continue
                match = re.search(r'\bON\s+"[^"]+"\."[^"]+"\((.*?)\)\s+STORAGE',text,re.S|re.I)
                if not match:raise ValueError('Unsupported native generated-column index DDL')
                for position,part in enumerate(match[1].split(','),1):
                    col = re.fullmatch(r'\s*"?(\w+)"?\s*(?:ASC)?\s*',part,re.I)
                    if not col:raise ValueError('Unsupported native functional-index expression')
                    replacement.append({'TABLE_NAME':table,'INDEX_NAME':name,'COLUMN_NAME':col[1],
                                        'COLUMN_POSITION':position,'NON_UNIQUE':0 if kinds[name]=='UNIQUE' else 1})
            indexes = [r for r in indexes if r['TABLE_NAME']!=table] + replacement
        # JDBC's DM driver reports underlying column names, not SQL aliases. Use tuples for this join.
        q.execute("SELECT C.TABLE_NAME,C.CONSTRAINT_NAME,CC.COLUMN_NAME,P.TABLE_NAME,PC.COLUMN_NAME FROM DBA_CONSTRAINTS C JOIN DBA_CONS_COLUMNS CC ON CC.OWNER=C.OWNER AND CC.CONSTRAINT_NAME=C.CONSTRAINT_NAME JOIN DBA_CONSTRAINTS P ON P.OWNER=C.R_OWNER AND P.CONSTRAINT_NAME=C.R_CONSTRAINT_NAME JOIN DBA_CONS_COLUMNS PC ON PC.OWNER=P.OWNER AND PC.CONSTRAINT_NAME=P.CONSTRAINT_NAME AND PC.POSITION=CC.POSITION WHERE C.OWNER=? AND C.CONSTRAINT_TYPE='R' ORDER BY C.TABLE_NAME,C.CONSTRAINT_NAME,CC.POSITION", [schema])
        foreign = [dict(zip(['TABLE_NAME','CONSTRAINT_NAME','COLUMN_NAME','REFERENCED_TABLE_NAME','REFERENCED_COLUMN_NAME'], r)) for r in q.fetchall()]
        primary = read("SELECT C.TABLE_NAME,CC.COLUMN_NAME FROM DBA_CONSTRAINTS C JOIN DBA_CONS_COLUMNS CC ON CC.OWNER=C.OWNER AND CC.CONSTRAINT_NAME=C.CONSTRAINT_NAME WHERE C.OWNER=? AND C.CONSTRAINT_TYPE='P' ORDER BY C.TABLE_NAME,CC.POSITION", [schema])
        triggers = read('SELECT TABLE_NAME,STATUS FROM DBA_TRIGGERS WHERE OWNER=?', [schema])
        return {'tables': {t['TABLE_NAME']: t['COMMENTS'] or '' for t in tables},
                'columns': grouped(columns, 'TABLE_NAME', 'COLUMN_NAME'),
                'indexes': index_sets(indexes), 'foreignKeys': foreign_sets(foreign), 'primaryKeys': primary_sets(primary),
                'updateTriggerTables': {t['TABLE_NAME'] for t in triggers if t['STATUS'] in {'ENABLED','Y'}},
                'dialect': 'dameng'}
    finally:
        q.close()
        connection.close()


def native_type_matches(actual, source):
    wanted = source['COLUMN_TYPE'].lower()
    kind = actual['DATA_TYPE'].upper()
    if re.fullmatch(r'(?:var)?char\(\d+\)', wanted):
        return kind in ({'CHAR','CHARACTER'} if wanted.startswith('char') else {'VARCHAR','VARCHAR2'}) and int(actual['DATA_LENGTH'])==int(re.search(r'\d+',wanted)[0])
    if wanted in {'json','text','longtext'}:
        return kind in {'CLOB','TEXT','LONGVARCHAR'}
    if wanted=='longblob':
        return kind in {'BLOB','LONGVARBINARY'}
    if wanted.startswith('datetime'):
        precision = int(re.search(r'\((\d+)\)', wanted)[1]) if '(' in wanted else 0
        return kind in {'TIMESTAMP','DATETIME'} and int(actual['DATA_SCALE'] or 0)>=precision
    if wanted=='date':
        return kind=='DATE' or (kind=='TIMESTAMP' and int(actual['DATA_SCALE'] or 0)==0)
    if wanted=='int unsigned':
        return kind=='BIGINT'
    if wanted=='bigint unsigned':
        wanted='decimal(20,0)'
    if wanted.startswith('decimal('):
        precision, scale=map(int,re.findall(r'\d+',wanted))
        return kind in {'DECIMAL','NUMERIC','NUMBER'} and int(actual['DATA_PRECISION'] or 0)==precision and int(actual['DATA_SCALE'] or 0)==scale
    if wanted.startswith('tinyint'):
        return kind=='SMALLINT'
    return kind in ({'INT','INTEGER'} if wanted=='int' else {wanted.upper()})


def default_value(value, native=False):
    if value is None:
        return None
    text=str(value).strip()
    if text.upper()=='NULL':
        return None
    if re.fullmatch(r'CURRENT_TIMESTAMP(?:\(\d*\))?',text,re.I):
        return 'CURRENT_TIMESTAMP'
    if re.fullmatch(r'\(?\s*(?:utc_timestamp\(\d*\)|SYS_EXTRACT_UTC\(CURRENT_TIMESTAMP\))\s*\)?',text,re.I):
        return 'UTC_TIMESTAMP'
    if native and text.startswith("'") and text.endswith("'"):
        return text[1:-1].replace("''", "'")
    return text


def generated_expression(value):
    # Current business rule is a simple CASE expression; quoting and function
    # parentheses differ across dialects without changing its tokens.
    return re.sub(r'[\s`"()]','',value or '').lower()


def compare(source, target, tenant, source_tenant):
    differences=[]
    def issue(table, kind, column=None):
        differences.append({'table':table,'difference':kind, **({'column':column} if column else {})})
    for table in sorted(set(source['tables'])-set(target['tables'])):
        issue(table,'missing-table')
    for table in sorted(set(target['tables'])-set(source['tables'])):
        issue(table,'extra-table')
    native=target['dialect']=='dameng'
    for table in sorted(set(source['tables'])&set(target['tables'])):
        if source['tables'][table]!=target['tables'][table]:
            issue(table,'table-comment')
        expected=source['columns'][table]; actual=target['columns'][table]
        for col in sorted(set(expected)-set(actual)):
            issue(table,'missing-column',col)
        for col in sorted(set(actual)-set(expected)):
            issue(table,'extra-column',col)
        for col in sorted(set(expected)&set(actual)):
            wanted, found=expected[col],actual[col]
            if native:
                if not native_type_matches(found,wanted):issue(table,'column-type',col)
                nullable='NO' if found['NULLABLE']=='N' else 'YES'
                # One exact, documented Oracle empty-string representation exception.
                if nullable!=wanted['IS_NULLABLE'] and not (table=='res_logical_model_field' and col=='entity_id' and nullable=='YES' and wanted['COLUMN_DEFAULT']==''):
                    issue(table,'column-nullability',col)
            else:
                for field in ['COLUMN_TYPE','IS_NULLABLE','EXTRA','CHARACTER_SET_NAME','COLLATION_NAME','GENERATION_EXPRESSION']:
                    if wanted[field]!=found[field]:issue(table,field.lower(),col)
            wanted_default=default_value(wanted['COLUMN_DEFAULT'])
            if col=='tenant_id' and wanted_default==source_tenant:wanted_default=tenant
            found_default=default_value(found['DATA_DEFAULT'] if native else found['COLUMN_DEFAULT'],native)
            if native and wanted['GENERATION_EXPRESSION']:
                if (table,col)!=('rm_user_t','active_login_name'):
                    issue(table,'native-generated-expression-needs-review',col)
                elif generated_expression(wanted['GENERATION_EXPRESSION'])!=generated_expression(found['DATA_DEFAULT']):issue(table,'generated-expression',col)
                found_default=None
            if wanted['COLUMN_TYPE'].startswith(('decimal','int','bigint','tinyint','smallint')):
                if wanted_default is not None and found_default is not None:
                    try:
                        wanted_default,found_default=Decimal(wanted_default),Decimal(found_default)
                    except Exception:
                        pass
            if wanted_default!=found_default:
                if table=='nifi_node_t' and col=='insecure_tls' and str(wanted_default) in {'0','1'} and str(found_default) in {'0','1'}:pass
                elif native and table=='res_logical_model_field' and col=='entity_id' and wanted_default=='' and found_default is None:pass
                else:issue(table,'column-default',col)
            if wanted['COLUMN_COMMENT']!=found['COLUMN_COMMENT']:issue(table,'column-comment',col)
            if native and 'on update' in wanted['EXTRA'].lower() and table not in target['updateTriggerTables']:issue(table,'missing-native-update-trigger',col)
        if source['indexes'][table]!=target['indexes'][table]:
            issue(table,'index-semantics')
        if source['foreignKeys'][table]!=target['foreignKeys'][table]:
            issue(table,'foreign-key-columns')
        if source['primaryKeys'].get(table,())!=target['primaryKeys'].get(table,()):
            issue(table,'primary-key-columns')
    return differences


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config',type=Path,default=Path.home()/'.codex/private/data-elements/resources.local.json')
    args=parser.parse_args()
    config=json.loads(args.config.read_text(encoding='utf-8-sig'))
    sources=[s for s in export.connections(config) if s[0]!='control']
    source_cfg=next(s for s in sources if s[2]==AUTHORITY and s[1]=='mysql')
    source=mysql_metadata(source_cfg[2],source_cfg[3])
    def tenant_id(key):
        values=[str(t) for t,k in config['nacos_tenant_database']['tenant-bindings'].items() if k==key]
        if len(values)!=1:raise ValueError('Industry datasource must have one unambiguous tenant binding')
        return values[0]
    reports=[]
    for key,dialect,schema,options in sources:
        target=source if schema==AUTHORITY else (mysql_metadata if dialect=='mysql' else dameng_metadata)(schema,options)
        differences=compare(source,target,tenant_id(key),tenant_id(source_cfg[0]))
        reports.append({'schema':schema,'dialect':dialect,'tables':len(target['tables']),'differences':differences})
    print(json.dumps({'authority':AUTHORITY,'scope':'industry tables, columns, indexes and foreign-key columns','schemas':reports},ensure_ascii=False,indent=2))
    return 1 if any(r['differences'] for r in reports) else 0


if __name__=='__main__':
    try:
        sys.exit(main())
    except Exception as error:
        print('Schema verification failed ('+type(error).__name__+'); inspect private configuration.',file=sys.stderr)
        sys.exit(2)
