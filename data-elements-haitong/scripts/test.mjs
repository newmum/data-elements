import fs from 'node:fs';import os from 'node:os';import path from 'node:path';import {spawnSync}from'node:child_process';import {compiler,projectRoot as root}from'./compiler.mjs';
const ts=compiler();const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'haitong-test-'));fs.writeFileSync(path.join(tmp,'package.json'),'{"type":"commonjs"}');
const inputs=['src/domain/types.ts','src/domain/rules.ts','src/domain/engine.ts','src/domain/seed.ts','src/services/workspace.ts'].map(p=>path.join(root,p));
const options={strict:true,skipLibCheck:true,target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS,moduleResolution:ts.ModuleResolutionKind.Node10,lib:['lib.es2022.d.ts','lib.dom.d.ts'],outDir:tmp,noEmitOnError:true};
const program=ts.createProgram(inputs,options);const diagnostics=ts.getPreEmitDiagnostics(program);if(diagnostics.length){console.error(ts.formatDiagnosticsWithColorAndContext(diagnostics,{getCurrentDirectory:()=>root,getCanonicalFileName:f=>f,getNewLine:()=> '\n'}));process.exit(1);}program.emit();
console.log(`PASS ${inputs.length} 个领域/仓储入口严格类型检查 / TypeScript ${ts.version}`);
const result=spawnSync(process.execPath,['--test',path.join(root,'tests/domain.cjs'),path.join(root,'tests/nifi-session.cjs'),path.join(root,'tests/entry-route.cjs')],{env:{...process.env,HAITONG_BUILD:tmp},stdio:'inherit'});fs.rmSync(tmp,{recursive:true,force:true});process.exit(result.status??1);
