import {ClockCircleOutlined} from '@ant-design/icons';
import {FieldTypeIcon,getFieldKind} from './ColumnHeader';
import type {PreviewColumn} from './modelingPreviewColumns';

export default function ModelingPreviewHeader({name,comment,type,kind:previewKind}:{name:string;comment?:string;type?:string;kind?:PreviewColumn['kind']}){
 const label=comment?.trim();
 const sqlType=type?.trim();
 const schemaKind=sqlType?getFieldKind(sqlType):null;
 const kind=previewKind==='time'?'temporal':previewKind==='other'?(schemaKind==='binary'||schemaKind==='document'?schemaKind:null):previewKind??schemaKind;
 return <div className="modeling-preview-column" title={[label,name,sqlType].filter(Boolean).join(' · ')}>
  {label&&label!==name&&<strong className="modeling-preview-column-comment">{label}</strong>}
  <span className={label&&label!==name?"modeling-preview-column-name":"modeling-preview-column-name is-primary"}>{name}</span>
  <span className="modeling-preview-column-type">
   {kind==='temporal'?<ClockCircleOutlined aria-hidden="true"/>:kind==='number'||kind==='text'?<b className={`preview-type-${kind}`} aria-hidden="true">{kind==='number'?'#':'T'}</b>:kind&&sqlType?<FieldTypeIcon type={sqlType}/>:null}
   <span>{sqlType?.toUpperCase()||'类型未提供'}</span>
  </span>
 </div>;
}
