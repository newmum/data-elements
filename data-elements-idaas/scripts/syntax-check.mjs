import ts from 'typescript';
import fs from 'node:fs';
import path from 'node:path';
let count=0,errors=0;
function walk(dir){for(const entry of fs.readdirSync(dir,{withFileTypes:true})){const file=path.join(dir,entry.name);if(entry.isDirectory())walk(file);else if(/\.tsx?$/.test(file)){const source=ts.createSourceFile(file,fs.readFileSync(file,'utf8'),ts.ScriptTarget.Latest,true,file.endsWith('tsx')?ts.ScriptKind.TSX:ts.ScriptKind.TS);count++;for(const error of source.parseDiagnostics){errors++;const pos=source.getLineAndCharacterOfPosition(error.start??0);console.error(`${file}:${pos.line+1}:${pos.character+1} ${ts.flattenDiagnosticMessageText(error.messageText,' ')}`);}}}}
walk('src');console.log(`已解析 ${count} 个源码文件；语法错误 ${errors} 项。此检查不替代完整类型检查。`);process.exitCode=errors?1:0;
