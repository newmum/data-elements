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
const constants=['pushAttachmentTemplateHeaders','pushAttachmentExtensions','integrationTemplateTableHeaders','integrationTemplateFieldHeaders','requiredIntegrationTemplateTableHeaders','requiredIntegrationTemplateFieldHeaders','businessTypeTemplateOptions','booleanTemplateOptions','resourceCatalogFieldTypes','integrationStandardFormatOptions'];
const functions=['excelColumnName','getCellText','normalizeTemplateHeader','templateAttachment','styleWorksheetHeader','autoFitColumns','setWorksheetColumnWidth','addTemplateSheet','addIntegrationTemplateOptionSheet','applyListValidation','markRequiredColumns','formulaText','applyIntegrationTemplateAutoDefaults','downloadIntegrationMetadataTemplate'];
function fixture(mode='receive'){
 let buffer;const accessMode={value:mode};
 const state={accessMode,ExcelJS,Blob,URL:{createObjectURL(blob){buffer=blob;return 'blob:test'},revokeObjectURL(){}},document:{createElement(){return {style:{},click(){}}},body:{appendChild(){},removeChild(){}}},window:{setTimeout(fn){fn()}},$message:{success(){}}};
 const api=new Function(...Object.keys(state),constants.map(extract).join('\n')+'\n'+functions.map(extract).join('\n')+'\nreturn {templateAttachment,downloadIntegrationMetadataTemplate}')( ...Object.values(state));
 return {...api,async workbook(){await api.downloadIntegrationMetadataTemplate();const workbook=new ExcelJS.Workbook();await workbook.xlsx.load(await buffer.arrayBuffer());return workbook;}};
}
const base={'字段类型':'字符串','字段长度':'64','是否关联附件':'是','附件大小上限(MB)':'20','附件格式':'.JPG，png、pdf'};
test('actual push workbook includes attachment columns, dropdown and usage instructions; other modes retain their fields',async()=>{
 const workbook=await fixture().workbook();const fields=workbook.getWorksheet('数据字段');assert.deepEqual(fields.getRow(1).values.slice(-3),['是否关联附件','附件大小上限(MB)','附件格式']);
 assert.equal(fields.getCell('L2').dataValidation.type,'list');assert.match(JSON.stringify(workbook.getWorksheet('填写说明').getSheetValues()),/fileId/);
 const capture=await fixture('capture').workbook();assert.ok(!capture.getWorksheet('数据字段').getRow(1).values.includes('是否关联附件'));
});
test('attachment cells normalize format and exact bytes, while old templates remain valid',()=>{
 const f=fixture();assert.deepEqual(f.templateAttachment(base),{enabled:true,maxFileBytes:20971520,allowedExtensions:['jpg','png','pdf']});assert.equal(f.templateAttachment({'字段类型':'字符串'}),null);
 for(const values of [{'附件大小上限(MB)':'0'},{'附件大小上限(MB)':'1.5'},{'附件格式':'exe'},{'字段类型':'整数'},{'字段长度':'10'},{'是否关联附件':'否'},{'是否关联附件':'未知'}])assert.throws(()=>f.templateAttachment({...base,...values}));
});
