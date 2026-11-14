import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { createRequire } from 'node:module';

const root = new URL('../../', import.meta.url);
const require = createRequire(new URL('data-elements-chengtian/package.json', root));
const ExcelJS = require('exceljs');
const component = new URL('lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource)/01.登记数据源(register-db).vue', root);
const source = fs.readFileSync(component, 'utf8');
const script = source.match(/<script setup[^>]*>([\s\S]*?)<\/script>/)[1];
const sandbox = {};
vm.runInNewContext(fs.readFileSync(new URL('data-elements-chengtian/public/babel.min.js', root), 'utf8'), sandbox);
const nodes = sandbox.Babel.transform(script, {
  ast: true, code: false, sourceType: 'module', parserOpts: { plugins: ['typescript'] },
}).ast.program.body;
const names = [
  'resourceCatalogSourceTypes', 'sourceTypeLabels', 'resourceCatalogHeaders', 'resourceFieldHeaders',
  'requiredResourceCatalogHeaders', 'requiredResourceFieldHeaders', 'businessTypeTemplateOptions',
  'booleanTemplateOptions', 'resourceCatalogFieldTypes', 'sourceTemplateExamples', 'commonRelationalFieldTypes', 'sourceFieldTypeOptions',
  'relationalSourceTypes', 'templateTableHeaders', 'templateFieldHeaders', 'requiredTableHeaders', 'requiredFieldHeaders',
  'dataUploadHeaders', 'dataUploadFieldHeaders', 'usesResourceCatalogTemplate',
  'currentTemplateExample', 'normalizeTemplateHeader', 'excelColumnName', 'currentTableTypeOptions',
  'currentFieldTypeOptions', 'currentResourceCatalogFieldTypeOptions', 'findFieldType', 'styleWorksheetHeader',
  'autoFitColumns', 'setWorksheetColumnWidth', 'addTemplateSheet', 'addTemplateOptionSheet',
  'applyListValidation', 'markRequiredColumns', 'formulaText', 'applyTemplateAutoDefaults',
  'downloadResourceCatalogTemplate', 'downloadCurrentMetadataTemplate', 'parseBooleanText',
  'templateCellText', 'worksheetToObjects', 'validateTemplateHeaders', 'requireTemplateCell',
  'matchTemplateOption', 'getCellText', 'validateImportedResourceTemplateRows', 'normalizeImportedResourceMetadata',
  'validateImportedTemplateRows', 'normalizeImportedMetadata', 'buildImportSnapshotPayload', 'handleMetadataTemplateImport',
];
const declarations = names.map(name => {
  const node = nodes.find(n => n.type === 'FunctionDeclaration' ? n.id.name === name
    : n.type === 'VariableDeclaration' && n.declarations.some(d => d.id.name === name));
  assert.ok(node, name);
  return script.slice(node.start, node.end);
});
function fixture(type = 'ftp') {
  let blob, filename, staged;
  const errors = [], writes = [];
  const state = {
    sourceType: { value: type }, accessMode: { value: 'explore' }, connectionEnabled: { value: '0' },
    templateDownloadState: {}, metadataImportState: {}, currentDb: { value: {} }, isFilePush: { value: false },
    isRelationalSource: { value: type === 'mysql' }, isOfflineApiCapture: { value: false },
    formRef: { value: undefined },
    ExcelJS, Blob, computed: get => ({ get value() { return get(); } }), nextTick: async () => {},
    URL: { createObjectURL(value) { blob = value; return 'blob:validation'; }, revokeObjectURL() {} },
    window: { setTimeout(fn) { fn(); } },
    document: { createElement: () => ({ style: {}, set download(value) { filename = value; }, click() {} }),
      body: { appendChild() {}, removeChild() {} } },
    $message: { success() {}, warning: message => errors.push(message), error: message => errors.push(message) },
    rememberMetadataImportForCurrentMode: imported => { staged = imported; },
    persistImportedMetadataSnapshot: async (dbId, imported) => writes.push(api.buildImportSnapshotPayload(dbId, imported)),
    markMetadataImportPersisted() {},
  };
  const api = new Function(...Object.keys(state), declarations.join('\n')
    + '\nreturn {downloadCurrentMetadataTemplate,handleMetadataTemplateImport,buildImportSnapshotPayload};')(...Object.values(state));
  return {
    state, errors, writes, get staged() { return staged; },
    async download() {
      await api.downloadCurrentMetadataTemplate();
      assert.ok(blob, errors.join('\n'));
      const workbook = new ExcelJS.Workbook();
      await workbook.xlsx.load(await blob.arrayBuffer());
      return { filename, workbook };
    },
    async upload(workbook, name) {
      const bytes = await workbook.xlsx.writeBuffer();
      const input = { value: name, files: [{ name, arrayBuffer: async () => bytes }] };
      await api.handleMetadataTemplateImport({ target: input });
      assert.equal(input.value, '', 'The same file can be selected again after an error');
    },
  };
}

test('offline FTP extraction accepts its own XLSX and preserves file metadata for saving', async () => {
  const f = fixture();
  const { filename, workbook } = await f.download();
  await f.upload(workbook, filename);
  assert.deepEqual(f.errors, []);
  assert.equal(filename, 'FTP资源目录导入模板.xlsx');
  assert.ok(workbook.getWorksheet('资源目录信息'));
  assert.ok(workbook.getWorksheet('资源数据项'));
  assert.equal(f.staged.metadataImport.tableCount, 1);
  assert.equal(f.staged.metadataImport.fieldCount, 2);
  assert.equal(f.staged.tables[0].dataFormat, 'CSV');
  assert.equal(f.staged.tables[0].sourcePath, '/data/alarm/alarm.csv');
  assert.equal(f.staged.fieldMap[f.staged.tables[0].tableName][0].sourcePath, 'file_name');
  assert.equal(f.writes.length, 0, 'New source remains a draft until save');

  f.state.currentDb.value = { tid: 'validation-ftp' };
  await f.upload(workbook, filename);
  assert.equal(f.writes.length, 1);
  assert.equal(f.writes[0].dbId, 'validation-ftp');
  assert.equal(f.writes[0].dataSourceType, 'ftp');
  assert.equal(f.writes[0].tables[0].sourcePath, '/data/alarm/alarm.csv');
});

test('old table-only workbook gives actionable FTP migration guidance without saving', async () => {
  const f = fixture();
  f.state.currentDb.value = { tid: 'validation-ftp' };
  const old = new ExcelJS.Workbook();
  old.addWorksheet('数据表信息').addRow(['数据表名']);
  old.addWorksheet('数据项信息').addRow(['字段英文名']);
  await f.upload(old, '旧FTP登记模板.xlsx');
  assert.match(f.errors[0], /旧版.*FTP.*重新下载.*数据格式.*来源地址/);
  assert.equal(f.writes.length, 0);
  assert.equal(f.staged, undefined);
});

test('missing file location remains invalid in the new FTP workbook', async () => {
  const f = fixture();
  f.state.currentDb.value = { tid: 'validation-ftp' };
  const { filename, workbook } = await f.download();
  workbook.getWorksheet('资源目录信息').getCell('G2').value = '';
  await f.upload(workbook, filename);
  assert.match(f.errors[0], /来源地址\/主题.*不能为空/);
  assert.equal(f.writes.length, 0);
});
