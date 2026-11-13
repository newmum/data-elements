import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import test from 'node:test';

const root = new URL('../../', import.meta.url);
const require = createRequire(new URL('data-elements-chengtian/package.json', root));
const Vue = require('vue');
const compiler = require('vue/compiler-sfc');
const source = await fs.readFile(new URL('lowcode/09.系统管理/09.日志管理/日志管理(log-management).vue', root), 'utf8');
const {descriptor, errors} = compiler.parse(source);
assert.deepEqual(errors, []);
const script = descriptor.scriptSetup.content;
const ast = compiler.babelParse(script, {sourceType: 'module', plugins: ['typescript']});
// 从真实 SFC 的 AST 提取整个 setup，不复制业务函数，也不通过正则模拟实现。
const nodes = ast.program.body.filter(node => node.type !== 'ImportDeclaration' && !node.declare);
const names = nodes.flatMap(node => node.type === 'FunctionDeclaration' ? [node.id.name]
  : node.type === 'VariableDeclaration' ? node.declarations.map(declaration => declaration.id.name) : []);
const sandbox = {};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js', root), 'utf8'), sandbox);
const executable = sandbox.Babel.transform(nodes.map(node => script.slice(node.start, node.end)).join('\n') +
  `\nglobalThis.actual = {${names.join(',')}};`, {presets: ['typescript'], filename: 'log.ts'}).code;
