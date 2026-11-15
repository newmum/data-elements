import {StreamLanguage, HighlightStyle, syntaxHighlighting, type StringStream} from '@codemirror/language';
import {tags} from '@lezer/highlight';
import {sql} from '@codemirror/lang-sql';

/** JSON model definitions, not executable SQL. Strings are scanned with escape handling. */
export function modelJsonToken(stream:StringStream):string|null {
 if(stream.eatSpace())return null;
 if(stream.peek()==='"'){
  stream.next();let escaped=false;
  while(!stream.eol()){const ch=stream.next();if(ch==='"'&&!escaped)break;if(ch==='\\'&&!escaped)escaped=true;else escaped=false;}
  return /^\s*:/.test(stream.string.slice(stream.pos))?'propertyName':'string';
 }
 if(stream.match(/^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?/))return 'number';
 if(stream.match(/^(?:true|false)\b/))return 'bool';
 if(stream.match(/^null\b/))return 'null';
 if(stream.eat(/[{}\[\],:]/))return 'punctuation';
 stream.next();return 'invalid';
}
export const modelJsonLanguage=StreamLanguage.define({token:modelJsonToken});
export const modelingCodeColors=syntaxHighlighting(HighlightStyle.define([
 {tag:tags.propertyName,color:'var(--model-code-key)'},
 {tag:tags.string,color:'var(--model-code-string)'},
 {tag:[tags.number,tags.bool,tags.null],color:'var(--model-code-number)'},
 {tag:tags.keyword,color:'var(--model-code-key)'},
 {tag:tags.comment,color:'var(--model-muted)'},
 {tag:tags.operator,color:'var(--model-code-number)'},
 {tag:tags.invalid,textDecoration:'underline wavy #d85e5e'},
]));
export function modelingLanguage(language:'json'|'sql'){return language==='json'?modelJsonLanguage:sql();}
