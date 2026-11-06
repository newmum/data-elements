"""Kingbase tenant schema/migration primitives. Credentials remain in private config.

Only the explicitly selected source schema and target database are handled. Data
copy streams bounded JDBC batches; reports contain counts and digests, not rows.
"""
import argparse
import collections
import datetime
from decimal import Decimal
import gzip
import hashlib
import json
from pathlib import Path
import re
import sys

ROOT=Path(__file__).resolve().parents[1]
PRIVATE=Path.home()/'.codex/private/data-elements'

def ident(value):
    if not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*',value):raise ValueError('Unsafe database identifier')
    return '"'+value.lower()+'"'

def literal(value):
    return "'"+str(value).replace("'","''")+"'"

def load_config(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))

def target_connect(config,bootstrap=False):
    import jaydebeapi
    import jpype
    jpype.addClassPath(config['jar'])
    url=config['bootstrapUrl' if bootstrap else 'url']
    for name,value in [('defaultRowFetchSize','500'),('socketTimeout','90'),('tcpKeepAlive','true')]:
        if not re.search(r'[?&]'+name+'=',url):url+=('&' if '?' in url else '?')+name+'='+value
    c=jaydebeapi.connect(config['driver'],url,
                        [config['user'],config['password']],config['jar'])
    c.jconn.setAutoCommit(False)
    return c

def source_connect(config):
    import pymysql
    options=dict(config['control']);options['database']='chengtian'
    return pymysql.connect(**options,cursorclass=pymysql.cursors.DictCursor,connect_timeout=15,read_timeout=180,autocommit=False)

def source_metadata(connection):
    with connection.cursor() as q:
        q.execute("SELECT TABLE_NAME,TABLE_COMMENT FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name")
        tables={r['TABLE_NAME']:{'comment':r['TABLE_COMMENT'],'columns':[],'indexes':[],'foreign':[]} for r in q.fetchall()}
        q.execute('SELECT * FROM information_schema.columns WHERE table_schema=DATABASE() ORDER BY table_name,ordinal_position')
        for r in q.fetchall():tables[r['TABLE_NAME']]['columns'].append(r)
        q.execute('SELECT * FROM information_schema.statistics WHERE table_schema=DATABASE() ORDER BY table_name,index_name,seq_in_index')
        for r in q.fetchall():tables[r['TABLE_NAME']]['indexes'].append(r)
        q.execute('SELECT k.*,r.UPDATE_RULE,r.DELETE_RULE FROM information_schema.key_column_usage k JOIN information_schema.referential_constraints r ON r.constraint_schema=k.constraint_schema AND r.constraint_name=k.constraint_name AND r.table_name=k.table_name WHERE k.table_schema=DATABASE() AND k.referenced_table_name IS NOT NULL ORDER BY k.table_name,k.constraint_name,k.ordinal_position')
        for r in q.fetchall():tables[r['TABLE_NAME']]['foreign'].append(r)
    return tables

def column_type(column):
    kind=column['DATA_TYPE'].lower();raw=column['COLUMN_TYPE'].lower()
    if kind in {'varchar','char'}:return ('varchar' if kind=='varchar' else 'char')+'('+str(column['CHARACTER_MAXIMUM_LENGTH'])+')'
    if kind in {'text','mediumtext','longtext'}:return 'text'
    if kind=='json':return 'json'
    if kind in {'blob','longblob','mediumblob','binary','varbinary'}:return 'bytea'
    if kind in {'datetime','timestamp'}:return 'timestamp('+str(column['DATETIME_PRECISION'] or 0)+') without time zone'
    if kind=='date':return 'date'
    if kind=='time':return 'time('+str(column['DATETIME_PRECISION'] or 0)+')'
    if kind=='tinyint':return 'smallint'
    if kind=='smallint':return 'integer' if 'unsigned' in raw else 'smallint'
    if kind in {'mediumint','int'}:return 'bigint' if 'unsigned' in raw else 'integer'
    if kind=='bigint':return 'numeric(20,0)' if 'unsigned' in raw else 'bigint'
    if kind in {'decimal','numeric'}:return 'numeric('+str(column['NUMERIC_PRECISION'])+','+str(column['NUMERIC_SCALE'])+')'
    if kind=='float':return 'real'
    if kind=='double':return 'double precision'
    raise ValueError('Unsupported source type '+raw)

def default_sql(column):
    value=column['COLUMN_DEFAULT']
    if value is None:return ''
    if re.fullmatch(r'CURRENT_TIMESTAMP(?:\(\d*\))?',str(value),re.I):return ' DEFAULT '+str(value)
    if re.fullmatch(r'\(?utc_timestamp\(\d*\)\)?',str(value),re.I):return " DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')"
    if column['DATA_TYPE'] in {'int','bigint','smallint','tinyint','decimal','float','double'}:return ' DEFAULT '+str(Decimal(value))
    return ' DEFAULT '+literal(value)

