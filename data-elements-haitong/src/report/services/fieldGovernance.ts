import type {TimeRole} from '../core/model';
import type {UploadField} from './uploadWorkbench';

// Stable codes, labels and property interactions follow register-dbTable-catalog.
export const STANDARD_FORMAT_OPTIONS = [
  {value:'LXDH',label:'手机号码',description:'字符型11位'},
  {value:'SFZH',label:'身份证',description:'字符型18位'},
  {value:'DATE',label:'日期',description:'YYYY-MM-DD'},
  {value:'DATETIME',label:'时间',description:'YYYY-MM-DD HH:mm:ss'},
] as const;
export const TIME_ROLE_OPTIONS = [
  {value:'timestamp',label:'时间戳',dialogLabel:'数据抽取时间',description:'用于抽取数据的增量时间'},
  {value:'business',label:'业务时间',dialogLabel:'业务发生时间',description:'记录业务实际发生的时间'},
  {value:'occurrence',label:'发生时间',dialogLabel:'轨迹发生时间',description:'记录轨迹数据实际发生的时间'},
] as const;
export function isTimeRoleCandidate(field:UploadField){
  if(field.standardField==='LXDH'||field.standardField==='SFZH')return false;
  return /^(date|datetime|timestamp)$/i.test(field.columnType)||field.standardField==='DATE'||field.standardField==='DATETIME';
}
export function timeRolesFor(field:UploadField):TimeRole[]{
  return !Array.isArray(field.timeRoles)?(field.isTimestamp?['timestamp']:[]):TIME_ROLE_OPTIONS.flatMap(item=>field.timeRoles?.includes(item.value)?[item.value]:[]);
}
export function updateReportField(fields:UploadField[],index:number,patch:Partial<UploadField>):UploadField[]{
  if(!fields[index])return fields;
  const target={...fields[index],...patch};
  let roles=patch.timeRoles??timeRolesFor(fields[index]);
  if(patch.isTimestamp!==undefined&&patch.timeRoles===undefined)roles=patch.isTimestamp?[...roles.filter(role=>role!=='timestamp'),'timestamp']:roles.filter(role=>role!=='timestamp');
  if(!isTimeRoleCandidate(target))roles=[];
  return fields.map((field,i)=>{
    const remaining=i===index?roles:timeRolesFor(field).filter(role=>!roles.includes(role));
    return {...(i===index?target:field),timeRoles:remaining,isTimestamp:remaining.includes('timestamp'),
      ...(i!==index&&patch.dictionaryRole&&field.dictionaryRole===patch.dictionaryRole?{dictionaryRole:'' as const}:{}),
      ...(i===index&&patch.primaryKey?{nullable:false}:{})};
  });
}