const plain = value => JSON.parse(JSON.stringify(value));
const allCapabilities = ['auth', 'operations', 'requests', 'security', 'exports', 'archives', 'requestParameters'];
function fixture(kindValue = 'operations', options = {}) {
  const calls = [], messages = [];
  const send = async (method, path, data) => {
    calls.push({method, path, data: plain(data)});
    if (options.respond) return options.respond(method, path, data);
    if (path === '/capabilities') return options.capabilities ?? allCapabilities;
    return {list: Array.from({length: data.size ?? 20}, (_, index) => ({tid: String(index)})), total: 200};
  };
  const ctx = vm.createContext({
    ref: Vue.ref, shallowRef: Vue.shallowRef, computed: Vue.computed, Error,
    getCurrentInstance: () => ({appContext: {components: options.components ?? {}}}),
    onMounted: () => {}, onBeforeUnmount: () => {}, ElTable: 'ElTable', ElMessageBox: {},
    ElMessage: Object.fromEntries(['success', 'warning', 'error'].map(level => [level, text => messages.push({level, text})])),
    navigator: options.navigator,
    __logClient: options.common ? undefined : {request: send},
    $common: {
      get: async (url, data) => ({data: await send('GET', url.replace('/sym/logs', ''), data)}),
      post: async (url, data) => ({data: await send('POST', url.replace('/sym/logs', ''), data)}),
    },
  });
  vm.runInContext(executable, ctx);
  const f = ctx.actual;
  f.kind.value = kindValue;
  f.capabilities.value = options.capabilities ?? allCapabilities;
  // 固定时间范围，让列表/导出的完整请求可以逐字段比较。
  f.manualRange.value = true;
  f.dates.value = [new Date('2026-11-01T00:00:00Z'), new Date('2026-11-08T00:00:00Z')];
  return {...f, calls, messages, ctx};
}
const snapshot = (body = null, overrides = {}) => ({
  version: 1, status: 'CAPTURED', path: {}, query: {}, body, verified: {}, notices: [], ...overrides,
});
async function switchTab(f, kind) {
  f.kind.value = kind;
  await f.changeTab();
}
test('Stored UA identifies Edge before Chrome, mobile browsers and non-browser clients; absent history stays absent',()=>{
  const f=fixture('auth');
  const cases=[
    ['Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/141.0.0.0 Safari/537.36 Edg/141.0.0.0','Edge 141.0.0.0','Windows 10 / 11'],
    ['Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) Version/18.6 Mobile/15E148 Safari/604.1','Safari 18.6','iOS 18.6'],
    ['Mozilla/5.0 (Linux; Android 14) Chrome/140.0.0.0 Safari/537.36','Chrome 140.0.0.0','Android 14'],
    ['Mozilla/5.0 (X11; Linux x86_64; rv:144.0) Gecko/20100101 Firefox/144.0','Firefox 144.0','Linux'],
    ['PostmanRuntime/7.37.3','Postman 7.37.3',''],
    ['', '历史未记录',''],
    [undefined,'历史未记录',''],
  ];
  for(const [ua,browser,os] of cases){assert.equal(f.browserLabel(ua),browser);assert.equal(f.clientOS(ua),os);}
  assert.equal(f.browserLabel('validation-client'),'其他客户端');
});
test('Legacy details keep every actual safe key, false/0/null and unknown parameters without inventing fields',()=>{
  const f = fixture();
  assert.equal(f.detailParameters.value.length, 0);
  assert.equal(f.parameterCaptureStatus.value, 'LEGACY');
  assert.equal(f.parameterGroups.value.length, 0);
  const old = {deletedTaskName: ['验证任务'], businessObject: 'object-9', enabled: false, offset: 0, optional: null};
  f.detail.value = {request_parameters: old};
  assert.deepEqual(plain(f.detailParameters.value), Object.entries(old));
  assert.deepEqual(plain(f.parameterGroups.value.map(group => group.key)), ['legacy']);
  assert.match(f.parameterGroups.value[0].title, /不完整/);
  assert.match(f.parameterCaptureNotice.value, /历史.*不完整/);
  assert.deepEqual(plain(f.parameterGroups.value[0].value), old);
  assert.ok(!Object.hasOwn(f.parameterGroups.value[0].value, 'taskId'));
  // The new detail adapter returns an explicit LEGACY envelope for missing columns;
  // that envelope must not hide the old, actually recorded safe parameters.
  f.detail.value = {request_parameters: old, parameter_capture_status: 'LEGACY',
    parameter_snapshot: snapshot(null, {status: 'LEGACY'})};
  assert.deepEqual(plain(f.parameterGroups.value.map(group => group.key)), ['legacy']);
  assert.deepEqual(plain(f.parameterGroups.value[0].value), old);
});
test('Auth omits menu and duration, request details retain zero milliseconds and full escaped-as-text UA',()=>{
  const f=fixture('auth');
  const fields=()=>f.detailGroups.value.flatMap(g=>g.fields.map(x=>x[0]));
  assert.ok(!fields().includes('menu_path'));assert.ok(!fields().includes('duration_ms'));
  assert.ok(fields().includes('user_agent'));assert.ok(fields().includes('browser'));
  f.detail.value={user_agent:'PostmanRuntime/7.37.3',duration_ms:0};
  assert.equal(f.detailFieldText('browser'),'Postman 7.37.3');
  assert.equal(f.detailFieldText('user_agent'),'PostmanRuntime/7.37.3');
  f.kind.value='requests';assert.ok(fields().includes('menu_path'));assert.ok(fields().includes('duration_ms'));
  assert.equal(f.detailFieldText('duration_ms'),'0 ms');
  f.detail.value={};assert.equal(f.detailFieldText('duration_ms'),'历史未记录');
  f.kind.value='operations';assert.ok(!fields().includes('duration_ms'));
  assert.match(f.searchPlaceholder.value,/菜单、功能或接口/);
});

test('Drafts never request; submit sends one complete literal parameterKeyword AND ordinary keyword on page one', async () => {
  const f = fixture();
  const literal = '  业务.*%_[]\\" OR id=1  ';
  for (const char of literal) f.parameterKeyword.value += char;
  f.keyword.value = '保存任务';
  f.page.value = 7;
  await Vue.nextTick();
  assert.equal(f.calls.length, 0);
  assert.equal(f.filters().keyword, '');
  assert.ok(!Object.hasOwn(f.filters(), 'parameterKeyword'));
  await f.searchNow();
  assert.equal(f.calls.length, 1);
  assert.equal(f.calls[0].path, '/operations');
  assert.equal(f.calls[0].data.keyword, '保存任务');
  assert.equal(f.calls[0].data.parameterKeyword, literal);
  assert.equal(f.calls[0].data.page, 1);
  assert.ok(!Object.hasOwn(f.calls[0].data, 'parameterName'));
  assert.ok(!Object.hasOwn(f.calls[0].data, 'parameterValue'));
  f.parameterKeyword.value = '未提交参数';
  f.keyword.value = '未提交普通关键词';
  await f.sortChanged({prop: 'event_time', order: 'ascending'});
  f.size.value = 100;
  await f.reloadFirstPage();
  await f.exportLogs();
  assert.deepEqual(f.calls.at(-1), {method: 'POST', path: '/exports', data: {
    ...f.calls.at(-2).data, kind: 'operations', format: 'CSV',
  }});
  assert.equal(f.calls.at(-1).data.parameterKeyword, literal);
  assert.equal(f.calls.at(-1).data.keyword, '保存任务');
  const longKeyword = '完整参数'.repeat(150);
  f.parameterKeyword.value = longKeyword;
  await f.searchNow();
  assert.equal(f.calls.at(-1).data.parameterKeyword, longKeyword);
});