def generated_sql(expression):
    expression=expression.replace("\\'","'")
    expression=re.sub(r'_utf8mb4(?=\')','',expression,flags=re.I)
    expression=re.sub(r'`([A-Za-z_][A-Za-z0-9_]*)`',r'"\1"',expression)
    expression=re.sub(r'\b(json_extract|json_unquote)\s*\(',lambda m:'public.'+m[1].lower()+'(',expression,flags=re.I)
    return expression

def compatibility_sql():
    statements=[
    """CREATE OR REPLACE FUNCTION public.json_valid(value text) RETURNS integer LANGUAGE plpgsql IMMUTABLE AS $body$
    BEGIN IF value IS NULL THEN RETURN NULL; END IF; PERFORM value::jsonb; RETURN 1;
    EXCEPTION WHEN others THEN RETURN 0; END $body$""",
    """CREATE OR REPLACE FUNCTION public.json_extract(value text,path text) RETURNS text LANGUAGE plpgsql IMMUTABLE AS $body$
    DECLARE parts text[]; item jsonb; token text;
    BEGIN IF value IS NULL OR path IS NULL THEN RETURN NULL; END IF;
      item:=value::jsonb;
      IF path='$' THEN RETURN item::text; END IF;
      IF path !~ '^\\$\\.' THEN RAISE EXCEPTION 'Unsupported JSON path'; END IF;
      parts:=string_to_array(substring(path from 3),'.');
      FOREACH token IN ARRAY parts LOOP item:=item->token; END LOOP;
      RETURN item::text;
    END $body$""",
    """CREATE OR REPLACE FUNCTION public.json_unquote(value text) RETURNS text LANGUAGE plpgsql IMMUTABLE AS $body$
    DECLARE item jsonb;
    BEGIN IF value IS NULL THEN RETURN NULL; END IF;
      BEGIN item:=value::jsonb; EXCEPTION WHEN others THEN RETURN value; END;
      IF jsonb_typeof(item)='string' THEN RETURN item #>> '{}'; END IF;
      RETURN value;
    END $body$""",
    """CREATE OR REPLACE FUNCTION public.json_length(value text) RETURNS integer LANGUAGE sql IMMUTABLE AS $body$
    SELECT CASE WHEN value IS NULL THEN NULL WHEN jsonb_typeof(value::jsonb)='array' THEN jsonb_array_length(value::jsonb)
      WHEN jsonb_typeof(value::jsonb)='object' THEN (SELECT count(*)::integer FROM jsonb_object_keys(value::jsonb)) ELSE 1 END $body$""",
    """CREATE OR REPLACE FUNCTION public.json_contains(value text,candidate text) RETURNS boolean LANGUAGE sql IMMUTABLE AS $body$
    SELECT value::jsonb @> candidate::jsonb $body$""",
    """CREATE OR REPLACE FUNCTION public.json_overlaps(value text,candidate text) RETURNS boolean LANGUAGE sql IMMUTABLE AS $body$
    SELECT CASE WHEN jsonb_typeof(value::jsonb)='array' AND jsonb_typeof(candidate::jsonb)='array' THEN
      EXISTS(SELECT 1 FROM jsonb_array_elements(value::jsonb) a JOIN jsonb_array_elements(candidate::jsonb) b ON a.value=b.value)
      WHEN jsonb_typeof(value::jsonb)='object' AND jsonb_typeof(candidate::jsonb)='object' THEN
      EXISTS(SELECT 1 FROM jsonb_each(value::jsonb) a JOIN jsonb_each(candidate::jsonb) b ON a.key=b.key AND a.value=b.value)
      ELSE value::jsonb @> candidate::jsonb OR candidate::jsonb @> value::jsonb END $body$""",
    """CREATE OR REPLACE FUNCTION public.json_quote(value text) RETURNS text LANGUAGE sql IMMUTABLE AS $body$
    SELECT to_json(value)::text $body$""",
    """CREATE OR REPLACE FUNCTION public.json_number(value text) RETURNS numeric LANGUAGE plpgsql IMMUTABLE AS $body$
    BEGIN RETURN value::numeric; EXCEPTION WHEN others THEN RETURN NULL; END $body$""",
    """CREATE OR REPLACE FUNCTION public.json_table_elements(document text,path text) RETURNS SETOF jsonb LANGUAGE plpgsql IMMUTABLE AS $body$
    DECLARE value jsonb; root text;
    BEGIN IF document IS NULL THEN RETURN; END IF;
      IF right(path,3)<>'[*]' THEN RAISE EXCEPTION 'Unsupported JSON_TABLE row path'; END IF;
      root:=left(path,length(path)-3);
      value:=CASE WHEN root='$' THEN document::jsonb ELSE public.json_extract(document,root)::jsonb END;
      IF jsonb_typeof(value)='array' THEN RETURN QUERY SELECT jsonb_array_elements(value); END IF;
      RETURN;
    END $body$""",
    ]
    for name,args,returns in [('json_valid','value','integer'),('json_extract','value,path','text'),('json_unquote','value','text'),('json_length','value','integer')]:
        for kind in ['json','jsonb']:
            params='value '+kind+(',path text' if name=='json_extract' else '')
            call='value::text'+(',path' if name=='json_extract' else '')
            statements.append('CREATE OR REPLACE FUNCTION public.'+name+'('+params+') RETURNS '+returns+' LANGUAGE sql IMMUTABLE AS $body$ SELECT public.'+name+'('+call+') $body$')
    for name in ['json_contains','json_overlaps']:
        for left,right in [('text','json'),('text','jsonb'),('json','text'),('jsonb','text'),('json','json'),('jsonb','jsonb')]:
            statements.append('CREATE OR REPLACE FUNCTION public.'+name+'(value '+left+',candidate '+right+') RETURNS boolean LANGUAGE sql IMMUTABLE AS $body$ SELECT public.'+name+'(value::text,candidate::text) $body$')
    return statements

