import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const pkg=JSON.parse(fs.readFileSync(path.join(root,'package.json'),'utf8'));
const original=JSON.parse(fs.readFileSync(path.join(root,'doc/basis/package.original.json'),'utf8'));
let failed=false;
for(const group of ['dependencies','devDependencies'])for(const [name,version]of Object.entries(original[group]))if(pkg[group][name]!==version){console.error(`原始版本声明被改变：${name}`);failed=true;}
const missing=[];const installed={};
for(const name of Object.keys({...pkg.dependencies,...pkg.devDependencies})){const p=path.join(root,'node_modules',name,'package.json');if(!fs.existsSync(p)){missing.push(name);continue;}const x=JSON.parse(fs.readFileSync(p,'utf8'));installed[name]=x.version;}
// Targeted deny-list, not a replacement for dependency audit. Keep manifest ranges untouched.
const denied=JSON.parse(fs.readFileSync(path.join(root,'doc/dependency-risk.json'),'utf8')).deniedVersions;
function scan(dir){if(!fs.existsSync(dir))return;for(const f of fs.readdirSync(dir,{withFileTypes:true})){if(!f.isDirectory())continue;const p=path.join(dir,f.name);if(f.name.startsWith('@')){scan(p);continue;}const meta=path.join(p,'package.json');if(fs.existsSync(meta)){const x=JSON.parse(fs.readFileSync(meta,'utf8'));if(x.name==='@antv/setup'||denied[x.name]?.includes(x.version)){failed=true;console.error(`阻断已知受污染版本：${x.name}@${x.version}，不要执行该依赖。`);}}scan(path.join(p,'node_modules'));}}
scan(path.join(root,'node_modules'));
if(missing.length){failed=true;console.error(`缺少 ${missing.length} 项依赖：${missing.join(', ')}`);}
if(failed){console.error('依赖检查未通过，不能声称已完成生产构建。参见 README。');process.exit(1);}
console.log('版本声明保持一致；未命中本脚本的已知版本阻断清单。');console.log(JSON.stringify(installed,null,2));
