import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import path from 'node:path';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../../',import.meta.url));
const require=createRequire(path.join(root,'data-elements-chengtian/package.json'));
const ExcelJS=require('exceljs');const sandbox={};vm.runInNewContext(fs.readFileSync(path.join(root,'data-elements-chengtian/public/babel.min.js'),'utf8'),sandbox);
const source=fs.readFileSync(path.join(root,'lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource)/01.登记数据源(register-db).vue'),'utf8');
const script=source.match(/<script setup[^>]*>([\s\S]*?)<\/script>/)[1];
const nodes=sandbox.Babel.transform(script,{ast:true,code:false,sourceType:'module',parserOpts:{plugins:['typescript']}}).ast.program.body;
function extract(name){const node=nodes.find(n=>n.type==='FunctionDeclaration'?n.id.name===name:n.type==='VariableDeclaration'&&n.declarations.some(d=>d.id.name===name));assert.ok(node,name);return script.slice(node.start,node.end);}
const constants=['integrationTemplateTableHeaders','integrationTemplateFieldHeaders','requiredIntegrationTemplateTableHeaders','requiredIntegrationTemplateFieldHeaders','businessTypeTemplateOptions','booleanTemplateOptions','resourceCatalogFieldTypes','integrationStandardFormatOptions'];
const functions=['excelColumnName','getCellText','normalizeTemplateHeader','styleWorksheetHeader','autoFitColumns','setWorksheetColumnWidth','addTemplateSheet','addIntegrationTemplateOptionSheet','applyListValidation','markRequiredColumns','formulaText','applyIntegrationTemplateAutoDefaults','downloadIntegrationMetadataTemplate'];
function fixture(mode='receive'){
 let buffer;const accessMode={value:mode};
 const state={accessMode,isFilePush:{value:false},ExcelJS,Blob,URL:{createObjectURL(blob){buffer=blob;return 'blob:test'},revokeObjectURL(){}},document:{createElement(){return {style:{},click(){}}},body:{appendChild(){},removeChild(){}}},window:{setTimeout(fn){fn()}},$message:{success(){}}};
 const api=new Function(...Object.keys(state),constants.map(extract).join('\n')+'\n'+functions.map(extract).join('\n')+'\nreturn {downloadIntegrationMetadataTemplate}')( ...Object.values(state));
 return {...api,async workbook(){await api.downloadIntegrationMetadataTemplate();const workbook=new ExcelJS.Workbook();await workbook.xlsx.load(await buffer.arrayBuffer());return workbook;}};
}
test('structured push and collection workbook retain ordinary table/field definitions',async()=>{
 for(const mode of ['receive','capture']) {
  const workbook=await fixture(mode).workbook();const fields=workbook.getWorksheet('数据字段');const header=fields.getRow(1).values;
  assert.equal(header.length-1,11);
  for(const forbidden of ['是否关联附件','附件大小上限(MB)','附件格式'])assert.ok(!header.includes(forbidden));
  assert.equal(fields.getCell('E2').dataValidation.type,'list');
  const guide=JSON.stringify(workbook.getWorksheet('填写说明').getSheetValues());assert.doesNotMatch(guide,/关联附件|fileId|附件大小/);
  if(mode==='receive')assert.match(guide,/分片上传规范/);
 }
});
test('template parser no longer produces field attachment associations from old optional columns',()=>{
 assert.ok(!source.includes('templateAttachment(row)'));
 assert.ok(!source.includes('pushAttachmentTemplateHeaders'));
 assert.match(source,/数据登记模板\.xlsx/);
 assert.ok(!source.includes('数据接收模板'));
});