def schema_statements(tables):
    before=compatibility_sql();after=[];foreign_after=[]
    for table,info in tables.items():
        definitions=[]
        for column in info['columns']:
            expr=column['GENERATION_EXPRESSION']
            line=ident(column['COLUMN_NAME'])+' '+column_type(column)
            if expr:line+=' GENERATED ALWAYS AS ('+generated_sql(expr)+') STORED'
            else:line+=default_sql(column)
            if column['IS_NULLABLE']=='NO':line+=' NOT NULL'
            definitions.append(line)
            if 'unsigned' in column['COLUMN_TYPE'].lower():
                maximum=str((1<<{'tinyint':8,'smallint':16,'mediumint':24,'int':32,'bigint':64}[column['DATA_TYPE']])-1)
                definitions.append('CHECK ('+ident(column['COLUMN_NAME'])+' BETWEEN 0 AND '+maximum+')')
        groups=collections.defaultdict(list)
        for index in info['indexes']:groups[index['INDEX_NAME']].append(index)
        for name,index in groups.items():
            parts=[]
            for col in index:
                value=ident(col['COLUMN_NAME'])
                if col['SUB_PART']:value='left('+value+','+str(col['SUB_PART'])+')'
                if col.get('COLLATION')=='D':value+=' DESC'
                parts.append(value)
            if name=='PRIMARY':definitions.append('PRIMARY KEY ('+','.join(parts)+')')
            else:
                native_name='ix_'+hashlib.sha256((table+':'+name).encode()).hexdigest()[:24]
                after.append('CREATE '+('UNIQUE ' if index[0]['NON_UNIQUE']==0 else '')+'INDEX IF NOT EXISTS '+ident(native_name)+' ON public.'+ident(table)+' ('+','.join(parts)+')')
        before.append('CREATE TABLE public.'+ident(table)+' (\n  '+',\n  '.join(definitions)+'\n)')
        after.append('COMMENT ON TABLE public.'+ident(table)+' IS '+literal(info['comment']))
        for col in info['columns']:after.append('COMMENT ON COLUMN public.'+ident(table)+'.'+ident(col['COLUMN_NAME'])+' IS '+literal(col['COLUMN_COMMENT']))
        updates=[col['COLUMN_NAME'] for col in info['columns'] if 'on update' in col['EXTRA'].lower()]
        if updates:
            fname='touch_'+table
            assignments=' '.join('IF NEW.'+ident(col)+' IS NOT DISTINCT FROM OLD.'+ident(col)+' THEN NEW.'+ident(col)+':=CURRENT_TIMESTAMP; END IF;' for col in updates)
            after.append('CREATE OR REPLACE FUNCTION public.'+ident(fname)+'() RETURNS trigger LANGUAGE plpgsql AS $body$ BEGIN IF NEW IS DISTINCT FROM OLD THEN '+assignments+' END IF; RETURN NEW; END $body$')
            after.append('CREATE TRIGGER '+ident(fname)+' BEFORE UPDATE ON public.'+ident(table)+' FOR EACH ROW EXECUTE FUNCTION public.'+ident(fname)+'()')
        foreign=collections.defaultdict(list)
        for row in info['foreign']:foreign[row['CONSTRAINT_NAME']].append(row)
        for name,rows in foreign.items():
            parent=rows[0]['REFERENCED_TABLE_NAME'];reference=[r['REFERENCED_COLUMN_NAME'] for r in rows]
            grouped=collections.defaultdict(list)
            for idx in tables[parent]['indexes']:grouped[idx['INDEX_NAME']].append(idx)
            unique_names=[key for key,values in grouped.items() if not values[0]['NON_UNIQUE'] and [v['COLUMN_NAME'] for v in values]==reference]
            unique=bool(unique_names)
            if not unique:
                primary={v['COLUMN_NAME'] for v in grouped.get('PRIMARY',[])}
                if not primary or not primary.issubset(reference):raise ValueError('Referenced nonunique key needs explicit semantic review')
                support='fk_support_'+hashlib.sha256((parent+':'+','.join(reference)).encode()).hexdigest()[:20]
                foreign_after.append('CREATE UNIQUE INDEX IF NOT EXISTS '+ident(support)+' ON public.'+ident(parent)+' ('+','.join(map(ident,reference))+')')
                foreign_after.append('ALTER TABLE public.'+ident(parent)+' ADD CONSTRAINT '+ident(support)+' UNIQUE USING INDEX '+ident(support))
            elif 'PRIMARY' not in unique_names:
                native_name='ix_'+hashlib.sha256((parent+':'+unique_names[0]).encode()).hexdigest()[:24]
                foreign_after.append('ALTER TABLE public.'+ident(parent)+' ADD CONSTRAINT '+ident(native_name)+' UNIQUE USING INDEX '+ident(native_name))
            foreign_after.append('ALTER TABLE public.'+ident(table)+' ADD CONSTRAINT '+ident(name)+' FOREIGN KEY ('+','.join(ident(r['COLUMN_NAME']) for r in rows)+') REFERENCES public.'+ident(parent)+' ('+','.join(ident(r['REFERENCED_COLUMN_NAME']) for r in rows)+') ON UPDATE '+rows[0]['UPDATE_RULE']+' ON DELETE '+rows[0]['DELETE_RULE'])
    return before,after+foreign_after

