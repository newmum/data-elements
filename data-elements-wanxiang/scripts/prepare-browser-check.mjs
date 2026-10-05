import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const ts=require(process.env.TYPESCRIPT_PATH||'typescript');
const root=process.cwd(),output=path.join(root,'.browser-review/modules');
fs.rmSync(output,{recursive:true,force:true});fs.mkdirSync(output,{recursive:true});
const program=ts.createProgram([path.join(root,'src/services/api.ts'),path.join(root,'src/features/er/services/workspaceService.ts')],{
 target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022,moduleResolution:ts.ModuleResolutionKind.Bundler,
 strict:true,skipLibCheck:true,rootDir:path.join(root,'src'),outDir:output,lib:['lib.es2022.d.ts','lib.dom.d.ts','lib.dom.iterable.d.ts']
});
const diagnostics=ts.getPreEmitDiagnostics(program);
if(diagnostics.length){console.error(ts.formatDiagnosticsWithColorAndContext(diagnostics,{getCurrentDirectory:()=>root,getCanonicalFileName:x=>x,getNewLine:()=> '\n'}));process.exit(1);}
program.emit();
function fix(folder){for(const entry of fs.readdirSync(folder,{withFileTypes:true})){const file=path.join(folder,entry.name);if(entry.isDirectory())fix(file);else if(file.endsWith('.js')){const source=fs.readFileSync(file,'utf8').replace(/(\b(?:from|import)\s*['"])(\.[^'"\n]+)(['"])/g,(_,a,b,c)=>a+b+(path.extname(b)?'':'.js')+c);fs.writeFileSync(file,source);}}}
fix(output);
fs.writeFileSync(path.join(root,'.browser-review/index.html'),'<!doctype html><meta charset="utf-8"><title>Local runtime browser checks</title><script type="module">window.networkCalls=[];window.fetch=(...args)=>{networkCalls.push(args[0]);throw new Error("Business fetch is disabled during this test")};const api=await import("./modules/services/api.js");const {workspaceService}=await import("./modules/features/er/services/workspaceService.js");window.wx={api,workspaceService};window.ready=true;</script><p>Isolated browser tests of the actual local data services. Not the React application.</p>');
console.log('Prepared actual Mock/API/ER service modules for isolated Chromium checks.');
