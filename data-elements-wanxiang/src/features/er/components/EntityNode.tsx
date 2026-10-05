import { memo, useEffect, useMemo } from 'react';
import { useShallow } from 'zustand/react/shallow';
import { Handle, Position, useUpdateNodeInternals } from '@xyflow/react';
import type { Node, NodeProps } from '@xyflow/react';
import { App, Dropdown, Tooltip } from 'antd';
import { useStudio, activeDiagram } from '../store';
import { visibleFields } from '../core/relations';
import { NODE_WIDTH, errorText } from '../core/model';
import { Icon } from './Icon';
import { SourceIcon } from '../../../design/Visuals';
import { engineLabel } from '../../../shared/presentation';
import { canvasSnapshotIndex, canvasRelationIndex } from '../core/canvasIndex';
export type TableFlowNode=Node<{entityId:string;dimmed:boolean},'entity'>;
export const EntityNode=memo(function EntityNode({id,data,selected}:NodeProps<TableFlowNode>){
  const {message}=App.useApp();
  const snapshot=useStudio(s=>s.data?.snapshot);
  const relationships=useStudio(s=>s.data?.relationships);
  const node=useStudio(useShallow(s=>{const n=activeDiagram(s)?.nodes.find(n=>n.entityId===id);return n?{locked:n.locked,collapsed:n.collapsed,pinnedFieldIds:n.pinnedFieldIds}:null;}));
  const fieldMode=useStudio(s=>activeDiagram(s)?.fieldMode??'key');
  const selectedRel=useStudio(s=>s.selectedRelationshipId);
  const updateInternals=useUpdateNodeInternals();
  const snapshotIndex=snapshot?canvasSnapshotIndex(snapshot):undefined;
  const entity=snapshotIndex?.entities.get(data.entityId);
  const mode=node?.collapsed?'summary':fieldMode;
  const relationIndex=relationships?canvasRelationIndex(relationships):undefined;
  const filteredRelations=relationIndex?.byEntity.get(id)??[];
  const display=useMemo(()=>entity?visibleFields(entity,filteredRelations,mode,node?.pinnedFieldIds):{fields:[],hidden:[]},[entity,relationIndex,mode,node?.pinnedFieldIds]);
  const signature=display.fields.map(f=>f.id).join('|');
  useEffect(()=>{updateInternals(id);},[id,signature,mode,updateInternals]);
  if(!entity||!snapshot||!node)return null;
  const source=snapshotIndex?.sources.get(entity.sourceId);
  const pk=new Set(entity.keys?.filter(k=>k.kind==='primary').flatMap(k=>k.fieldIds));
  const fk=new Set(filteredRelations.filter(r=>r.origin==='catalog'&&r.sourceEntityId===entity.id).flatMap(r=>r.mappings?.map(m=>m.sourceFieldId)??[]));
  const r=selectedRel?relationIndex?.byId.get(selectedRel):undefined;
  const highlighted=new Set(r?.mappings?.flatMap(m=>[m.sourceFieldId,m.targetFieldId]));
  const incident=filteredRelations.filter(r=>r.sourceEntityId===entity.id||r.targetEntityId===entity.id).length;
  const action=(key:string)=>{
    const s=useStudio.getState();
    if(key==='detail')s.selectEntity(id,true);
    if(key==='lock')s.toggleLock(id);
    if(key==='collapse')s.toggleCollapse(id);
    if(key==='focus')s.setFocus(s.focusEntityId===id?null:id);
    if(key==='expand'){try{s.addEntities(filteredRelations.filter(r=>r.sourceEntityId===id||r.targetEntityId===id).flatMap(r=>[r.sourceEntityId,r.targetEntityId]));}catch(e){message.error(errorText(e));}}
    if(key==='remove')s.removeEntities([id]);
  };
  return <section className={`entity-card ${selected?'is-selected':''} ${data.dimmed?'is-dimmed':''}`} style={{width:NODE_WIDTH}} data-testid={`entity-${entity.name}`}>
    <div className={`node-header ${node.locked?'is-locked':''}`}>
      <SourceIcon engine={source?.engine} small/>
      <div className="node-title-block"><div className="node-title">{entity.displayName??entity.name}{node.locked&&<Tooltip title="位置已固定"><span className="lock-indicator"><Icon name="lock" size={12}/></span></Tooltip>}</div><div className="node-english" title={`${entity.catalog??''}.${entity.schemaName??''}.${entity.name}`}>{entity.name}</div></div>
      <Dropdown menu={{items:[{key:'detail',label:'查看表结构',icon:<Icon name="table"/>},{key:'focus',label:'仅看相关关系',icon:<Icon name="eye"/>},{key:'expand',label:'展开一层关联',icon:<Icon name="expand"/>},{type:'divider'},{key:'lock',label:node.locked?'取消固定位置':'固定位置',icon:<Icon name={node.locked?'unlock':'lock'}/>},{key:'collapse',label:node.collapsed?'展开字段':'折叠字段',icon:<Icon name="collapse"/>},{type:'divider'},{key:'remove',label:'仅从本图移除',icon:<Icon name="close"/>,danger:true}],onClick:({key})=>action(key)}} trigger={['click']}>
        <button aria-label={`${entity.displayName??entity.name}更多操作`} className="node-more nodrag nopan" onClick={e=>e.stopPropagation()}><Icon name="more" size={16}/></button>
      </Dropdown>
    </div>
    <div className="node-fields">
      {display.fields.map(f=><div key={f.id} className={`field-row ${highlighted.has(f.id)?'field-highlighted':''}`} title={`${f.path} · ${f.nativeType}${f.comment?` · ${f.comment}`:''}`} onClick={e=>{if(!e.shiftKey)useStudio.getState().selectEntity(id,true);}}>
        <Handle type="source" position={Position.Left} id={`f:${f.id}::left`} className="field-handle"/>
        <div className={`field-key ${pk.has(f.id)?'primary-key':fk.has(f.id)?'foreign-key':''}`}>
          {pk.has(f.id)?<Tooltip title="目录声明的主键成员；不代表单列唯一"><span><Icon name="key" size={11}/></span></Tooltip>:fk.has(f.id)?<Tooltip title="目录外键字段"><span><Icon name="link" size={11}/></span></Tooltip>:<span className="field-dot"/>}
        </div>
        <span className="field-name nodrag" style={{userSelect:'text'}}>{f.path.includes('.')?f.path:f.name}</span><span className="field-type">{f.nativeType.replace(/\(.+\)/,'').toUpperCase()}</span>
        <Handle type="source" position={Position.Right} id={`f:${f.id}::right`} className="field-handle"/>
      </div>)}
      {(mode==='summary'||display.hidden.length>0)&&<div className="node-summary nodrag" onDoubleClick={()=>useStudio.getState().selectEntity(id,true)}>
        <Handle type="source" position={Position.Left} id="summary::left" className="field-handle" isConnectable={false}/>
        <button onClick={()=>mode==='summary'?(()=>{const s=useStudio.getState();if(fieldMode==='summary')s.updateView({fieldMode:'key'});if(node.collapsed)s.toggleCollapse(id);})():useStudio.getState().selectEntity(id,true)}>{mode==='summary'?`${entity.fields.length} 个字段 · 摘要模式`:`还有 ${display.hidden.length} 个字段 · 查看详情`}</button>
        <Handle type="source" position={Position.Right} id="summary::right" className="field-handle" isConnectable={false}/>
      </div>}
    </div>
    <div className="node-footer"><span className={`source-dot engine-${source?.engine??'mysql'}`}/><span>{engineLabel(source?.engine)}</span><span className="node-meta-right">{entity.fields.length} 字段 <span>·</span> {incident} 关联</span></div>
  </section>;
});
