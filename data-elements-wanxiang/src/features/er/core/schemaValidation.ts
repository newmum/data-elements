import rawSchema from './metadata-import.schema.json';
/** Evaluates only the keywords used by our bundled 1.0 metadata schema.
 * No external references, code evaluation, coercion, or removal of unknown fields.
 * This is not advertised as a general-purpose JSON Schema implementation.
 */
interface Schema {
  $ref?: string; $defs?: Record<string,Schema>; type?: string; const?: unknown;
  enum?: unknown[]; anyOf?: Schema[]; properties?: Record<string,Schema>;
  additionalProperties?: boolean; required?: string[]; items?: Schema;
  minItems?: number; minLength?: number; maxLength?: number;
  minimum?: number; maximum?: number; format?: string;
}
const root=rawSchema as Schema;
export function metadataShapeErrors(value:unknown):string[]{
  const errors:string[]=[];
  function check(v:unknown,s:Schema,path:string,out:string[]):boolean {
    const start=out.length;
    if(out.length>=100)return false;
    if(s.$ref){
      const key=s.$ref.replace('#/$defs/','');const resolved=root.$defs?.[key];
      if(!s.$ref.startsWith('#/$defs/')||!resolved)throw new Error('内置元数据契约的引用无效。');
      return check(v,resolved,path,out);
    }
    if(s.anyOf&&!s.anyOf.some(branch=>check(v,branch,path,[])))out.push(`${path}：类型或取值不符合元数据契约。`);
    if('const' in s&&v!==s.const)out.push(`${path}：必须为 ${String(s.const)}。`);
    if(s.enum&&!s.enum.includes(v))out.push(`${path}：取值必须为 ${s.enum.join(' / ')}。`);
    const matches=!s.type||({null:v===null,object:v!==null&&typeof v==='object'&&!Array.isArray(v),array:Array.isArray(v),string:typeof v==='string',boolean:typeof v==='boolean',integer:typeof v==='number'&&Number.isInteger(v),number:typeof v==='number'&&Number.isFinite(v)}[s.type]??false);
    if(!matches){out.push(`${path}：必须为 ${s.type} 类型。`);return false;}
    if(typeof v==='string'){
      const length=Array.from(v).length;
      if(s.minLength!==undefined&&length<s.minLength)out.push(`${path}：不能为空。`);
      if(s.maxLength!==undefined&&length>s.maxLength)out.push(`${path}：长度超过 ${s.maxLength}。`);
      if(s.format==='date-time'&&!Number.isFinite(Date.parse(v)))out.push(`${path}：日期无效。`);
    }
    if(typeof v==='number'){
      if(s.minimum!==undefined&&v<s.minimum)out.push(`${path}：不能小于 ${s.minimum}。`);
      if(s.maximum!==undefined&&v>s.maximum)out.push(`${path}：不能大于 ${s.maximum}。`);
    }
    if(Array.isArray(v)){
      if(s.minItems!==undefined&&v.length<s.minItems)out.push(`${path}：至少需要 ${s.minItems} 项。`);
      if(s.items)v.forEach((x,i)=>check(x,s.items!,`${path}[${i}]`,out));
    }else if(v!==null&&typeof v==='object'){
      const obj=v as Record<string,unknown>;
      for(const k of s.required??[])if(!(k in obj))out.push(`${path}.${k}：缺少必填属性。`);
      for(const [k,x]of Object.entries(obj)){
        if(s.properties?.[k])check(x,s.properties[k],`${path}.${k}`,out);
        else if(s.additionalProperties===false)out.push(`${path}.${k}：未知属性，请先映射为统一元数据契约。`);
      }
    }
    return out.length===start;
  }
  check(value,root,'metadata',errors);return errors;
}