def run_statements(connection,statements):
    q=connection.cursor()
    try:
        for position,statement in enumerate(statements):
            try:q.execute(statement)
            except Exception as error:
                diagnostic=PRIVATE/'kingbase-chengtian/statement-error.private.txt'
                diagnostic.write_text('Statement '+str(position)+'\n'+statement+'\n'+str(error),encoding='utf-8')
                raise
    finally:q.close()

def query_rows(connection,sql,args=None):
    q=connection.cursor()
    try:
        q.execute(sql,args or [])
        names=[str(item[0]).lower() for item in q.description]
        return [dict(zip(names,row)) for row in q.fetchall()]
    finally:q.close()

def native_metadata(connection,schema='public'):
    tables=query_rows(connection,"SELECT c.relname AS table_name,obj_description(c.oid,'pg_class') AS comment FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname=? AND c.relkind='r' ORDER BY c.relname",[schema])
    columns=query_rows(connection,"""SELECT c.relname AS table_name,a.attname AS column_name,
       format_type(a.atttypid,a.atttypmod) AS native_type,a.attnotnull AS not_null,
       pg_get_expr(d.adbin,d.adrelid) AS expression,a.attgenerated AS generated,
       col_description(c.oid,a.attnum) AS comment,a.attnum AS position
       FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
       JOIN pg_attribute a ON a.attrelid=c.oid LEFT JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum
       WHERE n.nspname=? AND c.relkind='r' AND a.attnum>0 AND NOT a.attisdropped ORDER BY c.relname,a.attnum""",[schema])
    constraints=query_rows(connection,"""SELECT t.relname AS table_name,c.conname AS name,c.contype AS kind,
       pg_get_constraintdef(c.oid,true) AS definition,ref.relname AS referenced_table,
       (SELECT json_agg(a.attname ORDER BY k.position)::text FROM unnest(c.conkey) WITH ORDINALITY k(number,position)
         JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=k.number) AS columns,
       (SELECT json_agg(a.attname ORDER BY k.position)::text FROM unnest(c.confkey) WITH ORDINALITY k(number,position)
         JOIN pg_attribute a ON a.attrelid=ref.oid AND a.attnum=k.number) AS referenced_columns
       FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
       LEFT JOIN pg_class ref ON ref.oid=c.confrelid WHERE n.nspname=? ORDER BY t.relname,c.conname""",[schema])
    indexes=query_rows(connection,"""SELECT t.relname AS table_name,c.relname AS name,i.indisunique AS unique_key,
       i.indisprimary AS primary_key,pg_get_indexdef(i.indexrelid) AS definition,
       (SELECT json_agg(pg_get_indexdef(i.indexrelid,k.position,true) ORDER BY k.position)::text
        FROM generate_series(1,i.indnkeyatts) k(position)) AS parts,
       EXISTS(SELECT 1 FROM pg_constraint p WHERE p.conindid=i.indexrelid AND p.contype IN ('p','u')) AS constraint_index
       FROM pg_index i JOIN pg_class t ON t.oid=i.indrelid JOIN pg_class c ON c.oid=i.indexrelid
       JOIN pg_namespace n ON n.oid=t.relnamespace WHERE n.nspname=? ORDER BY t.relname,c.relname""",[schema])
    functions=query_rows(connection,"""SELECT p.oid,p.proname AS name,pg_get_functiondef(p.oid) AS definition
       FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
       WHERE n.nspname=? AND p.prokind IN ('f','p') AND NOT EXISTS
         (SELECT 1 FROM pg_depend d WHERE d.objid=p.oid AND d.classid='pg_proc'::regclass AND d.deptype='e')
       ORDER BY p.oid""",[schema])
    triggers=query_rows(connection,"""SELECT c.relname AS table_name,t.tgname AS name,pg_get_triggerdef(t.oid,true) AS definition,
       t.tgenabled AS enabled,pg_get_functiondef(t.tgfoid) AS function_definition
       FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace
       WHERE n.nspname=? AND NOT t.tgisinternal ORDER BY c.relname,t.tgname""",[schema])
    views=query_rows(connection,"""SELECT v.viewname AS name,v.definition FROM pg_views v
       JOIN pg_namespace n ON n.nspname=v.schemaname JOIN pg_class c ON c.relnamespace=n.oid AND c.relname=v.viewname
       WHERE v.schemaname=? AND NOT EXISTS(SELECT 1 FROM pg_depend d WHERE d.objid=c.oid
         AND d.classid='pg_class'::regclass AND d.deptype='e') ORDER BY v.viewname""",[schema])
    return {'tables':tables,'columns':columns,'constraints':constraints,'indexes':indexes,'functions':functions,'triggers':triggers,'views':views}

