import { Alert, Button, Table, type TableProps } from 'antd';
import { usePreviewTableOrder } from './usePreviewTableOrder';

type Field<T>={kind:'text'|'number'|'time';value:(row:T)=>unknown};
/** Complete editor drafts. Render callbacks retain the source index, including after sorting and paging. */
export function PreviewTable<T extends object>({dataSource=[],columns,fields,...props}:TableProps<T>&{fields:Record<string,Field<T>>}) {
 const originals=dataSource.map((row,index)=>({id:String(typeof props.rowKey==='function'?props.rowKey(row,index):props.rowKey?(row as any)[props.rowKey]??index:(row as any).id??index),row,index}));
 const kinds=Object.fromEntries(Object.entries(fields).map(([key,definition])=>[key,definition.kind]));
 const remote=usePreviewTableOrder(originals,kinds,(record,field)=>fields[field].value(record.row),Object.keys(fields)[0]);
 const indices=new Map(originals.map(record=>[record.row,record.index]));
 const decorated=columns?.map(column=>{
  const key=String(column.key??('dataIndex' in column?column.dataIndex:''));
  return {...column,sorter:!!fields[key],sortOrder:remote.order.sort.sortField===key?(remote.order.sort.sortDir==='asc'?'ascend' as const:'descend' as const):null,
   render:column.render?((value:unknown,row:T)=>column.render!(value,row,indices.get(row)!)):undefined};
 });
 return <>{remote.error&&<Alert type="error" showIcon title={remote.error instanceof Error?remote.error.message:'预览排序失败'} action={<Button onClick={remote.refresh}>重试</Button>}/>}<Table<T> {...props}
  rowKey={(row,index)=>typeof props.rowKey==='function'?props.rowKey(row,indices.get(row)):props.rowKey?String((row as any)[props.rowKey]):String(indices.get(row)??index)}
  loading={remote.loading} dataSource={remote.rows.map(record=>record.row)} columns={decorated}
  onChange={(pagination,filters,sorter,extra)=>{if(extra.action==='sort')remote.order.change(sorter);props.onChange?.(pagination,filters,sorter,extra);}}
  pagination={{current:remote.page,pageSize:remote.size,total:remote.total,showSizeChanger:true,pageSizeOptions:[20,50,100],onChange:remote.pagination.onChange}}/></>;
}
