import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {createRequire} from 'node:module';
const require=createRequire(new URL('../package.json',import.meta.url));
const {parse}=require('vue/compiler-sfc');
const source=fs.readFileSync(new URL('../../data-elements/db/migrations/resources/registration-field-toolbar-20260926/register-dbTable-catalog.vue',import.meta.url),'utf8');
const {descriptor,errors}=parse(source);
test('published SFC source remains complete and parsable',()=>{
 assert.equal(errors.length,0);
 assert.ok(descriptor.scriptSetup.content.length>100000);
 assert.doesNotMatch(source,/tokens truncated|Warning: truncated output/);
});
test('sync button has content width and toolbar allocates four columns',()=>{
 assert.match(descriptor.template.content,/<el-button class="field-sync-button" v-if="businessViewMode === 'fields'"/);
 const style=descriptor.styles.map(x=>x.content).join('\n');
 assert.match(style,/grid-template-columns: minmax\(160px, 1fr\) auto auto auto;/);
 assert.match(style,/\.field-sync-button\s*\{[^}]*width: max-content;[^}]*justify-self: start;/);
 assert.doesNotMatch(style,/minmax\(320px, 1fr\)/);
});
test('narrow panel wraps badges instead of hiding them and long timestamp has a title',()=>{
 const style=descriptor.styles.map(x=>x.content).join('\n');
 assert.match(style,/@container registration-fields \(max-width: 820px\)/);
 assert.match(style,/\.field-toolbar > \.field-summary\s*\{[^}]*grid-column: 1 \/ -1;[^}]*flex-wrap: wrap;/);
 assert.doesNotMatch(style,/\.field-summary\s*\{[^}]*display: none;/);
 assert.match(descriptor.template.content,/:title="`时间戳 \$\{timestampFieldName/);
 assert.match(style,/span:last-child\s*\{[^}]*max-width: 200px;[^}]*text-overflow: ellipsis;/);
});
