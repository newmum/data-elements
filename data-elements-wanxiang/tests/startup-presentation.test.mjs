import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { startupFailure, startupPhases } = require('../.test-build/src/app/startupPresentation.js');
const failure = (message, status, timedOut = false) => Object.assign(new Error(message), { status, timedOut });

test('session-bridge failure is not falsely classified as a backend outage', () => {
  for (const message of ['登录态同步超时，请检查数据中台地址后重试', '无法连接数据中台登录服务，请检查服务后重试']) {
    const result = startupFailure(new Error(message));
    assert.equal(result.kind, 'session-sync');
    assert.equal(result.title, '后端连接尚未就绪');
    assert.match(result.description, /登录信息同步未完成/);
    assert.doesNotMatch(result.detail, /数据中台/);
    assert.doesNotMatch(result.description, /服务已停止|后端宕机/);
  }
});
test('network, request timeout and service responses keep their real distinction', () => {
  assert.equal(startupFailure(failure('无法连接数据中台，请检查网络或服务状态')).kind, 'network');
  assert.equal(startupFailure(failure('Failed to fetch')).title, '暂时无法连接后端服务');
  assert.equal(startupFailure(failure('请求超时，请稍后重试', undefined, true)).kind, 'timeout');
  const service = startupFailure(failure('接口请求失败（503）', 503));
  assert.equal(service.kind, 'service'); assert.equal(service.reason, '服务异常 · HTTP 503');
  assert.equal(startupFailure(failure('接口请求失败（403）', 403)).kind, 'permission');
  assert.equal(startupFailure(failure('接口返回格式无法识别')).kind, 'unknown');
});
test('expired or absent authentication is a login transition, not a service outage', () => {
  for (const error of [failure('拒绝请求', 401), failure('拒绝请求', 100120), new Error('正在前往数据中台登录'), new Error('尚未登录数据中台，请先返回数据中台登录'), new Error('当前登录账号无效，正在前往数据中台登录'), new Error('token invalid'), new Error('登录已过期')]) {
    assert.equal(startupFailure(error).kind, 'authentication');
  }
});
test('diagnostic detail is length-bounded, escapes via React and hides credentials', () => {
  const message = 'Failed to fetch https://user:private@db.local/?token=private2 password=private3 Bearer private4 Authorization: private5';
  const detail = startupFailure(new Error(message)).detail;
  assert.doesNotMatch(detail, /private|user:/); assert.match(detail, /已隐藏/);
  assert.ok(startupFailure(new Error('x'.repeat(400))).detail.length <= 240);
  assert.equal(startupFailure(null).kind, 'unknown');
  assert.equal(startupFailure({ message: '请求超时', timedOut: true }).kind, 'timeout');
});
test('loading phases describe real preparation without a synthetic percentage', () => {
  assert.equal(startupPhases.session.step, 0); assert.equal(startupPhases.workspace.step, 2);
  assert.equal(startupPhases.redirect.step, -1);
  assert.doesNotMatch(JSON.stringify(startupPhases), /%|完成 \d/);
});

// Real React + Ant SSR, not the simplified offline page adapters.
const Module = require('node:module');
const ts = require('typescript');
const previous = { ts: Module._extensions['.ts'], tsx: Module._extensions['.tsx'] };
const transpile = (module, file) => module._compile(ts.transpileModule(fs.readFileSync(file, 'utf8'), { fileName: file, compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true } }).outputText, file);
Module._extensions['.ts'] = transpile; Module._extensions['.tsx'] = transpile;
let Scene;
try { Scene = require(path.resolve('src/app/StartupScene.tsx')).default; }
finally { if (previous.ts) Module._extensions['.ts'] = previous.ts; else delete Module._extensions['.ts']; if (previous.tsx) Module._extensions['.tsx'] = previous.tsx; else delete Module._extensions['.tsx']; }
const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');
const render = props => renderToStaticMarkup(React.createElement(Scene, props));
test('loading scene has a full-size local illustration and accessible busy status', () => {
  const html = render({ state: 'loading' });
  assert.match(html, /data-state="loading"/); assert.match(html, /aria-busy="true"/);
  assert.match(html, /role="status"/); assert.match(html, /aria-live="polite"/);
  assert.match(html, /viewBox="0 0 600 400"/); assert.match(html, /数据治理中心/);
  for (const name of ['元数据', '数据标准', '数据质量', '后端服务']) assert.ok(html.includes(name));
  assert.doesNotMatch(html, /ant-skeleton|万象|无法连接数据中台|[0-9]+%|<img|<iframe/);
});
test('error scene keeps a retry action and collapsible, escaped real diagnostic', () => {
  const html = render({ state: 'error', error: new Error('Failed to fetch <script>'), onRetry: () => {} });
  assert.match(html, /aria-busy="false"/); assert.match(html, /role="alert"/);
  assert.match(html, /暂时无法连接后端服务/); assert.match(html, /重新连接/);
  assert.match(html, /<details[^>]*>/); assert.doesNotMatch(html, /<details[^>]* open/);
  assert.match(html, /&lt;script&gt;/); assert.doesNotMatch(html, /<script>|ant-result/);
});
test('expired authentication renders only the login transition without retry controls', () => {
  const html = render({ state: 'error', error: failure('登录已失效', 401), onRetry: () => {} });
  assert.match(html, /正在返回登录页面/); assert.match(html, /data-state="loading"/);
  assert.doesNotMatch(html, /重新连接|查看连接详情|服务连接异常/);
});
test('contained module loading omits a duplicate system header', () => {
  const html = render({ state: 'loading', phase: 'module', contained: true });
  assert.match(html, /is-contained/); assert.match(html, /正在准备功能工作台/);
  assert.doesNotMatch(html, /wx-startup-brand/);
});
test('startup CSS is scoped, responsive and honors reduced motion', () => {
  const css = fs.readFileSync('src/styles/startup.css', 'utf8');
  assert.match(css, /prefers-reduced-motion:reduce/); assert.match(css, /animation:none!important/);
  assert.match(css, /max-width:680px/); assert.match(css, /overflow-y:auto/);
  assert.doesNotMatch(css, /\bzoom\s*:|https?:\/\//);
  assert.match(fs.readFileSync('src/main.tsx', 'utf8'), /styles\/startup.css/);
});
test('both real framework loading gates use the scene without altering authentication', () => {
  const gate = fs.readFileSync('src/app/AuthGate.tsx', 'utf8');
  assert.match(gate, /<StartupScene state="loading" phase="session"/);
  assert.match(gate, /<StartupScene state="error" error=\{error\}/);
  assert.match(gate, /loadingSession.current\)return/);
  assert.match(gate, /wanxiang:session-expired/); assert.match(gate, /redirectToPlatformLogin/);
  assert.doesNotMatch(gate, /Skeleton|Result|setTimeout|mock/);
  const app = fs.readFileSync('src/app/App.tsx', 'utf8');
  assert.match(app, /phase="workspace"/); assert.match(app, /phase="module" contained/);
});