test('Clear commits only its own input, query and reset return to page one', async () => {
  const f = fixture();
  f.keyword.value = '菜单'; f.parameterKeyword.value = 'task-id';
  await f.searchNow();
  f.keyword.value = '普通草稿'; f.page.value = 5;
  await f.clearParameterKeyword();
  assert.equal(f.calls.length, 2);
  assert.equal(f.calls.at(-1).data.page, 1);
  assert.equal(f.calls.at(-1).data.keyword, '菜单');
  assert.ok(!Object.hasOwn(f.calls.at(-1).data, 'parameterKeyword'));
  f.parameterKeyword.value = 'new-id'; await f.searchNow();
  f.parameterKeyword.value = '参数草稿'; f.page.value = 3;
  await f.clearKeyword();
  assert.equal(f.calls.at(-1).data.keyword, '');
  assert.equal(f.calls.at(-1).data.parameterKeyword, 'new-id');
  assert.equal(f.calls.at(-1).data.page, 1);
  f.keyword.value = ''; f.parameterKeyword.value = '';
  await f.searchNow();
  assert.ok(!Object.hasOwn(f.calls.at(-1).data, 'parameterKeyword'));
  f.keyword.value = '另一个'; f.parameterKeyword.value = 'another'; await f.searchNow();
  await f.resetFilters();
  assert.equal(f.submittedKeyword.value, ''); assert.equal(f.submittedParameterKeyword.value, '');
  assert.equal(f.hasFilters.value, false); assert.equal(f.page.value, 1);
});

test('Each tab restores committed conditions, page and sorting, never unsent drafts', async () => {
  const f = fixture('auth');
  await f.loadCapabilities();
  await switchTab(f, 'operations');
  f.keyword.value = '操作查询'; f.parameterKeyword.value = 'op-id'; await f.searchNow();
  await f.sortChanged({prop: 'actor_name', order: 'descending'});
  f.page.value = 4; await f.load();
  f.keyword.value = '丢弃普通草稿'; f.parameterKeyword.value = '丢弃参数草稿';
  await switchTab(f, 'requests');
  assert.equal(f.filters().keyword, ''); assert.ok(!Object.hasOwn(f.filters(), 'parameterKeyword'));
  f.keyword.value = '接口查询'; f.parameterKeyword.value = 'request-id'; await f.searchNow();
  await switchTab(f, 'operations');
  assert.equal(f.keyword.value, '操作查询'); assert.equal(f.parameterKeyword.value, 'op-id');
  assert.equal(f.calls.at(-1).data.keyword, '操作查询');
  assert.equal(f.calls.at(-1).data.parameterKeyword, 'op-id');
  assert.equal(f.calls.at(-1).data.page, 4);
  assert.equal(f.calls.at(-1).data.sortField, 'actor_name');
  await f.exportLogs(); assert.equal(f.calls.at(-1).data.parameterKeyword, 'op-id');
  await switchTab(f, 'requests');
  assert.equal(f.keyword.value, '接口查询'); assert.equal(f.parameterKeyword.value, 'request-id');
  await switchTab(f, 'auth');
  assert.equal(f.canFilterParameters.value, false);
  assert.ok(!Object.hasOwn(f.filters(), 'parameterKeyword'));
});