def normalized_options(options):
    result=dict(options)
    result.setdefault('driver',result.get('driver-class-name','com.kingbase8.Driver'))
    result.setdefault('user',result.get('username'))
    result.setdefault('jar',str(Path.home()/'.m2/repository/cn/com/kingbase/kingbase8/8.6.1/kingbase8-8.6.1.jar'))
    return result

def native_export(database,options):
    cfg=normalized_options(options);schema=cfg.get('schema','public');c=target_connect(cfg)
    try:meta=native_metadata(c,schema)
    finally:c.close()
    lines=['-- 澄天租户-人大金仓完整原生结构；仅用于新空库，不含业务数据或凭据。',
           '-- 数据库 '+database+'；Schema '+schema+'；数据库须以 UTF8 创建。',
           'SET search_path TO '+ident(schema)+';', 'SET ora_input_emptystr_isnull=off;']
    def emit(sql):lines.extend(['',str(sql).rstrip(';')+';'])
    for function in meta['functions']:emit(function['definition'])
    columns=collections.defaultdict(list);constraints=collections.defaultdict(list)
    for col in meta['columns']:columns[col['table_name']].append(col)
    for constraint in meta['constraints']:
        if constraint['kind']!='f':constraints[constraint['table_name']].append(constraint)
    for table in meta['tables']:
        name=table['table_name'];parts=[]
        for col in columns[name]:
            value=ident(col['column_name'])+' '+str(col['native_type'])
            if col['expression'] is not None:
                value+=(' GENERATED ALWAYS AS ('+str(col['expression'])+') STORED') if col['generated'] else (' DEFAULT '+str(col['expression']))
            if col['not_null']:value+=' NOT NULL'
            parts.append(value)
        parts.extend('CONSTRAINT '+ident(x['name'])+' '+str(x['definition']) for x in constraints[name])
        emit('CREATE TABLE '+ident(schema)+'.'+ident(name)+' (\n  '+',\n  '.join(parts)+'\n)')
        emit('COMMENT ON TABLE '+ident(schema)+'.'+ident(name)+' IS '+literal(table['comment'] or ''))
        for col in columns[name]:emit('COMMENT ON COLUMN '+ident(schema)+'.'+ident(name)+'.'+ident(col['column_name'])+' IS '+literal(col['comment'] or ''))
    for index in meta['indexes']:
        if not index['constraint_index']:emit(index['definition'])
    for constraint in meta['constraints']:
        if constraint['kind']=='f':emit('ALTER TABLE '+ident(schema)+'.'+ident(constraint['table_name'])+' ADD CONSTRAINT '+ident(constraint['name'])+' '+str(constraint['definition']))
    for view in meta['views']:emit('CREATE VIEW '+ident(schema)+'.'+ident(view['name'])+' AS '+str(view['definition']))
    for trigger in meta['triggers']:emit(trigger['definition'])
    counts={'tables':len(meta['tables']),'views':len(meta['views']),'indexes':len(meta['indexes']),
            'foreignKeys':sum(x['kind']=='f' for x in meta['constraints']),'triggers':len(meta['triggers']),
            'functions':len(meta['functions']),'procedures':0,'events':0}
    return database,'\n'.join(lines)+'\n',counts,{'definitions':'Kingbase native catalogs and pg_get_*def','sqlSchema':schema,'database':database}

def canonical_value(value,column):
    if value is None:return None
    kind=column['DATA_TYPE'].lower();text=str(value)
    if kind in {'int','bigint','smallint','tinyint','decimal','numeric','float','double'}:
        number=Decimal(text)
        return format(number.normalize(),'f') if number else '0'
    if kind in {'datetime','timestamp'}:
        return datetime.datetime.fromisoformat(text.replace(' ','T')).isoformat(timespec='microseconds')
    if kind=='date':return datetime.date.fromisoformat(text).isoformat()
    if kind=='json':return json.dumps(json.loads(text),ensure_ascii=False,sort_keys=True,separators=(',',':'))
    if kind=='char':return text.rstrip(' ')
    return text

