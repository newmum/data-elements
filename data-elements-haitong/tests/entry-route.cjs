const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const source = fs.readFileSync(path.join(__dirname, '../src/app/entryRoute.ts'), 'utf8');
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText;
const moduleResult = { exports: {} };
new Function('module', 'exports', compiled)(moduleResult, moduleResult.exports);
const { entryRouteDestination, canonicalizeLegacyCanvasUrl } = moduleResult.exports;

test('legacy document selections move into the hash and do not linger across SPA navigation', () => {
  const migrated = canonicalizeLegacyCanvasUrl('https://example.test/haitong/?_t=9&jobId=old-flow&token=secret#/');
  assert.equal(migrated, 'https://example.test/haitong/?_t=9&token=secret#/?jobId=old-flow');
  assert.equal(canonicalizeLegacyCanvasUrl('https://example.test/haitong/?accessTaskId=task-1#/development/canvas'),
    'https://example.test/haitong/#/development/canvas?accessTaskId=task-1');
  assert.equal(canonicalizeLegacyCanvasUrl('https://example.test/haitong/?jobId=old-flow#/overview'),
    'https://example.test/haitong/#/overview');
  assert.equal(canonicalizeLegacyCanvasUrl('https://example.test/haitong/#/development/tasks'), null);
});

test('direct /haitong/canvas entry opens the integrated canvas and keeps selection', () => {
  assert.equal(canonicalizeLegacyCanvasUrl('https://example.test/haitong/canvas'),
    'https://example.test/haitong/#/development/canvas');
  assert.equal(canonicalizeLegacyCanvasUrl('https://example.test/haitong/canvas?pipelineId=flow-1'),
    'https://example.test/haitong/#/development/canvas?pipelineId=flow-1');
  assert.equal(canonicalizeLegacyCanvasUrl('http://localhost:3002/canvas?accessTaskId=task-1'),
    'http://localhost:3002/#/development/canvas?accessTaskId=task-1');
});

test('legacy hash and document queries both enter the integrated canvas', () => {
  assert.equal(entryRouteDestination('?communication=postMessage&tid=123&accessTaskId=456', ''),
    '/development/canvas?communication=postMessage&tid=123&accessTaskId=456');
  assert.equal(entryRouteDestination('', '?_t=998&communication=postMessage&tid=123'),
    '/development/canvas?communication=postMessage&tid=123');
  assert.equal(entryRouteDestination('', '?pipelineId=flow%201&token=secret'),
    '/development/canvas?pipelineId=flow+1');
});

test('hash selection wins, unrelated entry requests open the overview', () => {
  assert.equal(entryRouteDestination('?jobId=new-job', '?jobId=old-job&orgId=abc'),
    '/development/canvas?jobId=new-job&orgId=abc');
  assert.equal(entryRouteDestination('', '?_t=123'), '/overview');
  assert.equal(entryRouteDestination('', ''), '/overview');
});
