/** Resolve the existing system ES connection in memory; never output credentials. */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readdir} from 'node:fs/promises';
import {resolve} from 'node:path';
import {spawn} from 'node:child_process';
const require=createRequire(import.meta.url),modules=resolve(import.meta.dirname,'../../data-elements-front/node_modules/.pnpm');
const yaml=require(resolve(modules,(await readdir(modules)).find(n=>n.startsWith('js-yaml@')),'node_modules/js-yaml'));
const nacos=(process.env.WX_NACOS_URL||'http://192.168.175.86:8848')+'/nacos';
assert(process.env.WX_NACOS_USER&&process.env.WX_NACOS_PASSWORD&&process.env.WX_VERIFY_MAVEN,'Nacos environment and Maven path required');
const auth=await fetch(nacos+'/v1/auth/users/login',{method:'POST',body:new URLSearchParams({username:process.env.WX_NACOS_USER,password:process.env.WX_NACOS_PASSWORD}),signal:AbortSignal.timeout(10000)}).then(r=>r.json());assert(auth.accessToken);
let config={};for(const dataId of ['data-element.yml','data-element-dev.yml']){const response=await fetch(nacos+'/v1/cs/configs?'+new URLSearchParams({dataId,group:'DEFAULT_GROUP',tenant:'data-element',accessToken:auth.accessToken}),{signal:AbortSignal.timeout(10000)});if(response.ok){const data=yaml.load(await response.text());config={...config,...data,spring:{...config.spring,...data.spring},elasticsearch:{...config.elasticsearch,...data.elasticsearch},es:{...config.es,...data.es}};}}
const rest=config.spring?.elasticsearch?.rest;let url=rest?.uris||config.elasticsearch?.host;if(Array.isArray(url))url=url[0];url=String(url||'').split(',')[0];if(!/^https?:\/\//.test(url))url='http://'+url;assert(/^https?:\/\/[\w.-]+:\d+/.test(url),'Existing ES URL required');
const environment={...process.env,WX_ES_URL:url,WX_ES_INDEX:String(config.es?.prefix||'elements_')+'dataassets',WX_ES_USER:String(rest?.username||''),WX_ES_PASSWORD:String(rest?.password||'')};
const args=['-Dtest=MetadataReadOnlyAdaptersLiveTest#existingElasticsearchIndexMappingAndCountAreReadable','test','-q'];
const child=process.platform==='win32'?spawn('pwsh',['-NoProfile','-Command',"& $env:WX_VERIFY_MAVEN '-Dtest=MetadataReadOnlyAdaptersLiveTest#existingElasticsearchIndexMappingAndCountAreReadable' 'test' '-q'; exit $LASTEXITCODE"],{cwd:resolve(import.meta.dirname,'../../data-elements'),env:environment,stdio:'inherit',windowsHide:true}):spawn(process.env.WX_VERIFY_MAVEN,args,{cwd:resolve(import.meta.dirname,'../../data-elements'),env:environment,stdio:'inherit'});
child.on('error',error=>{console.error('Cannot launch adapter test: '+error.name);process.exitCode=1;});child.on('exit',code=>{process.exitCode=code??1;});
