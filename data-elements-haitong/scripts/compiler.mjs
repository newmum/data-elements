import {createRequire} from 'node:module';
import {execFileSync} from 'node:child_process';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const require=createRequire(import.meta.url);
export function compiler(){try{return require('typescript');}catch{const root=execFileSync('npm',['root','-g'],{encoding:'utf8'}).trim();const ts=require(path.join(root,'typescript'));console.log(`使用环境预装 TypeScript ${ts.version}（不是项目依赖安装验证）`);return ts;}}
export const projectRoot=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
