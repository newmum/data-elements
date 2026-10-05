import fs from 'node:fs';
import path from 'node:path';
import {compiler,projectRoot as root} from './compiler.mjs';
const ts=compiler();let count=0,errors=[];
const files=[];function walk(d){for(const e of fs.readdirSync(d,{withFileTypes:true})){const p=path.join(d,e.name);if(e.isDirectory())walk(p);else if(/\.tsx?$/.test(p)&&!/\.d\.ts$/.test(p))files.push(p);}}walk(path.join(root,'src'));
for(const file of files){const text=fs.readFileSync(file,'utf8');const result=ts.transpileModule(text,{fileName:file,reportDiagnostics:true,compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ESNext,jsx:ts.JsxEmit.ReactJSX}});for(const d of result.diagnostics||[])if(d.category===ts.DiagnosticCategory.Error)errors.push(`${file}: ${ts.flattenDiagnosticMessageText(d.messageText,' ')}`);
 for(const m of text.matchAll(/(?:from\s*|import\s*\()\s*['"](\.[^'"]+)['"]/g)){const abs=path.resolve(path.dirname(file),m[1]);if(!['','.ts','.tsx','.js','.css','/index.ts','/index.tsx'].some(ext=>fs.existsSync(abs+ext)))errors.push(`缺失本地引用 ${file} -> ${m[1]}`);}count++;}
// The imported canvas stays at the 2026-09-29 source baseline. Local Haitong fixes are allowed.
for(const file of files.filter(file=>file.startsWith(path.join(root,'src','nifi')+path.sep))){
 const source=fs.readFileSync(file,'utf8');
 if(/structured-surveillance|getSurveillance(?:Channels|Resources|KafkaConnection|OutputKafkaConnection)|getSelectedSurveillanceResource(?:Code|Name)/.test(source))errors.push(`NiFi 画布包含基线之后的布控代码：${file}`);
}
const pkg=JSON.parse(fs.readFileSync(path.join(root,'package.json'),'utf8')),base=JSON.parse(fs.readFileSync(path.join(root,'doc/basis/package.original.json'),'utf8'));
for(const group of ['dependencies','devDependencies'])for(const[name,v]of Object.entries(base[group]))if(pkg[group][name]!==v)errors.push(`版本声明漂移：${name}`);
for(const [name,command] of Object.entries(base.scripts))if(pkg.scripts[name]!==command)errors.push(`原始脚本被修改：${name}`);
console.log(`语法与本地导入检查 ${count} 个 TS/TSX 文件；原 28 项依赖声明逐字比较。`);
if(errors.length){console.error(errors.join('\n'));process.exit(1);}console.log('PASS（非完整 React/X6 类型或运行验收）');