def data_digest(rows,columns,text_fingerprints=False):
    total=0;xor=0;count=0
    for row in rows:
        values=[]
        for value,col in zip(row,columns):
            if text_fingerprints and col.get('DIGEST_TEXT'):
                values.append(None if value is None else str(value))
            else:
                normalized=canonical_value(value,col)
                if col.get('DIGEST_TEXT') and normalized is not None:
                    normalized=hashlib.sha256(normalized.encode('utf-8')).hexdigest()
                values.append(normalized)
        encoded=json.dumps(values,ensure_ascii=False,separators=(',',':')).encode('utf-8')
        hashed=int.from_bytes(hashlib.sha256(encoded).digest(),'big')
        total=(total+hashed)%(1<<256);xor^=hashed;count+=1
    return {'rows':count,'sumSha256':f'{total:064x}','xorSha256':f'{xor:064x}'}

def verify_copy(source,config,snapshot=None):
    tables=source_metadata(source);report=[]
    for table,info in tables.items():
            columns=[{**col,'DIGEST_TEXT':col['DATA_TYPE'].lower() in {'text','mediumtext','longtext'} or (col.get('CHARACTER_MAXIMUM_LENGTH') or 0)>4096} for col in info['columns'] if not col['GENERATION_EXPRESSION']]
            backup=(snapshot or PRIVATE/'kingbase-chengtian/source-data')/(table+'.jsonl.gz')
            with gzip.open(backup,'rt',encoding='utf-8') as f:
                expected=data_digest((json.loads(line) for line in f),columns)
            c=None
            try:
                projections=[]
                for col in columns:
                    value='CAST('+ident(col['COLUMN_NAME'])+' AS text)'
                    if col['DATA_TYPE'].lower()=='char':value='rtrim('+value+",' ')"
                    if col['DIGEST_TEXT']:value="encode(sha256(convert_to("+value+",'UTF8')),'hex')"
                    projections.append(value)
                names=[col['COLUMN_NAME'] for col in columns]
                primary=[x['COLUMN_NAME'] for x in info['indexes'] if x['INDEX_NAME']=='PRIMARY']
                positions=[names.index(name) for name in primary]
                if not primary or any(columns[index]['DIGEST_TEXT'] for index in positions):raise ValueError('Verification requires a normal primary key')
                def stream():
                    nonlocal c
                    last=None
                    while True:
                        where='' if last is None else ' WHERE ('+','.join(map(ident,primary))+')>('+','.join('?' for _ in primary)+')'
                        sql='SELECT '+','.join(projections)+' FROM public.'+ident(table)+where+' ORDER BY '+','.join(map(ident,primary))+' LIMIT 500'
                        for attempt in range(3):
                            q=None
                            try:
                                if c is None:
                                    c=target_connect(config);c.jconn.setAutoCommit(True)
                                q=c.cursor();q.execute(sql,last or []);rows=q.fetchall();break
                            except Exception:
                                if c is not None:
                                    try:c.close()
                                    except Exception:pass
                                c=None
                                if attempt==2:raise
                            finally:
                                if q is not None:
                                    try:q.close()
                                    except Exception:pass
                        if not rows:break
                        yield from rows
                        last=[rows[-1][index] for index in positions]
                actual=data_digest(stream(),columns,text_fingerprints=True)
            finally:
                if c is not None:c.close()
            report.append({'table':table,'matches':expected==actual,'sourceSnapshot':expected,'target':actual})
            print(json.dumps({'verified':table,'rows':actual['rows'],'matches':expected==actual}),flush=True)
    out=ROOT/'logs/kingbase-tenant-migration/data-verification.json'
    out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    if any(not r['matches'] for r in report):raise ValueError('Data verification differs; see counts/digests report')
    return {'verifiedTables':len(report),'verifiedRows':sum(r['target']['rows'] for r in report),'allContentDigestsMatch':True,'textComparison':'native SHA256 of UTF8 content; no large-text transfer'}


def dependency_order(tables):
    """Parents first for inserts, reverse order for deletions; reject FK cycles."""
    result=[];pending=set(tables)
    while pending:
        ready=sorted(t for t in pending if not {
            r['REFERENCED_TABLE_NAME'] for r in tables[t]['foreign']
            if r['REFERENCED_TABLE_NAME']!=t
        }.intersection(pending))
        if not ready:raise ValueError('Foreign key cycle requires a reviewed migration order')
        result.extend(ready);pending.difference_update(ready)
    return result


def row_identity(row,columns,primary):
    values=[canonical_value(value,col) for value,col in zip(row,columns)]
    key=tuple(values[index] for index in primary)
    digest=hashlib.sha256(json.dumps(values,ensure_ascii=False,separators=(',',':')).encode()).digest()
    return key,digest


