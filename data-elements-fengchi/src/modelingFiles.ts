import type {Cell,Row,TableDef} from './data';
import type {ModelResult} from './modeling';
export type ModelFileFormat='csv'|'json'|'xml';
const MAX_TEXT=2_000_000,MAX_ROWS=1000,MAX_COLS=200,MAX_CELL=100_000;
function checkedText(text:string){if(text.length>MAX_TEXT)throw new Error('文件文本超过 200 万字符');return text.replace(/^\uFEFF/,'')}
function columnNames(columns:string[]){if(columns.length>MAX_COLS)throw new Error('文件超过 200 列');if(columns.some(c=>!c.trim()||c.length>1000)||new Set(columns).size!==columns.length)throw new Error('列名不能为空、重复或超过 1000 字符')}
function scalar(value:unknown):Cell{if(value===null)return null;if(typeof value==='number'&&Number.isFinite(value))return value;if(typeof value==='string'&&value.length<=MAX_CELL)return value;throw new Error('单元格仅支持有限数字、NULL 或不超过 10 万字符的文本')}
function validated(result:ModelResult):ModelResult{columnNames(result.columns);if(result.rows.length>MAX_ROWS)throw new Error('文件超过 1000 行');let chars=0;const rows=result.rows.map(r=>Object.fromEntries(result.columns.map(k=>{const v=scalar(Object.hasOwn(r,k)?r[k]:null);if(typeof v==='string')chars+=v.length;if(chars>MAX_TEXT)throw new Error('数据文本超过 200 万字符');return[k,v]})));return {columns:[...result.columns],rows}}
function csvParse(text:string):ModelResult{
 if(!text)return {columns:[],rows:[]};
 const records:string[][]=[];let row:string[]=[],field='',state:'start'|'plain'|'quoted'|'closed'='start',endedRow=false;
 const append=(c:string)=>{field+=c;if(field.length>MAX_CELL)throw new Error('单元格文本超过 10 万字符')};
 const endField=()=>{row.push(field);field='';state='start';if(row.length>MAX_COLS)throw new Error('文件超过 200 列')};
 const endRow=()=>{endField();records.push(row);row=[];if(records.length>MAX_ROWS+1)throw new Error('文件超过 1000 行')};
 for(let i=0;i<text.length;i++){const c=text[i];endedRow=false;
  if(state==='quoted'){if(c==='"'){if(text[i+1]==='"'){append('"');i++}else state='closed'}else append(c);continue}
  if(c===','){endField();continue}
  if(c==='\n'||c==='\r'){if(c==='\r'&&text[i+1]==='\n')i++;endRow();endedRow=true;continue}
  if(state==='closed')throw new Error('CSV 引号闭合后只能接逗号或换行');
  if(c==='"'){if(state!=='start')throw new Error('CSV 非引号字段中出现引号');state='quoted'}else{state='plain';append(c)}
 }
 if(state==='quoted')throw new Error('CSV 引号未闭合');if(!endedRow)endRow();
 const columns=records.shift()||[];columnNames(columns);
 const rows=records.map(values=>{if(values.length!==columns.length)throw new Error('CSV 每行字段数必须与表头一致');return Object.fromEntries(columns.map((c,i)=>[c,values[i]]))});return {columns,rows};
}
function jsonParse(text:string):ModelResult{let value:unknown;try{value=JSON.parse(text)}catch{throw new Error('无效 JSON 文件')}
 if(!Array.isArray(value))throw new Error('JSON 输入必须是对象数组');if(value.length>MAX_ROWS)throw new Error('文件超过 1000 行');const names=new Set<string>();
 for(const row of value){if(row===null||typeof row!=='object'||Array.isArray(row))throw new Error('JSON 每条记录必须是对象');for(const key of Object.keys(row)){names.add(key);if(names.size>MAX_COLS)throw new Error('文件超过 200 列')}}
 return {columns:[...names],rows:value as Row[]};
}
const validXmlChar=(cp:number)=>cp===9||cp===10||cp===13||(cp>=32&&cp<=0xd7ff)||(cp>=0xe000&&cp<=0xfffd)||(cp>=0x10000&&cp<=0x10ffff);
function xmlCharacters(text:string){for(const c of text)if(!validXmlChar(c.codePointAt(0)!))throw new Error('XML 包含不允许的字符');return text}
function escapeXml(value:string){return xmlCharacters(value).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&apos;')}
function unescapeXml(text:string){if(text.includes('<'))throw new Error('XML 字段只能包含转义文本');const entities:Record<string,string>={amp:'&',lt:'<',gt:'>',quot:'"',apos:"'"};const value=text.replace(/&([^;]*);/g,(_,entity:string)=>{if(Object.hasOwn(entities,entity))return entities[entity];if(/^#(?:[0-9]+|x[0-9a-fA-F]+)$/.test(entity)){const cp=entity[1]==='x'?parseInt(entity.slice(2),16):Number(entity.slice(1));if(validXmlChar(cp))return String.fromCodePoint(cp)}throw new Error('XML 仅支持标准实体和有效数字字符引用')});if(/&(?!amp;|lt;|gt;|quot;|apos;|#(?:[0-9]+|x[0-9a-fA-F]+);)/.test(text))throw new Error('XML 实体不完整');return xmlCharacters(value)}
/** Deliberately accepts only Fengchi's bounded table-exchange XML, not general XML. */
function xmlParse(text:string):ModelResult{
 if(/<!|<\?(?!xml\s)/i.test(text))throw new Error('不支持 XML DTD、声明、CDATA 或处理指令');
 text=text.replace(/^\s*<\?xml\s+version=["']1\.0["'](?:\s+encoding=["']UTF-8["'])?\s*\?>/i,'');let pos=0;
 const ws=()=>{while(pos<text.length&&/\s/.test(text[pos]))pos++};
 const take=(token:string)=>{ws();if(!text.startsWith(token,pos))throw new Error('仅支持风驰表格 XML 交换格式');pos+=token.length};
 const starts=(token:string)=>{ws();return text.startsWith(token,pos)};
 const attrs=(tag:string,selfClosing:boolean)=>{ws();if(!text.startsWith(`<${tag}`,pos))throw new Error('XML 标签错误');pos+=tag.length+1;const out:Record<string,string>=Object.create(null);while(true){const before=pos;ws();const end=selfClosing?'/>':'>';if(text.startsWith(end,pos)){pos+=end.length;break}if(before===pos)throw new Error('XML 属性之间需要空白');const match=/^([A-Za-z]+)\s*=\s*(["'])/.exec(text.slice(pos));if(!match)throw new Error('XML 属性格式错误');pos+=match[0].length;const endQuote=text.indexOf(match[2],pos);if(endQuote<0)throw new Error('XML 属性未闭合');if(Object.hasOwn(out,match[1]))throw new Error('XML 属性重复');out[match[1]]=unescapeXml(text.slice(pos,endQuote));pos=endQuote+1}return out};
 take('<table>');take('<columns>');const columns:string[]=[];
 while(!starts('</columns>')){const a=attrs('column',true);if(Object.keys(a).length!==1||!Object.hasOwn(a,'name'))throw new Error('XML column 仅允许 name 属性');columns.push(a.name);if(columns.length>MAX_COLS)throw new Error('文件超过 200 列')}
 take('</columns>');columnNames(columns);take('<rows>');const rows:Row[]=[];
 while(!starts('</rows>')){take('<row>');const entries:[string,Cell][]=[];const seen=new Set<string>();
  while(!starts('</row>')){const a=attrs('field',false);if(Object.keys(a).length!==2||!Object.hasOwn(a,'name')||!Object.hasOwn(a,'type')||!columns.includes(a.name)||seen.has(a.name))throw new Error('XML 字段名称、属性或重复字段错误');seen.add(a.name);const end=text.indexOf('</field>',pos);if(end<0)throw new Error('XML field 未闭合');const content=unescapeXml(text.slice(pos,end));pos=end+8;let value:Cell;
   if(a.type==='null'){if(content!=='')throw new Error('XML NULL 字段必须为空');value=null}else if(a.type==='string')value=content;else if(a.type==='number'){if(!/^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?$/.test(content)||!Number.isFinite(Number(content)))throw new Error('XML 数字无效');value=Number(content)}else throw new Error('XML 字段类型不支持');entries.push([a.name,scalar(value)])}
  take('</row>');rows.push(Object.fromEntries(entries));if(rows.length>MAX_ROWS)throw new Error('文件超过 1000 行')}
 take('</rows>');take('</table>');ws();if(pos!==text.length)throw new Error('XML 存在额外内容');return {columns,rows};
}
export function parseModelFile(text:string,format:ModelFileFormat,name:string):TableDef{const content=checkedText(text);const result=validated(format==='csv'?csvParse(content):format==='json'?jsonParse(content):xmlParse(content));return {name:name.slice(0,200)||'local-file',comment:'用户选择的本地文件，仅在浏览器会话内使用',columns:result.columns.map(name=>({name,type:result.rows.some(r=>typeof r[name]==='number')?'number':'text',nullable:true})),rows:result.rows}}
function safeCsv(value:Cell){let text=value===null?'':String(value);if(typeof value==='string'&&/^[\s\uFEFF]*[=+\-@\t\r\n]/.test(text))text="'"+text;return /[",\r\n]/.test(text)?`"${text.replace(/"/g,'""')}"`:text}
export function serializeModelFile(result:ModelResult,format:ModelFileFormat):{text:string;mime:string;extension:string}{const clean=validated(result);const chunks:string[]=[];let size=0;const put=(part:string)=>{size+=part.length;if(size>MAX_TEXT)throw new Error('导出文件超过 200 万字符');chunks.push(part)};let mime:string;
 if(format==='csv'){clean.columns.forEach((name,i)=>{if(i)put(',');put(safeCsv(name))});for(const row of clean.rows){put('\r\n');clean.columns.forEach((key,i)=>{if(i)put(',');put(safeCsv(row[key]))})}mime='text/csv;charset=utf-8'}
 else if(format==='json'){put('[');clean.rows.forEach((row,i)=>{if(i)put(',');put('\n  {');clean.columns.forEach((key,j)=>{if(j)put(',');put(JSON.stringify(key));put(':');put(JSON.stringify(row[key]))});put('}')});put(clean.rows.length?'\n]':']');mime='application/json;charset=utf-8'}
 else{put('<?xml version="1.0" encoding="UTF-8"?>\n<table><columns>');for(const name of clean.columns){put('<column name="');put(escapeXml(name));put('"/>')}put('</columns><rows>');for(const row of clean.rows){put('<row>');for(const name of clean.columns){const value=row[name];put('<field name="');put(escapeXml(name));put(`" type="${value===null?'null':typeof value}">`);if(value!==null)put(escapeXml(String(value)));put('</field>')}put('</row>')}put('</rows></table>');mime='application/xml;charset=utf-8'}
 return {text:chunks.join(''),mime,extension:format};
}