test('Pagination, dates and result changes do not implicitly submit either draft', async () => {
  const f = fixture();
  f.keyword.value = '已提交'; f.parameterKeyword.value = 'committed'; await f.searchNow();
  f.keyword.value = '草稿'; f.parameterKeyword.value = 'draft';
  f.paginate({page: 3, limit: 100});
  await Vue.nextTick();
  await f.dateChanged(); f.result.value = 'FAILED'; await f.reloadFirstPage();
  for (const call of f.calls) {
    assert.equal(call.data.keyword, '已提交'); assert.equal(call.data.parameterKeyword, 'committed');
  }
  assert.equal(f.calls.at(-1).data.result, 'FAILED');
});

for (const host of ['shared', 'element', 'platform']) for (const count of [20, 100]) {
  test(`${host}: ${count} rows use fixed capability/list requests, no row enrichment or tenant override`, async () => {
    const f = fixture('auth', {
      common: host === 'element',
      components: host === 'shared' ? {DataTable: 'SharedTable', Pagination: 'SharedPager'} : {},
      capabilities: host === 'platform' ? allCapabilities.filter(value => value !== 'requestParameters') : allCapabilities,
    });
    f.size.value = count;
    await f.loadCapabilities();
    assert.equal(f.calls.length, 2); assert.equal(f.rows.value.length, count);
    assert.equal(f.tableComponent, host === 'shared' ? 'SharedTable' : 'ElTable');
    if (host === 'shared') assert.equal(f.tablePresentation.value.showPage, false);
    await switchTab(f, 'operations');
    assert.equal(f.calls.length, 3);
    f.parameterKeyword.value = 'object-id'; await f.searchNow();
    assert.equal(f.calls.length, 4);
    assert.equal(f.canFilterParameters.value, host !== 'platform');
    assert.equal(Object.hasOwn(f.calls.at(-1).data, 'parameterKeyword'), host !== 'platform');
    assert.ok(f.calls.every(call => !Object.hasOwn(call.data, 'tenantId') && !Object.hasOwn(call.data, 'tenant_id')));
  });
}

test('Six capture statuses have explicit Chinese meanings; absent/failed/omitted never become no-parameters', () => {
  const f = fixture();
  const labels = {EMPTY: '无参数', CAPTURED: '已采集', PARTIAL: '部分采集', OMITTED: '按策略省略', FAILED: '采集失败', LEGACY: '历史未采集'};
  for (const [status, label] of Object.entries(labels)) {
    f.detail.value = {parameter_snapshot: snapshot(null, {status})};
    assert.equal(f.parameterCaptureStatus.value, status);
    assert.equal(f.parameterStatuses[f.parameterCaptureStatus.value], label);
    assert.ok(f.parameterCaptureNotice.value);
    if (status === 'LEGACY') assert.equal(f.parameterGroups.value.length, 0);
    else assert.equal(f.parameterGroups.value.find(group => group.key === 'body').present, true);
  }
  f.detail.value = {parameter_capture_status: 'OMITTED', parameter_snapshot: snapshot(null, {status: 'CAPTURED'})};
  assert.equal(f.parameterCaptureStatus.value, 'OMITTED');
  f.detail.value = {parameter_capture_status: 'FAILED'};
  assert.equal(f.parameterStatuses[f.parameterCaptureStatus.value], '采集失败');
  f.detail.value = {};
  assert.equal(f.parameterCaptureStatus.value, 'LEGACY');
  f.detail.value = {parameter_snapshot: snapshot(null, {status: 'future-status'})};
  assert.equal(f.parameterCaptureStatus.value, 'LEGACY');
});