def reconcile_staging(source,config,snapshot):
    """Converge the unpublished target to a final source snapshot, by primary key.

    Both local backends must be stopped. The source remains read-only. Target
    changes (including removal of validation audit rows) commit as one transaction.
    """
    import socket
    import pymysql
    for port in [8088,18089]:
        with socket.socket() as probe:
            if probe.connect_ex(('127.0.0.1',port))==0:
                raise ValueError('Stop both local backends before final staging reconciliation')
    snapshot=snapshot.resolve()
    if not snapshot.is_relative_to(PRIVATE.resolve()):raise ValueError('Recovery data must remain in the private directory')
    if snapshot.exists():raise ValueError('Use a fresh private snapshot directory; recovery files are never overwritten')
    tables=source_metadata(source);order=dependency_order(tables)
    snapshot.mkdir(parents=True)
    c=target_connect(config);q=c.cursor();report=[];removed={}
    try:
        q.execute("SELECT current_database(),current_setting('server_encoding'),current_setting('ora_input_emptystr_isnull')")
        if tuple(map(str,q.fetchone()))!=('baseline_ct','UTF8','off'):raise ValueError('Target identity differs')
        with source.cursor() as start:start.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT')
        triggers=native_metadata(c)['triggers']
        for t in triggers:q.execute('ALTER TABLE public.'+ident(t['table_name'])+' DISABLE TRIGGER '+ident(t['name']))
        for table in order:
            info=tables[table];columns=[x for x in info['columns'] if not x['GENERATION_EXPRESSION']]
            names=[x['COLUMN_NAME'] for x in columns]
            primary=[x['COLUMN_NAME'] for x in info['indexes'] if x['INDEX_NAME']=='PRIMARY']
            if not primary:raise ValueError('Final reconciliation requires a primary key: '+table)
            positions=[names.index(name) for name in primary]
            q.execute('SELECT '+','.join('CAST('+ident(n)+' AS text)' for n in names)+' FROM public.'+ident(table))
            existing={}
            while True:
                rows=q.fetchmany(500)
                if not rows:break
                for row in rows:
                    key,digest=row_identity(row,columns,positions);existing[key]=digest
            changed=0;total=0
            updates=[n for n in names if n not in primary]
            sql='INSERT INTO public.'+ident(table)+' ('+','.join(map(ident,names))+') VALUES ('+','.join('?' for _ in names)+') ON CONFLICT ('+','.join(map(ident,primary))+') '
            sql+=('DO UPDATE SET '+','.join(ident(n)+'=EXCLUDED.'+ident(n) for n in updates)) if updates else 'DO NOTHING'
            with source.cursor(pymysql.cursors.SSCursor) as stream,gzip.open(snapshot/(table+'.jsonl.gz'),'wt',encoding='utf-8') as recovery:
                stream.execute('SELECT '+','.join('`'+n+'`' for n in names)+' FROM `'+table+'`')
                while True:
                    rows=stream.fetchmany(500)
                    if not rows:break
                    batch=[]
                    for row in rows:
                        values=[str(v) if isinstance(v,(Decimal,datetime.date,datetime.datetime,datetime.time)) else v for v in row]
                        recovery.write(json.dumps(values,ensure_ascii=False,separators=(',',':'),default=str)+'\n')
                        key,digest=row_identity(values,columns,positions)
                        if existing.pop(key,None)!=digest:batch.append(values)
                        total+=1
                    if batch:q.executemany(sql,batch);changed+=len(batch)
            removed[table]=(primary,list(existing))
            report.append({'table':table,'rows':total,'upserted':changed,'removedFromUnpublishedTarget':len(existing)})
            print(json.dumps(report[-1]),flush=True)
        for table in reversed(order):
            primary,keys=removed[table]
            sql='DELETE FROM public.'+ident(table)+' WHERE '+' AND '.join(ident(n)+'=?' for n in primary)
            for start in range(0,len(keys),500):q.executemany(sql,keys[start:start+500])
        for t in triggers:q.execute('ALTER TABLE public.'+ident(t['table_name'])+' ENABLE TRIGGER '+ident(t['name']))
        c.commit()
        (snapshot/'committed.private.json').write_text(json.dumps({'committed':True,'tables':len(report)}),encoding='utf-8')
    except Exception:c.rollback();raise
    finally:q.close();c.close();source.rollback()
    output=ROOT/'logs/kingbase-tenant-migration/final-reconciliation.json'
    output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    return {'tables':len(report),'rows':sum(r['rows'] for r in report),'upserted':sum(r['upserted'] for r in report),'removedValidationRows':sum(r['removedFromUnpublishedTarget'] for r in report)}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('command',choices=['plan','create','copy','finish','verify','reconcile-staging'])
    p.add_argument('--config',type=Path,default=PRIVATE/'kingbase-chengtian/target.private.json')
    p.add_argument('--source-config',type=Path,default=PRIVATE/'resources.local.json')
    p.add_argument('--apply',action='store_true')
    p.add_argument('--snapshot',type=Path,help='Private recovery snapshot directory (required fresh path for reconcile-staging)')
    args=p.parse_args();target=load_config(args.config)
    if target['database']!='baseline_ct' or target['schema']!='public':raise ValueError('Explicit target must be baseline_ct.public')
    source=source_connect(load_config(args.source_config));metadata=source_metadata(source)
    before,after=schema_statements(metadata)
    output=ROOT/'logs/kingbase-tenant-migration';output.mkdir(parents=True,exist_ok=True)
    canonical=ROOT/'db/baseline_ct/init.sql';canonical.parent.mkdir(parents=True,exist_ok=True)
    if args.command=='verify':
        print(json.dumps(verify_copy(source,target,args.snapshot)));source.close();return
    if args.command=='reconcile-staging':
        if not args.apply or not args.snapshot:raise ValueError('Final staging reconciliation requires --apply and a fresh --snapshot directory')
        print(json.dumps(reconcile_staging(source,target,args.snapshot)));source.close();return
    if args.command=='plan':
        canonical.write_text('-- 澄天租户-人大金仓完整空库结构；业务数据库 baseline_ct，模式 public，编码 UTF8。\n-- 运行连接设置 ora_input_emptystr_isnull=off；不在已有库执行完整初始化。\n'+ ';\n\n'.join(before+after)+';\n',encoding='utf-8',newline='\n')
        print(json.dumps({'tables':len(metadata),'statements':len(before+after),'schemaFile':str(canonical),'noDatabaseWrites':True}));return
    if not args.apply:raise ValueError('Database changes require --apply')
    if args.command=='create':
        c=target_connect(target,True);c.jconn.setAutoCommit(True);q=c.cursor()
        q.execute('SELECT datname FROM pg_database WHERE datname=?',[target['database']])
        if q.fetchone():raise ValueError('Target database already exists; refusing automatic recreation')
        q.execute('CREATE DATABASE '+ident(target['database'])+" ENCODING 'UTF8' TEMPLATE template0");q.close();c.close()
        c=target_connect(target)
        try:run_statements(c,before);c.commit()
        except Exception:c.rollback();raise
        finally:c.close()
        print(json.dumps({'databaseCreated':target['database'],'tablesCreated':len(metadata)}))
    elif args.command=='copy':
        import pymysql
        c=target_connect(target);q=c.cursor()
        q.execute("SELECT current_database(),current_setting('server_encoding'),current_setting('ora_input_emptystr_isnull')")
        identity=q.fetchone()
        if tuple(map(str,identity))!=('baseline_ct','UTF8','off'):raise ValueError('Target identity, encoding or empty-string mode differs')
        backup=PRIVATE/'kingbase-chengtian/source-data';backup.mkdir(parents=True,exist_ok=True)
        with source.cursor() as start:start.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT')
        report=[]
        for table,info in metadata.items():
            columns=[col for col in info['columns'] if not col['GENERATION_EXPRESSION']]
            names=[col['COLUMN_NAME'] for col in columns]
            q.execute('SELECT count(*) FROM public.'+ident(table))
            if q.fetchone()[0]!=0:raise ValueError('Target table is not empty; refusing overwrite: '+table)
            statement='INSERT INTO public.'+ident(table)+' ('+','.join(ident(n) for n in names)+') VALUES ('+','.join('?' for _ in names)+')'
            primary=[r['COLUMN_NAME'] for r in info['indexes'] if r['INDEX_NAME']=='PRIMARY']
            order=' ORDER BY '+','.join('`'+n+'`' for n in primary) if primary else ''
            count=0;digest=hashlib.sha256()
            with source.cursor(pymysql.cursors.SSCursor) as stream,gzip.open(backup/(table+'.jsonl.gz'),'wt',encoding='utf-8') as recovery:
                stream.execute('SELECT '+','.join('`'+n+'`' for n in names)+' FROM `'+table+'`'+order)
                while True:
                    rows=stream.fetchmany(500)
                    if not rows:break
                    batch=[]
                    for row in rows:
                        values=[str(v) if isinstance(v,(Decimal,datetime.date,datetime.datetime,datetime.time)) else v for v in row]
                        encoded=json.dumps(values,ensure_ascii=False,separators=(',',':'),default=str)+'\n'
                        recovery.write(encoded);digest.update(encoded.encode());batch.append(values)
                    q.executemany(statement,batch);count+=len(batch)
            report.append({'table':table,'rows':count,'sourceDigest':digest.hexdigest()})
            (output/'copy-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
            print(json.dumps({'copied':table,'rows':count}),flush=True)
        c.commit()
        (output/'copy-committed.json').write_text(json.dumps({'tables':len(report),'rows':sum(r['rows'] for r in report),'committed':True})+'\n',encoding='utf-8')
        q.close();c.close();source.rollback()
    elif args.command=='finish':
        c=target_connect(target)
        try:run_statements(c,after);c.commit()
        except Exception:c.rollback();raise
        finally:c.close()
        print(json.dumps({'indexesCommentsTriggersAndConstraintsApplied':True}))
    source.close()

if __name__=='__main__':
    try:main()
    except Exception as error:
        import traceback
        private_error=PRIVATE/'kingbase-chengtian/last-error.private.txt'
        private_error.parent.mkdir(parents=True,exist_ok=True)
        private_error.write_text(traceback.format_exc(),encoding='utf-8')
        print('Kingbase operation failed: '+type(error).__name__+'. Restricted diagnostic saved outside repository.',file=sys.stderr)
        raise SystemExit(1)
