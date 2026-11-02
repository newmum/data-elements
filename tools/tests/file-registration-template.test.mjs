import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import path from 'node:path';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../../',import.meta.url));
const require=createRequire(path.join(root,'data-elements-chengtian/package.json'));
const ExcelJS=require('exceljs');
const sandbox={};vm.runInNewContext(fs.readFileSync(path.join(root,'data-elements-chengtian/public/babel.min.js'),'utf8'),sandbox);
const file='lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource)/01.登记数据源(register-db).vue';
const source=fs.readFileSync(path.join(root,file),'utf8');
const script=source.match(/<script setup[^>]*>([\s\S]*?)<\/script>/)[1];
const nodes=sandbox.Babel.transform(script,{ast:true,code:false,sourceType:'module',parserOpts:{plugins:['typescript']}}).ast.program.body;
function extract(name){const node=nodes.find(n=>n.type==='FunctionDeclaration'?n.id.name===name:n.type==='VariableDeclaration'&&n.declarations.some(d=>d.id.name===name));assert.ok(node,name);return script.slice(node.start,node.end);}
const constants=['fileFormatOptions','fileRegistrationHeaders','fileRegistrationRequiredHeaders'];
const functions=['excelColumnName','normalizeTemplateHeader','getCellText','styleWorksheetHeader','autoFitColumns','setWorksheetColumnWidth','addTemplateSheet','markRequiredColumns','templateCellText','worksheetToObjects','validateTemplateHeaders','parseFileRegistrationRows','downloadFileRegistrationTemplate'];
function fixture(){
 let blob,filename;
 const state={ExcelJS,Blob,URL:{createObjectURL(value){blob=value;return 'blob:test'},revokeObjectURL(){}},window:{setTimeout(fn){fn()}},document:{createElement(){return {style:{},set download(value){filename=value},click(){}}},body:{appendChild(){},removeChild(){}}}};
 const api=new Function(...Object.keys(state),constants.map(extract).join('\n')+'\n'+functions.map(extract).join('\n')+'\nreturn {parseFileRegistrationRows,worksheetToObjects,validateTemplateHeaders,downloadFileRegistrationTemplate};')(...Object.values(state));
 return {...api,async workbook(){await api.downloadFileRegistrationTemplate();const w=new ExcelJS.Workbook();await w.xlsx.load(await blob.arrayBuffer());return {workbook:w,filename};}};
}
test('file registration workbook has instructions and file definitions, five required columns, format choices and size validation',async()=>{
 const f=fixture();const {workbook:w,filename}=await f.workbook();
 assert.equal(filename,'文件登记模板.xlsx');
 assert.deepEqual(w.worksheets.filter(s=>s.state==='visible').map(s=>s.name),['填写说明','文件登记']);
 assert.equal(w.getWorksheet('数据字段'),undefined);
 const sheet=w.getWorksheet('文件登记');
 assert.equal(sheet.getRow(1).values.filter(v=>String(v).includes('*')).length,5);
 assert.equal(sheet.getCell('E2').dataValidation.type,'list');
 assert.equal(sheet.getCell('F2').dataValidation.type,'decimal');
 const rows=f.parseFileRegistrationRows(f.worksheetToObjects(sheet));
 assert.deepEqual(rows.map(r=>r.fileCode),['scene_photo','case_document','business_document']);assert.equal(rows.length,3);assert.deepEqual(rows.map(r=>r.fileFormat),['jpg','pdf','docx']);
 assert.ok(rows.every(r=>!r.relatedTableName&&!r.relatedFieldName));
 assert.match(JSON.stringify(w.getWorksheet('填写说明').getSheetValues()),/直接进入登记内容审查/);
});
test('file import validates required values, names, formats, bounds and optional association pair without accepting the structured workbook',()=>{
 const f=fixture();const valid={'文件名称':'现场照片','文件说明':'采集照片','文件格式':'JPG 图片','文件大小上限(MB)':'20','关联数据表名':'','关联字段名':''};
 for(const code of ['', '中文','UPPER','has-dash','1bad'])assert.throws(()=>f.parseFileRegistrationRows([{...valid,'文件英文名':code}]));
 assert.throws(()=>f.parseFileRegistrationRows([{...valid,'文件英文名':'same_code'},{...valid,'文件名称':'第二项','文件英文名':'same_code'}]));
 for(const column of ['文件名称','文件说明','文件格式','文件大小上限(MB)'])assert.throws(()=>f.parseFileRegistrationRows([{...valid,[column]:''}]));
 for(const value of ['0','-1','1048577','NaN','1e2'])assert.throws(()=>f.parseFileRegistrationRows([{...valid,'文件大小上限(MB)':value}]));
 assert.throws(()=>f.parseFileRegistrationRows([{...valid,'文件格式':'exe'}]));
 assert.throws(()=>f.parseFileRegistrationRows([{...valid,'关联字段名':'photo_id'}]));
 assert.equal(f.parseFileRegistrationRows([{...valid,'关联数据表名':'police_person'}])[0].relatedTableName,'police_person');
 assert.throws(()=>f.parseFileRegistrationRows([valid,valid]));
 assert.throws(()=>f.parseFileRegistrationRows(Array.from({length:101},(_,i)=>({...valid,'文件名称':'file-'+i}))));
 assert.throws(()=>f.validateTemplateHeaders(undefined,'文件登记',['文件名称']));
});
test('conditional flow skips only the three table steps and restores normal forward/back navigation',()=>{
 const store=fs.readFileSync(path.join(root,'data-elements-chengtian/src/store/modules/register-store.ts'),'utf8');
 const methods=store.slice(store.indexOf('  const setCurrentStep ='),store.indexOf('  // 状态控制方法'));
 const state={value:{currentStep:0,steps:Array.from({length:6},(_,i)=>({skipped:i>=1&&i<=3,hidden:i===5}))}};
 const {preStep,nextStep}=new Function('state',methods.replace('(step: number)','(step)')+'\nreturn {preStep,nextStep};')(state);
 nextStep();assert.equal(state.value.currentStep,4);preStep();assert.equal(state.value.currentStep,0);
 state.value.steps.forEach(s=>s.skipped=false);nextStep();assert.equal(state.value.currentStep,1);preStep();assert.equal(state.value.currentStep,0);
});

