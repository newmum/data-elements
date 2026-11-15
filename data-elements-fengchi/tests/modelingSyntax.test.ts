import {describe,it,expect} from 'vitest';
import {StringStream} from '@codemirror/language';
import {EditorState} from '@codemirror/state';
import {syntaxTree} from '@codemirror/language';
import {modelJsonToken,modelJsonLanguage,modelingLanguage} from '../src/modelingSyntax';
import editor from '../src/ModelingCodeEditor.tsx?raw';
import workspace from '../src/ModelingWorkspace.tsx?raw';
function tokens(text:string){const stream=new StringStream(text,4,2),out:{text:string;kind:string|null}[]=[];while(!stream.eol()){stream.start=stream.pos;const kind=modelJsonToken(stream);out.push({text:stream.current(),kind})}return out}
describe('modeling syntax highlighting',()=>{
 it('distinguishes JSON properties, escaped strings, numbers, booleans and null',()=>{const out=tokens('{"name": "a\\\"b", "n": -1.2e3, "ok": true, "empty": null}');expect(out).toContainEqual({text:'"name"',kind:'propertyName'});expect(out).toContainEqual({text:'"a\\\"b"',kind:'string'});expect(out).toContainEqual({text:'-1.2e3',kind:'number'});expect(out).toContainEqual({text:'true',kind:'bool'});expect(out).toContainEqual({text:'null',kind:'null'});expect(out.some(x=>x.kind==='invalid')).toBe(false)});
 it('marks non-JSON tokens invalid without interpreting code',()=>{expect(tokens('SELECT')[0].kind).toBe('invalid');expect(modelingLanguage('json')).toBe(modelJsonLanguage)});
 it('uses existing SQL parser for SQL drafts',()=>{const state=EditorState.create({doc:'SELECT id FROM users WHERE id = 1',extensions:[modelingLanguage('sql')]});expect(syntaxTree(state).toString()).toContain('Keyword')});
 it('retains read-only generated SQL and editable bounded input draft semantics',()=>{expect(workspace).toContain('language="sql" label="SQL 草稿" value={scriptDraft');expect(workspace).toContain('language="sql"');expect(editor).toContain('readOnly={readOnly} editable={!readOnly}');expect(editor).toContain('tr.newDoc.length>maxLength');expect(editor).not.toContain('fillVisibleSqlRows');expect(editor).not.toContain('fetch(')});
});
