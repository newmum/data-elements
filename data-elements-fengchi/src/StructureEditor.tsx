import {Button} from 'antd';
import {BranchesOutlined,CodeOutlined,KeyOutlined,InfoCircleOutlined} from '@ant-design/icons';
import type {TableDef} from './data';
import {createSchemaDraft,SCHEMA_TYPES,type SchemaFieldDraft} from './schemaEditing';
import './structureEditor.css';

type Props={table:TableDef;draft:SchemaFieldDraft[];error?:string;errorField?:string;onChange:(draft:SchemaFieldDraft[])=>void;onShowDdl:()=>void};
const numeric=(type:string)=>/^(TINYINT|SMALLINT|MEDIUMINT|INT|INTEGER|BIGINT|DECIMAL|NUMERIC|FLOAT|DOUBLE|REAL)$/.test(type);
const hasLength=(type:string)=>/^(CHAR|VARCHAR|DECIMAL)$/.test(type);
export default function StructureEditor({table,draft,error,errorField,onChange,onShowDdl}:Props){
 const baseline=createSchemaDraft(table);
 function change(key:string,patch:Partial<SchemaFieldDraft>){onChange(draft.map(field=>field.key===key?{...field,...patch}:field))}
 function changeType(field:SchemaFieldDraft,type:string){const textLength=/^(CHAR|VARCHAR|BINARY|VARBINARY)$/.test(type),decimal=/^(DECIMAL|NUMERIC)$/.test(type);const sameGroup=textLength&&/^(CHAR|VARCHAR|BINARY|VARBINARY)$/.test(field.type)||decimal&&/^(DECIMAL|NUMERIC)$/.test(field.type);change(field.key,{type,length:sameGroup?field.length:textLength?'255':decimal?'10,2':'',unsigned:numeric(type)&&field.unsigned})}
 return <div className="structure-panel schema-editor">
  <div className="section-toolbar"><span><BranchesOutlined/> 字段设计 <span className="schema-count">{draft.length}</span></span><Button icon={<CodeOutlined/>} onClick={onShowDdl}>查看建表语句</Button></div>
  {error&&<div className="schema-validation" role="alert"><InfoCircleOutlined/><span>{error}。请修正后应用，当前记录尚未改变。</span></div>}
  <div className="schema-grid-scroll"><table className="schema-grid" aria-label={`${table.name} 字段结构`}><colgroup><col style={{width:165}}/><col style={{width:135}}/><col style={{width:98}}/><col style={{width:66}}/><col style={{width:70}}/><col style={{width:52}}/><col style={{width:192}}/><col style={{width:205}}/></colgroup><thead><tr>{['字段名','数据类型','长度','无符号','允许空值','主键','默认值','注释'].map(label=><th key={label}>{label}</th>)}</tr></thead><tbody>{draft.map(field=>{
   const original=baseline.find(item=>item.sourceName===field.sourceName);
   const dirty=(property:keyof SchemaFieldDraft)=>original&&original[property]!==field[property]?'schema-property changed':'schema-property';
   const label=(property:string)=>`${field.sourceName} ${property}`;
   return <tr key={field.key} className={errorField===field.key?'schema-invalid-row':''}>
    <td className={dirty('name')}><div className="schema-name-cell">{field.primary?<KeyOutlined className="schema-key"/>:<span className="schema-field-dot"/>}<input aria-label={label('字段名')} title={field.sourceName==='id'?'id 是当前记录标识，暂不可重命名':undefined} disabled={field.sourceName==='id'} value={field.name} onChange={event=>change(field.key,{name:event.target.value})}/></div></td>
    <td className={dirty('type')}><select aria-label={label('数据类型')} value={field.type} onChange={event=>changeType(field,event.target.value)}>{SCHEMA_TYPES.map(type=><option key={type} value={type}>{type}</option>)}</select></td>
    <td className={dirty('length')}><input aria-label={label('长度')} placeholder={/DECIMAL|NUMERIC/.test(field.type)?'精度,小数位':'—'} disabled={!hasLength(field.type)} value={field.length} onChange={event=>change(field.key,{length:event.target.value})}/></td>
    <td className={`${dirty('unsigned')} schema-check-cell`}><input type="checkbox" aria-label={label('无符号')} checked={field.unsigned} disabled={!numeric(field.type)} onChange={event=>change(field.key,{unsigned:event.target.checked})}/></td>
    <td className={`${dirty('nullable')} schema-check-cell`}><input type="checkbox" aria-label={label('允许空值')} checked={field.nullable} disabled={field.primary||field.sourceName==='id'} onChange={event=>change(field.key,{nullable:event.target.checked})}/></td>
    <td className={`${dirty('primary')} schema-check-cell`}><input type="checkbox" aria-label={label('主键')} checked={field.primary} onChange={event=>change(field.key,{primary:event.target.checked,...(event.target.checked?{nullable:false}:{})})}/></td>
    <td className={`${dirty('defaultValue')} ${original?.hasDefault!==field.hasDefault?'changed':''}`}><div className="schema-default-cell"><input type="checkbox" aria-label={label('启用默认值')} checked={field.hasDefault} onChange={event=>change(field.key,{hasDefault:event.target.checked})}/><input aria-label={label('默认值')} placeholder={field.hasDefault?'空字符串':'未设置'} disabled={!field.hasDefault} value={field.defaultValue} onChange={event=>change(field.key,{defaultValue:event.target.value})}/></div></td>
    <td className={dirty('comment')}><input aria-label={label('注释')} value={field.comment} onChange={event=>change(field.key,{comment:event.target.value})}/></td>
   </tr>;
  })}</tbody></table></div>
 </div>;
}
