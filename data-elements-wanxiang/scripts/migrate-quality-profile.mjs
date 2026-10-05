/** Additive migration in the actual configured tenant schemas. Secrets stay in memory/env.
 * Only existing quality tables are touched; no task, result, business row or IAM update. */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {mkdtemp,writeFile,rm,readdir} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
const require=createRequire(import.meta.url),exec=promisify(execFile);
const packageRoot=new URL('../../data-elements-front/node_modules/.pnpm/',import.meta.url),yamlPackage=(await readdir(packageRoot)).find(n=>n.startsWith('js-yaml@'));
assert(yamlPackage,'Use the existing frontend YAML dependency');
const yaml=require(new URL(yamlPackage+'/node_modules/js-yaml/index.js',packageRoot).pathname.replace(/^\/([A-Za-z]:)/,'$1'));
const source=`import java.sql.*;import java.util.*;import java.nio.file.*;
public class WxQualitySchema {
 public static void main(String[] args)throws Exception {
  String url=System.getenv("WX_SCHEMA_URL"),schema=System.getenv("WX_SCHEMA_NAME"),mode=args[0];
  if(!url.startsWith("jdbc:mysql:")&&!url.startsWith("jdbc:dm:"))throw new IllegalArgumentException("Unreviewed tenant database");
  try(Connection c=DriverManager.getConnection(url,System.getenv("WX_SCHEMA_USER"),System.getenv("WX_SCHEMA_PASSWORD"));Statement s=c.createStatement()){
   s.setQueryTimeout(20);if(schema!=null&&!schema.isBlank()){if(!schema.matches("[A-Za-z0-9_]+"))throw new IllegalArgumentException("Invalid schema");if(url.startsWith("jdbc:dm:"))s.execute("SET SCHEMA "+schema);}
   Map<String,Set<String>> columns=new LinkedHashMap<>();Map<String,Long> counts=new LinkedHashMap<>();StringBuilder backup=new StringBuilder();
   for(String table:List.of("dq_quality_metric_t","dq_quality_run_t")){
    try(ResultSet r=s.executeQuery("SELECT * FROM "+table+" WHERE 1=0")){Set<String> names=new HashSet<>();ResultSetMetaData m=r.getMetaData();for(int i=1;i<=m.getColumnCount();i++){names.add(m.getColumnName(i).toLowerCase());backup.append(table+"."+m.getColumnName(i)+":"+m.getColumnTypeName(i)+"("+m.getPrecision(i)+")\\n");}columns.put(table,names);}catch(SQLException e){if(e.getMessage().toLowerCase().matches(".*(doesn't exist|does not exist|无效的表|不存在).*")){System.out.println("NO_QUALITY_TABLES");return;}throw e;}
    try(ResultSet r=s.executeQuery("SELECT COUNT(*) FROM "+table)){r.next();counts.put(table,r.getLong(1));backup.append(table+" rows="+r.getLong(1)+"\\n");}
   }
   try(ResultSet r=s.executeQuery("SELECT COUNT(*) FROM dq_quality_run_t WHERE is_del=0 AND status IN ('PENDING','INITIALIZING','RUNNING')")){r.next();backup.append("active="+r.getLong(1)+"\\n");if(r.getLong(1)>0)throw new IllegalStateException("Active quality runs; postpone migration/restart");}
   Files.writeString(Path.of(args[1]),backup.toString());
   if(mode.equals("apply")){
    if(!columns.get("dq_quality_metric_t").contains("profile_json"))s.execute("ALTER TABLE dq_quality_metric_t ADD profile_json "+(url.startsWith("jdbc:dm:")?"CLOB":"LONGTEXT")+" NULL");
    if(!columns.get("dq_quality_run_t").contains("run_kind"))s.execute("ALTER TABLE dq_quality_run_t ADD run_kind VARCHAR(16) DEFAULT 'QUALITY' NOT NULL");
   }
   for(String table:counts.keySet())try(ResultSet r=s.executeQuery("SELECT COUNT(*) FROM "+table)){r.next();if(r.getLong(1)!=counts.get(table))throw new AssertionError("Concurrent result writes; inspect migration");}
   System.out.println("QUALITY_SCHEMA_"+mode.toUpperCase()+"_OK");
  }
 }
}`;
const mode=process.argv.includes('--apply')?'apply':'inspect';
for(const key of ['WX_SCHEMA_JAVA','WX_SCHEMA_DRIVER_CP'])assert(process.env[key],key+' required');
let config,accessToken;
const nacos=process.env.WX_NACOS_URL||'http://192.168.175.86:8848';
if(process.env.WX_NACOS_PASSWORD){const r=await fetch(nacos+'/nacos/v1/auth/users/login',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({username:process.env.WX_NACOS_USER||'nacos',password:process.env.WX_NACOS_PASSWORD}),signal:AbortSignal.timeout(10000)}).then(r=>r.json());assert(r.accessToken,'Nacos authentication failed');accessToken=r.accessToken;}
for(const dataId of ['data-element.yml','data-element-'+(process.env.DATA_ELEMENT_PROFILE||'dev')+'.yml']){
 const url=new URL('/nacos/v1/cs/configs',nacos);url.search=new URLSearchParams({dataId,group:'DEFAULT_GROUP',tenant:'data-element',...(accessToken?{accessToken}:{})});
 const r=await fetch(url,{signal:AbortSignal.timeout(15000)});if(r.ok){const v=yaml.load(await r.text()),next=v?.['data-element']?.tenant?.database;if(next)config={...config,...next,'data-sources':{...config?.['data-sources'],...next['data-sources']}};}
}
assert(config?.['data-sources'],'Cannot resolve the live tenant definitions; nothing modified');
const defs=Object.entries(config['data-sources']);
const root=await mkdtemp(join(tmpdir(),'wx-quality-schema-before-')),file=join(root,'WxQualitySchema.java');await writeFile(file,source);
const resolve=v=>String(v??'').replace(/\$\{([^}:]+)(?::([^}]*))?\}/g,(_,key,fallback)=>process.env[key]??fallback??'');
const results=[];
try{for(const [key,d]of defs){const url=resolve(d.url);if(!/^jdbc:(mysql|dm):/.test(url))throw Error('Unverified tenant vendor; inspect '+key);const schema=resolve(d.schema),report=join(root,key.replace(/[^a-z0-9_-]/gi,'_')+'.txt');const r=await exec(process.env.WX_SCHEMA_JAVA,['-cp',process.env.WX_SCHEMA_DRIVER_CP,file,mode,report],{env:{...process.env,WX_SCHEMA_URL:url,WX_SCHEMA_NAME:schema,WX_SCHEMA_USER:resolve(d.username),WX_SCHEMA_PASSWORD:resolve(d.password)},windowsHide:true,timeout:45000});assert(/QUALITY_SCHEMA_|NO_QUALITY_TABLES/.test(r.stdout));results.push({dataSourceKey:key,schema:schema||new URL(url.substring(5)).pathname.slice(1),status:r.stdout.trim()});}}
finally{await rm(file,{force:true});}
await writeFile(join(root,'result.json'),JSON.stringify({mode,results},null,2));console.log(JSON.stringify({mode,backup:root,results},null,2));
