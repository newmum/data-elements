import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
const require=createRequire(import.meta.url);
const ts=require(process.env.TYPESCRIPT_PATH||'typescript');
function walk(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(dir,e.name)):[path.join(dir,e.name)]);}
const files=walk('src').filter(f=>/\.tsx?$/.test(f)&&!f.endsWith('.d.ts'));
let errors=0;
for(const f of files){const text=fs.readFileSync(f,'utf8');const out=ts.transpileModule(text,{fileName:f,reportDiagnostics:true,compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ESNext,jsx:ts.JsxEmit.ReactJSX,isolatedModules:true,verbatimModuleSyntax:true}});for(const d of out.diagnostics??[]){if(d.category===ts.DiagnosticCategory.Error){errors++;console.error(f,ts.flattenDiagnosticMessageText(d.messageText,'\n'));}}}
console.log(JSON.stringify({typescript:ts.version,filesChecked:files.length,syntaxErrors:errors,note:'Syntax/transpile check only. Not dependency type-checking or a browser build.'},null,2));
process.exitCode=errors?1:0;