test('skipped table steps remain in the five-step header without becoming return targets',()=>{
 const ts=require('typescript');
 const component=fs.readFileSync(path.join(root,'data-elements-chengtian/src/components/business/asset-register.vue'),'utf8');
 const body=component.match(/<script setup[^>]*>([\s\S]*?)<\/script>/)[1];
 const ast=ts.createSourceFile('asset-register.ts',body,ts.ScriptTarget.Latest,true);
 const names=['visibleSteps','displayCurrentStep','displaySteps','goToCompletedStep'];
 const declarations=ast.statements.filter(n=>ts.isVariableStatement(n)&&n.declarationList.declarations.some(d=>names.includes(d.name.getText(ast)))).map(n=>n.getText(ast)).join('\n');
 const js=ts.transpileModule(declarations,{compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText;
 const store={state:{currentStep:0,steps:Array.from({length:6},(_,index)=>({title:String(index+1),skipped:index>=1&&index<=3,hidden:index===5}))},setCurrentStep(index){this.state.currentStep=index;}};
 const computed=get=>({get value(){return get();}});
 const nav=new Function('store','computed',js+'\nreturn {visibleSteps,displayCurrentStep,displaySteps,goToCompletedStep};')(store,computed);
 assert.equal(nav.visibleSteps.value.length,5);
 assert.equal(nav.displayCurrentStep.value,0);
 store.state.currentStep=4;
 assert.equal(nav.displayCurrentStep.value,4);
 assert.deepEqual(nav.displaySteps.value.map(s=>s.clickable),[true,false,false,false,false]);
 nav.goToCompletedStep(1);assert.equal(store.state.currentStep,4);
 nav.goToCompletedStep(0);assert.equal(store.state.currentStep,0);
 store.state.steps.forEach(s=>s.skipped=false);store.state.currentStep=3;
 nav.goToCompletedStep(1);assert.equal(store.state.currentStep,1);
 store.state.currentStep=5;assert.equal(nav.displayCurrentStep.value,4);
});
