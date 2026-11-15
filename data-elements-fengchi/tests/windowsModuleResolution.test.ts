import {describe,it,expect} from 'vitest';
import ts from 'typescript';
function resolve(...parts:string[]){const stack:string[]=[];for(const part of parts.join('/').replaceAll('\\','/').split('/')){if(part==='..')stack.pop();else if(part&&part!=='.')stack.push(part)}return (/^[A-Za-z]:$/.test(stack[0]||'')?'':'/')+stack.join('/')}
const dirname=(p:string)=>p.slice(0,p.lastIndexOf('/'));
const extname=(p:string)=>p.slice(p.lastIndexOf('.'));
const root=resolve(ts.sys.getCurrentDirectory(),'src');
const relative=(_root:string,p:string)=>p.slice(root.length+1);
const readSource=(p:string)=>ts.sys.readFile(p)||'';
const entries=ts.sys.readDirectory(root,['.ts','.tsx','.js','.jsx']).map(p=>resolve(p));
const insensitive=new Map(entries.map(p=>[p.toLowerCase(),p]));
const dirs=new Set(entries.flatMap(p=>[dirname(p).toLowerCase(),root.toLowerCase()]));
const host:ts.ModuleResolutionHost={fileExists:p=>insensitive.has(resolve(p).toLowerCase()),readFile:p=>{const real=insensitive.get(resolve(p).toLowerCase());return real?readSource(real):undefined},directoryExists:p=>dirs.has(resolve(p).toLowerCase()),useCaseSensitiveFileNames:false};
describe('Windows-compatible frontend module names',()=>{
 it('has no case-insensitive extensionless source collisions',()=>{const seen=new Map<string,string>();for(const p of entries){const key=p.slice(0,-extname(p).length).toLowerCase();expect(seen.get(key),`Conflicting modules: ${seen.get(key)} and ${relative(root,p)}`).toBeUndefined();seen.set(key,relative(root,p))}});
 it('resolves catalog UI and storage separately on a case-insensitive filesystem',()=>{const from=resolve(root,'ModelingWorkspace.tsx'),options={moduleResolution:ts.ModuleResolutionKind.Bundler,jsx:ts.JsxEmit.ReactJSX};const ui=ts.resolveModuleName('./ModelingCatalog',from,options,host).resolvedModule;const store=ts.resolveModuleName('./modelCatalogStore',from,options,host).resolvedModule;expect(ui?.resolvedFileName).toBe(resolve(root,'ModelingCatalog.tsx'));expect(store?.resolvedFileName).toBe(resolve(root,'modelCatalogStore.ts'));expect(host.readFile(ui!.resolvedFileName)).toContain('export default function ModelingCatalog');expect(host.readFile(store!.resolvedFileName)).toContain('saveModelDocument')});
 it('reproduces the old ambiguous import choosing the storage .ts before the UI .tsx',()=>{const legacy=resolve(root,'modelingCatalog.ts').toLowerCase();const oldHost:ts.ModuleResolutionHost={...host,fileExists:p=>resolve(p).toLowerCase()===legacy||host.fileExists(p),readFile:p=>resolve(p).toLowerCase()===legacy?readSource(resolve(root,'modelCatalogStore.ts')):host.readFile(p)};const found=ts.resolveModuleName('./ModelingCatalog',resolve(root,'ModelingWorkspace.tsx'),{moduleResolution:ts.ModuleResolutionKind.Bundler,jsx:ts.JsxEmit.ReactJSX},oldHost).resolvedModule;expect(found?.extension).toBe(ts.Extension.Ts);expect(oldHost.readFile(found!.resolvedFileName)).not.toContain('export default function ModelingCatalog')});

});
