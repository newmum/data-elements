import {Button,Checkbox,Input,Popover,Select,Table,Tag,Tooltip} from 'antd';
import {ClockCircleOutlined,KeyOutlined} from '@ant-design/icons';
import type {UploadField,UploadTable} from '../services/uploadWorkbench';
import {reportFieldIssues} from '../services/reportSchema';
import {isTimeRoleCandidate,STANDARD_FORMAT_OPTIONS,TIME_ROLE_OPTIONS,timeRolesFor} from '../services/fieldGovernance';

const fieldTypes=['varchar','char','text','int','bigint','decimal','float','double','date','datetime','timestamp','boolean'];
export default function FieldGovernanceTable({table,disabled,onChange}:{table:UploadTable;disabled:boolean;onChange:(index:number,patch:Partial<UploadField>)=>void}){
  const rows=table.fields.map((field,index)=>({...field,index}));
  return <Table size="small" rowKey="index" className="report-field-table report-governance-table" dataSource={rows} pagination={false} scroll={{x:780}}
    rowClassName={field=>reportFieldIssues(field,table.fields).length?'report-field-invalid':''}
    columns={[
      {title:'序号',width:50,align:'center',render:(_,field)=><Tooltip title={reportFieldIssues(field,table.fields).join('；')}><span>{field.index+1}</span></Tooltip>},
      {title:'字段名',width:205,render:(_,field)=>{
        const roles=timeRolesFor(field);
        return <div className="report-governance-name"><div className="report-governance-name-row">
          <Input aria-label={'字段名 '+field.index} value={field.columnName} disabled={disabled} onChange={event=>onChange(field.index,{columnName:event.target.value})}/>
          <div className="report-governance-name-actions"><Tooltip title={field.primaryKey?'取消主键':'设为主键'}><Button type="text" size="small" aria-label={'主键 '+field.index} aria-pressed={field.primaryKey} className={`report-field-property${field.primaryKey?' is-primary':''}`} disabled={disabled} icon={<KeyOutlined/>} onClick={()=>onChange(field.index,{primaryKey:!field.primaryKey})}/></Tooltip>
          {isTimeRoleCandidate(field)&&<Popover trigger="click" placement="bottomLeft" overlayClassName="report-time-property-popover" open={disabled?false:undefined} title="时间属性" content={<div className="report-time-property-content">
            <Checkbox.Group value={roles} onChange={value=>onChange(field.index,{timeRoles:value as UploadField['timeRoles']})}>
              {TIME_ROLE_OPTIONS.map(role=><Checkbox key={role.value} value={role.value}><strong>{role.dialogLabel}</strong><small>{role.description}</small></Checkbox>)}
            </Checkbox.Group>
            <label>来源时间格式<Select aria-label={'来源时间格式 '+field.index} value={field.timeFormat||(field.standardField==='DATE'||field.columnType==='date'?'ymd':'iso')} options={[{value:'iso',label:'年-月-日 时:分:秒'},{value:'ymd',label:'年-月-日'},{value:'dmy',label:'日/月/年'},{value:'mdy',label:'月/日/年'},{value:'unix-s',label:'Unix 秒'},{value:'unix-ms',label:'Unix 毫秒'}]} onChange={value=>onChange(field.index,{timeFormat:value})}/></label>
            <label>来源时区<Select aria-label={'来源时区 '+field.index} value={field.timezone||'Asia/Shanghai'} options={[{value:'Asia/Shanghai',label:'北京时间'},{value:'UTC',label:'UTC'},{value:'Asia/Tokyo',label:'东京时间'},{value:'source',label:'源值自带时区'}]} onChange={value=>onChange(field.index,{timezone:value})}/></label>
          </div>}><Tooltip title="设置时间属性"><Button type="text" size="small" aria-label={'时间属性 '+field.index} className={`report-field-property${roles.length?' is-time':''}`} icon={<ClockCircleOutlined/>}/></Tooltip></Popover>}</div>
        </div>{(field.primaryKey||roles.length>0)&&<div className="report-field-property-tags">{field.primaryKey&&<Tag color="orange">主键</Tag>}{TIME_ROLE_OPTIONS.filter(role=>roles.includes(role.value)).map(role=><Tag key={role.value} color={role.value==='timestamp'?'green':role.value==='business'?'blue':'default'}>{role.label}</Tag>)}</div>}</div>;
      }},
      {title:'字段中文名',width:175,render:(_,field)=><div className="report-governance-comment"><Input aria-label={'字段中文名 '+field.index} value={field.columnComment} disabled={disabled} placeholder="填写规范字段中文名" onChange={event=>onChange(field.index,{columnComment:event.target.value})}/>{field.standardField&&<Tag>{STANDARD_FORMAT_OPTIONS.find(option=>option.value===field.standardField)?.description}</Tag>}</div>},
      {title:'字段类型',width:115,render:(_,field)=><div className="report-governance-type"><Popover trigger="click" title="字段类型" open={disabled?false:undefined} content={<Select aria-label={'字段类型 '+field.index} value={field.columnType} style={{width:150}} options={fieldTypes.map(value=>({value,label:value}))} onChange={value=>onChange(field.index,{columnType:value,columnLength:['varchar','char','text'].includes(value)?4000:undefined})}/>}><Button type="text" aria-label={'修改字段类型 '+field.index} disabled={disabled}>{field.columnType}{field.columnLength&&['varchar','char'].includes(field.columnType)?`(${field.columnLength})`:''}</Button></Popover></div>},
      {title:'是否为空',width:78,align:'center',render:(_,field)=><Button type="text" aria-label={'允许空值 '+field.index} disabled={disabled||field.primaryKey} onClick={()=>onChange(field.index,{nullable:!field.nullable})}>{field.nullable?'是':'否'}</Button>},
      ...(table.businessType==='字典表'?[{title:'字典字段',width:130,render:(_:unknown,field:UploadField&{index:number})=><Select<NonNullable<UploadField['dictionaryRole']>> aria-label={'字典字段 '+field.index} disabled={disabled} value={field.dictionaryRole||''} options={[{value:'',label:'普通字段'},{value:'code',label:'字典代码'},{value:'label',label:'字典名称'}]} onChange={value=>onChange(field.index,{dictionaryRole:value})}/>}]:[]),
      {title:'统一格式',width:145,fixed:'right',render:(_,field)=><Select<NonNullable<UploadField['standardField']>> aria-label={'统一格式 '+field.index} className="report-standard-format-select" popupClassName="report-standard-format-popup" popupMatchSelectWidth={300} disabled={disabled} value={field.standardField||undefined} placeholder="请选择格式" allowClear onClear={()=>onChange(field.index,{standardField:''})} onChange={value=>onChange(field.index,{standardField:value||''})} options={[{value:'',label:'不设置'},...STANDARD_FORMAT_OPTIONS]} optionRender={option=><div className="report-standard-format-option"><span>{option.label}</span><small>{STANDARD_FORMAT_OPTIONS.find(item=>item.value===option.value)?.description}</small></div>}/>},
    ]}/>;
}
