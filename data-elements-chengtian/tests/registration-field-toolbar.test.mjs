import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { createRequire } from 'node:module';

const require = createRequire(new URL('../package.json', import.meta.url));
const { compileScript, parse } = require('vue/compiler-sfc');
const root = new URL('../../', import.meta.url);
const filename = 'lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource)/03.登记数据表(register-dbTable-catalog).vue';
const source = fs.readFileSync(new URL(filename, root), 'utf8');
const { descriptor, errors } = parse(source, { filename });
const template = descriptor.template?.content ?? '';
const style = descriptor.styles.map(({ content }) => content).join('\n');

test('the canonical registration component compiles with its template', () => {
  assert.deepEqual(errors, []);
  const { content } = compileScript(descriptor, { id: 'registration-field-toolbar-test', inlineTemplate: true });
  assert.match(content, /字段治理配置/);
  assert.match(content, /syncActiveTableFields/);
  assert.doesNotMatch(source, /tokens truncated|Warning: truncated output/);
});

test('the title-adjacent sync icon is accessible and flanks a centered view switch', () => {
  const title = template.match(/<div class="field-toolbar-title">([\s\S]*?)<\/div>/)?.[1];
  assert.match(title, /<strong>字段治理配置<\/strong>\s*<el-button class="field-sync-button"/);
  assert.match(title, /v-if="businessViewMode === 'fields'"/);
  assert.match(title, /aria-label="同步字段"/);
  assert.match(title, /:loading="syncingFields"/);
  assert.match(title, /:disabled="!activeTable\?\.tid"/);
  assert.match(title, /@click="syncActiveTableFields"/);
  assert.match(style, /\.field-toolbar\s*\{\s*display: grid;\s*grid-template-columns: minmax\(0, 1fr\) auto minmax\(0, 1fr\);/);
  assert.match(style, /> \.business-view-switch\s*\{[^}]*justify-self: center;/);
  assert.match(style, /\.field-sync-button\s*\{[^}]*width: 28px;[^}]*height: 28px;[^}]*padding: 0;/);
});

test('narrow panels wrap badges and preserve the complete timestamp in a title', () => {
  assert.match(style, /@container registration-fields \(max-width: 820px\)\s*\{\s*\.field-toolbar > \.field-summary\s*\{[^}]*grid-column: 1 \/ -1;[^}]*flex-wrap: wrap;/);
  assert.match(style, /@container registration-fields \(max-width: 540px\)\s*\{[\s\S]*?\.field-toolbar > \.business-view-switch\s*\{[^}]*grid-column: 1 \/ -1;/);
  assert.doesNotMatch(style, /\.field-summary\s*\{[^}]*display: none;/);
  assert.match(template, /:title="`时间戳 \$\{timestampFieldName/);
  assert.match(style, /span:last-child\s*\{[^}]*max-width: 200px;[^}]*text-overflow: ellipsis;/);
});