test('Snapshots accept arbitrary nested JSON and scalar bodies; false, zero, null and empty containers stay present', () => {
  const f = fixture();
  const bodies = [false, 0, null, '', [], {}, [false, 0, null, {nested: [{name: '对象', flags: [false, 0, null]}]}]];
  for (const body of bodies) {
    f.detail.value = {parameter_snapshot: snapshot(body, {query: {enabled: false, offset: 0, absent: null}})};
    const groups = f.parameterGroups.value;
    assert.deepEqual(plain(groups.map(group => group.key)), ['path', 'query', 'body', 'verified']);
    const actualBody = groups.find(group => group.key === 'body');
    assert.equal(actualBody.present, true);
    assert.deepEqual(JSON.parse(f.formatParameterJson(actualBody.value)), body);
    const tree = f.parameterTree(actualBody.value);
    assert.ok(tree.length); assert.match(JSON.stringify(tree), /对象|数组|false|0|null|\"\"/);
  }
  const missing = snapshot(); delete missing.body;
  f.detail.value = {parameter_snapshot: missing};
  assert.equal(f.parameterGroups.value.find(group => group.key === 'body').present, false);
  assert.equal(f.calls.length, 0);
});

test('Verified facts stay separate from request input and never rewrite the original execution result', () => {
  const f = fixture();
  f.detail.value = {result: 'FAILED', request_parameters: {deletedTaskName: '旧名称'}, parameter_snapshot: snapshot(
    {deleteTargetTable: true, deletedTaskName: '请求名称'}, {verified: {matchedObjectId: 'verified-id'}})};
  const verified = f.parameterGroups.value.find(group => group.key === 'verified');
  assert.deepEqual(plain(verified.value), {matchedObjectId: 'verified-id'});
  assert.equal(f.detailFieldText('result'), '失败');
  assert.ok(!f.parameterGroups.value.some(group => group.key === 'legacy'));
  assert.equal(f.detailGroups.value[0].title, '执行概况');
  assert.equal(f.detailGroups.value.at(-1).title, '关联信息');
});

test('Highlight uses submitted full literal text, JSON escapes and interpolation, never regexp or HTML', () => {
  const f = fixture();
  const literals = ['a.*[0](x)+?$^|%_', '引号"与\\反斜杠\n换行', '<img src=x onerror=alert(1)>', 'false', '0', 'null'];
  for (const literal of literals) {
    f.submittedParameterKeyword.value = literal;
    f.parameterKeyword.value = '未提交';
    const text = f.formatParameterJson({[literal]: [literal, literal]});
    const parts = f.parameterTextParts(text);
    assert.equal(parts.map(part => part.text).join(''), text);
    assert.equal(parts.filter(part => part.match).length, 3);
    assert.ok(parts.filter(part => part.match).every(part => part.text === JSON.stringify(literal).slice(1, -1)));
  }
  f.submittedParameterKeyword.value = '';
  assert.deepEqual(plain(f.parameterTextParts('false 0 null')), [{text: 'false 0 null', match: false}]);
  assert.doesNotMatch(source, /v-html\s*=|innerHTML|new RegExp/);
});

test('Copy uses the same recursively redacted JSON and exposes clipboard denial/unavailability', async () => {
  const copied = [];
  const f = fixture('operations', {navigator: {clipboard: {writeText: async text => copied.push(text)}}});
  const value = {normalUnknownKey: false, token: 'test-token', nested: [{api_key: 'test-key', zero: 0, absent: null}]};
  f.detail.value = {parameter_snapshot: snapshot(value)};
  await f.copyParameterContent(f.parameterGroups.value.find(group => group.key === 'body').value);
  assert.deepEqual(JSON.parse(copied[0]), {normalUnknownKey: false, token: '[已脱敏]', nested: [{api_key: '[已脱敏]', zero: 0, absent: null}]});
  assert.equal(value.token, 'test-token');
  assert.equal(f.parameterCopyFailure.value, '');
  f.ctx.navigator.clipboard.writeText = async () => {throw new Error('denied');};
  await f.copyParameterContent(false);
  assert.match(f.parameterCopyFailure.value, /复制失败/);
  assert.equal(f.messages.at(-1).level, 'error');
  f.ctx.navigator = undefined;
  await f.copyParameterContent(null);
  assert.match(f.parameterCopyFailure.value, /手动复制/);
  f.ctx.navigator = {clipboard: {writeText: async text => copied.push(text)}};
  await f.copyParameterContent(0);
  assert.equal(copied.at(-1), '0'); assert.equal(f.parameterCopyFailure.value, '');
});

test('SFC template AST binds only explicit submit/clear, gates parameters and compiles without extra dependencies', () => {
  const elements = [];
  function visit(node) {
    if (node.type === 1) elements.push(node);
    for (const child of node.children ?? []) visit(child);
  }
  visit(descriptor.template.ast);
  const prop = (node, name) => node.props.find(item => item.name === name);
  const input = elements.find(node => node.tag === 'el-input' && prop(node, 'model')?.exp?.content === 'parameterKeyword');
  assert.ok(input);
  assert.equal(prop(input, 'placeholder').value.content, '输入参数名、对象 ID 或参数内容');
  assert.equal(prop(input, 'maxlength'), undefined);
  const events = input.props.filter(item => item.name === 'on');
  assert.deepEqual(events.map(event => event.arg.content).sort(), ['clear', 'keydown']);
  assert.equal(events.find(event => event.arg.content === 'clear').exp.content, 'clearParameterKeyword');
  assert.equal(events.find(event => event.arg.content === 'keydown').exp.content, 'searchNow');
  assert.deepEqual(events.find(event => event.arg.content === 'keydown').modifiers.map(item => item.content), ['enter', 'prevent']);
  assert.ok(elements.some(node => prop(node, 'class')?.value?.content === 'log-parameter-filter' && prop(node, 'if')?.exp?.content === 'canFilterParameters'));
  assert.ok(elements.some(node => node.tag === 'el-alert' && prop(node, 'if')?.exp?.content === 'parameterCopyFailure'));
  assert.ok(!elements.some(node => node.props.some(item => item.name === 'html')));
  assert.deepEqual(ast.program.body.filter(node => node.type === 'ImportDeclaration').map(node => node.source.value), ['vue', 'element-plus']);
  const result = compiler.compileScript(descriptor, {id: 'log-validation', inlineTemplate: true});
  assert.ok(result.content);
});

test('SFC setup passes strict TypeScript using actual frontend dependencies, without emitting files', () => {
  const ts = require('typescript');
  const virtual = fileURLToPath(new URL('data-elements-chengtian/src/log-management.validation.ts', root)).replaceAll('\\', '/');
  const options = {target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext,
    moduleResolution: ts.ModuleResolutionKind.Bundler, strict: true, skipLibCheck: true, noEmit: true,
    lib: ['lib.es2022.d.ts', 'lib.dom.d.ts'],
  };
  const host = ts.createCompilerHost(options), original = host.getSourceFile;
  host.getSourceFile = (file, ...args) => file.replaceAll('\\', '/') === virtual
    ? ts.createSourceFile(file, script, options.target, true) : original(file, ...args);
  const program = ts.createProgram([virtual], options, host), file = program.getSourceFile(virtual);
  const diagnostics = [...program.getSyntacticDiagnostics(file), ...program.getSemanticDiagnostics(file)];
  assert.deepEqual(diagnostics.map(item => ({
    line: descriptor.scriptSetup.loc.start.line + file.getLineAndCharacterOfPosition(item.start).line,
    text: script.slice(item.start, item.start + item.length),
    message: ts.flattenDiagnosticMessageText(item.messageText, '\n'),
  })), []);
});

test('Real compiled SFC in three host modes preserves 1440/390 layout, explicit input events and safe details', {timeout: 120000}, async t => {
  const browserRequire = createRequire(new URL('data-elements-idaas/package.json', root));
  const {chromium, expect} = browserRequire('@playwright/test');
  const previousWindow = globalThis.window;
  let compiled;
  try {
    globalThis.window = {Babel: sandbox.Babel};
    const {compileCode} = await import(new URL('data-elements-chengtian/src/plugins/compiler/sfc-compiler.js', root));
    compiled = await compileCode(source);
  } finally {
    if (previousWindow === undefined) delete globalThis.window;
    else globalThis.window = previousWindow;
  }
  assert.doesNotThrow(() => new Function(compiled.compileJs));
  const bundledBrowser = await fs.access(chromium.executablePath()).then(() => true, () => false);
  const browser = await chromium.launch({headless: true,
    ...(!bundledBrowser && process.platform === 'win32' ? {channel: 'msedge'} : {}),
  });
  t.after(() => browser.close());
  for (const host of ['shared', 'element', 'platform']) for (const width of [1440, 390]) {
    await t.test(`${host} at ${width}px`, async () => {
      const page = await browser.newPage({viewport: {width, height: 900}});
      try {
        const pageErrors = [];
        page.on('pageerror', error => pageErrors.push(error.message));
        await page.setContent('<html lang="zh-CN"><head></head><body style="margin:0"><div id="app"></div></body></html>');
        await page.addStyleTag({path: require.resolve('element-plus/dist/index.css')});
        await page.addStyleTag({content: compiled.compileCss});
        await page.addScriptTag({path: require.resolve('vue/dist/vue.global.prod.js')});
        await page.addScriptTag({path: require.resolve('element-plus/dist/index.full.min.js')});
        await page.evaluate(({code, host}) => {
          const Vue = globalThis.Vue, Element = globalThis.ElementPlus;
          globalThis.logCalls = [];
          const send = async (method, path, data) => {
            globalThis.logCalls.push({method, path, data: JSON.parse(JSON.stringify(data))});
            if (path === '/capabilities') return ['operations', 'requests', 'exports', ...(host === 'platform' ? [] : ['requestParameters'])];
            if (path === '/operations/row-0') return {
              tid: 'row-0', result: 'FAILED', operation_name: '删除请求',
              parameter_capture_status: 'PARTIAL',
              parameter_snapshot: {version: 1, status: 'PARTIAL', path: {id: 'task.*%_[]'}, query: {enabled: false, offset: 0},
                body: {nested: [false, 0, null, {text: '<img src=x onerror=alert(1)>', token: 'test-token'}]},
                verified: {matchedId: 'verified-object'}, notices: ['正文已脱敏']},
            };
            return {list: Array.from({length: Number(data.size ?? 20)}, (_, index) => ({tid: `row-${index}`, actor_name: '人员', result: 'FAILED'})), total: 200};
          };
          if (host !== 'element') globalThis.__logClient = {request: send};
          else globalThis.$common = {
            get: async (url, data) => ({data: await send('GET', url.replace('/sym/logs', ''), data)}),
            post: async (url, data) => ({data: await send('POST', url.replace('/sym/logs', ''), data)}),
          };
          const component = new Function('___magic__import__', code)((module, name) => (module === 'vue' ? Vue : Element)[name]);
          const app = Vue.createApp(component);
          app.use(Element);
          if (host === 'shared') {
            app.component('DataTable', {inheritAttrs: false, setup: (_, {attrs, slots}) => () => Vue.h(Element.ElTable, attrs, slots)});
            app.component('Pagination', {props: ['page', 'limit', 'total', 'pageSizes'],
              emits: ['update:page', 'update:limit', 'pagination'],
              setup: (props, {emit}) => () => Vue.h(Element.ElPagination, {
                currentPage: props.page, pageSize: props.limit, pageSizes: props.pageSizes, total: props.total,
                layout: 'prev, pager, next, sizes',
                onCurrentChange: page => {emit('update:page', page); emit('pagination', {page, limit: props.limit});},
                onSizeChange: limit => {emit('update:limit', limit); emit('update:page', 1); emit('pagination', {page: 1, limit});},
              }),
            });
          }
          app.mount('#app');
        }, {code: compiled.compileJs, host});
        await expect(page.locator('.log-table .el-table__body tbody tr')).toHaveCount(20);
        assert.equal(await page.evaluate(() => logCalls.length), 2);
        await page.locator('.log-pager .el-select').click();
        await page.getByRole('option', {name: /^100/}).click();
        await expect(page.locator('.log-table .el-table__body tbody tr')).toHaveCount(100);
        assert.equal(await page.evaluate(() => logCalls.length), 3);
        await page.locator('.log-pager .el-pager li.number').nth(1).click();
        await expect.poll(() => page.evaluate(() => logCalls.length)).toBe(4);
        const parameterInput = page.getByPlaceholder('输入参数名、对象 ID 或参数内容', {exact: true});
        if (host === 'platform') {
          await expect(parameterInput).toHaveCount(0);
        } else {
          await parameterInput.fill('task.*%_[]');
          assert.equal(await page.evaluate(() => logCalls.length), 4);
          await parameterInput.press('Enter');
          await expect.poll(() => page.evaluate(() => logCalls.length)).toBe(5);
          assert.equal(await page.evaluate(() => logCalls.at(-1).data.page), 1);
          await parameterInput.fill('未提交草稿');
          await page.getByRole('button', {name: '导出日志', exact: true}).click();
          await expect.poll(() => page.evaluate(() => logCalls.length)).toBe(6);
          assert.equal(await page.evaluate(() => logCalls.at(-1).data.parameterKeyword), 'task.*%_[]');
          await page.getByRole('button', {name: '查询', exact: true}).click();
          await expect.poll(() => page.evaluate(() => logCalls.length)).toBe(7);
          assert.equal(await page.evaluate(() => logCalls.at(-1).data.parameterKeyword), '未提交草稿');
          await page.getByRole('button', {name: '清空', exact: true}).click();
          await expect.poll(() => page.evaluate(() => logCalls.length)).toBe(8);
          assert.equal(await page.evaluate(() => Object.hasOwn(logCalls.at(-1).data, 'parameterKeyword')), false);
          await parameterInput.fill('task.*%_[]');
          await parameterInput.press('Enter');
          await expect.poll(() => page.evaluate(() => logCalls.length)).toBe(9);
        }
        const geometry = () => page.evaluate(() => ({width: innerWidth, scroll: document.documentElement.scrollWidth}));
        let bounds = await geometry(); assert.ok(bounds.scroll <= bounds.width + 1, JSON.stringify(bounds));
        await page.getByRole('button', {name: '详情', exact: true}).first().click();
        await expect(page.getByRole('heading', {name: '执行概况', exact: true})).toBeVisible();
        await expect(page.getByRole('heading', {name: '关联信息', exact: true})).toBeVisible();
        if (host === 'platform') {
          await expect(page.locator('.log-parameter-status,.log-parameter-group')).toHaveCount(0);
        } else {
          await expect(page.locator('.log-parameter-status')).toContainText('部分采集');
          await expect(page.locator('.log-parameter-group')).toHaveCount(4);
          await expect(page.locator('.log-parameter-group mark').first()).toHaveText('task.*%_[]');
          const body = page.locator('.log-parameter-group').filter({has: page.getByRole('heading', {name: '请求正文（body）', exact: true})});
          await expect(body.locator('pre')).toContainText('false');
          await expect(body.locator('pre')).toContainText('0');
          await expect(body.locator('pre')).toContainText('null');
          await expect(body.locator('pre')).toContainText('<img src=x onerror=alert(1)>');
          await expect(body.locator('img,script')).toHaveCount(0);
          await expect(body.locator('pre')).not.toContainText('test-token');
          await page.evaluate(() => Object.defineProperty(navigator, 'clipboard', {configurable: true, value: {writeText: async () => {throw new Error('denied');}}}));
          await body.getByRole('button', {name: '复制请求正文（body）脱敏内容', exact: true}).click();
          await expect(page.locator('.log-parameter-status .el-alert')).toContainText('复制失败');
          await page.evaluate(() => Object.defineProperty(navigator, 'clipboard', {configurable: true, value: {writeText: async text => {globalThis.copiedLogJson = text;}}}));
          await body.getByRole('button', {name: '复制请求正文（body）脱敏内容', exact: true}).click();
          assert.equal(await page.evaluate(() => JSON.parse(copiedLogJson).nested[0]), false);
          assert.equal(await page.evaluate(() => JSON.parse(copiedLogJson).nested[3].token), '[已脱敏]');
          await expect(page.locator('.log-parameter-status .el-alert')).toHaveCount(0);
          const countBeforeExpand = await page.evaluate(() => logCalls.length);
          await body.locator('.el-tree-node__expand-icon').first().click();
          assert.equal(await page.evaluate(() => logCalls.length), countBeforeExpand);
        }
        bounds = await geometry(); assert.ok(bounds.scroll <= bounds.width + 1, JSON.stringify(bounds));
        assert.deepEqual(pageErrors, []);
      } finally {
        await page.close();
      }
    });
  }
});
