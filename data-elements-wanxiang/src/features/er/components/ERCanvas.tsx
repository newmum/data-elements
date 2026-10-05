import { metadataWritable as canWrite } from '../../../services/metadata';
import { STUDIO_ACTION_EVENT } from '../core/navigation';
import { hasBlockingLayer, isEditingTarget } from '../core/interaction';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { applyNodeChanges, Background, BackgroundVariant, ConnectionMode, MiniMap, ReactFlow, ReactFlowProvider, useReactFlow, useViewport, useNodesInitialized } from '@xyflow/react';
import type { Connection, NodeChange } from '@xyflow/react';
import { App, Button, Checkbox, Dropdown, Empty, Popover, Select, Tooltip } from 'antd';
import type { Position as Point } from '../types/domain';
import { activeDiagram, useStudio } from '../store';
import { EntityNode } from './EntityNode';
import type { TableFlowNode } from './EntityNode';
import { RelationshipEdge } from './RelationshipEdge';
import type { RelationFlowEdge } from './RelationshipEdge';
import { Icon } from './Icon';
import { multiplicityLabel, relatedEntityIds, relationshipKind, visibleFields } from '../core/relations';
import { CANVAS_PAGE_SIZE, NODE_WIDTH, errorText, uid } from '../core/model';
import { alignNodes } from '../core/alignment';
import { nodeHeight, preserveLockedNodes } from '../core/layout';
import { clusteredLayout } from '../core/clusterLayout';
import { canvasSnapshotIndex, canvasRelationIndex } from '../core/canvasIndex';
const nodeTypes={entity:EntityNode};const edgeTypes={relationship:RelationshipEdge};
const relationFilterOptions=[{value:'observed',label:'数据库外键'},{value:'confirmed',label:'已确认逻辑关系'},{value:'suggested',label:'待确认推荐'}];
function Canvas(){
  const {message,modal}=App.useApp();
  const data=useStudio(s=>s.data)!;const diagram=useStudio(activeDiagram)!;const theme=useStudio(s=>s.theme);const selectedId=useStudio(s=>s.selectedEntityId);const selectedRelId=useStudio(s=>s.selectedRelationshipId);const focusId=useStudio(s=>s.focusEntityId);const panMode=useStudio(s=>s.panMode);const minimap=useStudio(s=>s.minimap);const past=useStudio(s=>s.past.length);const future=useStudio(s=>s.future.length);const libraryOpen=useStudio(s=>s.libraryOpen);
  const {screenToFlowPosition,fitView,setCenter,getNodes,zoomIn,zoomOut,setViewport}=useReactFlow<TableFlowNode,RelationFlowEdge>();
  const nodesInitialized=useNodesInitialized();
  const initialFocus=useRef<string|null>(null);
  const viewport=useViewport();const container=useRef<HTMLDivElement>(null);const[layoutBusy,setLayoutBusy]=useState(false);const[isDropTarget,setDropTarget]=useState(false);const[selectedIds,setSelectedIds]=useState<string[]>([]);const[space,setSpace]=useState(false);
  const page=useStudio(s=>s.canvasGroup),setPage=useStudio(s=>s.setCanvasGroup);const pageCount=Math.max(1,Math.ceil(diagram.nodes.length/CANVAS_PAGE_SIZE));
  const visibleDiagramNodes=useMemo(()=>diagram.nodes.slice(page*CANVAS_PAGE_SIZE,(page+1)*CANVAS_PAGE_SIZE),[diagram.nodes,page]);
  const layoutRequest=useRef('');const workerRef=useRef<Worker|null>(null);
  useEffect(()=>{setSelectedIds(selectedId?[selectedId]:[]);},[selectedId]);
  useEffect(()=>{if(page>=pageCount)setPage(pageCount-1);},[page,pageCount]);
  useEffect(()=>{const target=focusId||selectedId||data.relationships.find(r=>r.id===selectedRelId)?.sourceEntityId;if(!target)return;const index=diagram.nodes.findIndex(n=>n.entityId===target);if(index>=0&&Math.floor(index/CANVAS_PAGE_SIZE)!==page)setPage(Math.floor(index/CANVAS_PAGE_SIZE));},[focusId,selectedId,selectedRelId,diagram.nodes,data.relationships,page]);
  useEffect(()=>{if(pageCount===1)return;let next=0;const frame=requestAnimationFrame(()=>{next=requestAnimationFrame(()=>void fitView({padding:.12,duration:250,maxZoom:1,minZoom:.15}));});return()=>{cancelAnimationFrame(frame);cancelAnimationFrame(next);};},[page,pageCount,fitView]);
  const nodeIds=useMemo(()=>new Set(visibleDiagramNodes.map(n=>n.entityId)),[visibleDiagramNodes]);
  const relations=useMemo(()=>data.relationships.filter(r=>nodeIds.has(r.sourceEntityId)&&nodeIds.has(r.targetEntityId)&&diagram.relationFilter.includes(r.reviewStatus as 'observed'|'confirmed'|'suggested')&&!diagram.hiddenRelationshipIds.includes(r.id)),[data.relationships,nodeIds,diagram.relationFilter,diagram.hiddenRelationshipIds]);
  const focusIds=useMemo(()=>focusId?relatedEntityIds(focusId,relations):null,[focusId,relations]);
  const projectedNodes:TableFlowNode[]=useMemo(()=>visibleDiagramNodes.map(n=>({id:n.entityId,type:'entity',position:n.position,dragHandle:'.node-header',draggable:canWrite()&&!n.locked,selected:selectedIds.includes(n.entityId),hidden:!!focusIds&&!focusIds.has(n.entityId),data:{entityId:n.entityId,dimmed:false}})),[visibleDiagramNodes,selectedIds,focusIds]);
  const [nodes,setNodes]=useState<TableFlowNode[]>(projectedNodes);
  const dragPositions=useRef<Record<string,Point>>({});
  useEffect(()=>{setNodes(projectedNodes);},[projectedNodes]);
  const snapshotIndex=useMemo(()=>canvasSnapshotIndex(data.snapshot),[data.snapshot]);
  const relationIndex=useMemo(()=>canvasRelationIndex(data.relationships),[data.relationships]);
  const diagramNodes=useMemo(()=>new Map(visibleDiagramNodes.map(n=>[n.entityId,n])),[visibleDiagramNodes]);
  const edges:RelationFlowEdge[]=useMemo(()=>relations.map(r=>{
    const source=diagramNodes.get(r.sourceEntityId)!;const target=diagramNodes.get(r.targetEntityId)!;
    const sourceEntity=snapshotIndex.entities.get(r.sourceEntityId);const mapping=r.mappings?.find(m=>!/^(tenant_id|org_scope_id|partition_key)$/.test(sourceEntity?.fields.find(f=>f.id===m.sourceFieldId)?.name??''))??r.mappings?.[0];const self=source.entityId===target.entityId;
    const sourceSide=self?'left':source.position.x<=target.position.x?'right':'left';const targetSide=self?'left':sourceSide==='right'?'left':'right';
    function handle(entityId:string,fieldId:string|undefined,side:string){const e=snapshotIndex.entities.get(entityId)!;const n=diagramNodes.get(entityId)!;const mode=n.collapsed?'summary':diagram.fieldMode;const display=visibleFields(e,relationIndex.byEntity.get(entityId)||[],mode,n.pinnedFieldIds);return display.fields.some(f=>f.id===fieldId)?`f:${fieldId}::${side}`:`summary::${side}`;}
    const kind=relationshipKind(r),count=r.mappings?.length??1;const prefix=kind==='catalog'?'外键':kind==='logical'?'逻辑':'待确认';
    return {id:`edge-${r.id}`,type:'relationship',source:r.sourceEntityId,target:r.targetEntityId,sourceHandle:handle(source.entityId,mapping?.sourceFieldId,sourceSide),targetHandle:handle(target.entityId,mapping?.targetFieldId,targetSide),selected:selectedRelId===r.id,hidden:!!focusIds&&(!focusIds.has(r.sourceEntityId)||!focusIds.has(r.targetEntityId)),data:{relationshipId:r.id,kind,showLabel:diagram.showEdgeLabels!==false,label:`${prefix}${count>1?` · ${count} 字段`:''}`,self,multiplicity:multiplicityLabel(r),sourceMax:r.sourcesPerTarget?.max??'unknown',targetMax:r.targetsPerSource?.max??'unknown',sourceMin:r.sourcesPerTarget?.min??'unknown',targetMin:r.targetsPerSource?.min??'unknown',dimmed:!!selectedRelId&&selectedRelId!==r.id}};
  }),[relations,diagramNodes,diagram.fieldMode,diagram.showEdgeLabels,data.snapshot,snapshotIndex,relationIndex,selectedRelId,focusIds]);
  const onChanges=useCallback((changes:NodeChange<TableFlowNode>[])=>{
    setNodes(previous=>applyNodeChanges(changes,previous));
    const positions:Record<string,Point>={};
    for(const c of changes)if(c.type==='position'&&c.position)positions[c.id]=c.position;
    if(useStudio.getState().dragging)Object.assign(dragPositions.current,positions);
    else if(Object.keys(positions).length)useStudio.getState().updatePositions(positions,true);
    const selections=changes.filter(c=>c.type==='select');
    if(selections.length)setSelectedIds(previous=>{const ids=new Set(previous);for(const c of selections)if(c.type==='select')c.selected?ids.add(c.id):ids.delete(c.id);return [...ids];});
  },[]);
  const startDrag=useCallback(()=>{dragPositions.current={};useStudio.getState().startDrag();},[]);
  const stopDrag=useCallback(()=>{const state=useStudio.getState();if(Object.keys(dragPositions.current).length)state.updatePositions(dragPositions.current,false);dragPositions.current={};state.endDrag();},[]);
  const onConnect=useCallback((c:Connection)=>{
    if(!canWrite())return;
    const field=(value:string|null)=>value?.replace(/::(?:left|right)$/,'').replace(/^f:/,'');
    if(!c.source||!c.target||!c.sourceHandle||!c.targetHandle||!c.sourceHandle.startsWith('f:')||!c.targetHandle.startsWith('f:'))return;
    useStudio.getState().openEditor({sourceEntityId:c.source,targetEntityId:c.target,sourceFieldId:field(c.sourceHandle),targetFieldId:field(c.targetHandle)});
  },[]);
  const fit=useCallback(()=>void fitView({padding:.12,duration:300,maxZoom:1,minZoom:.2}),[fitView]);
  useEffect(()=>{
    if(!nodesInitialized||!focusId||initialFocus.current===focusId)return;
    const node=getNodes().find(item=>item.id===focusId);
    if(!node)return;
    initialFocus.current=focusId;
    void setCenter(node.position.x+NODE_WIDTH/2,node.position.y+(node.measured?.height??260)/2,{zoom:.9,duration:250});
  },[nodesInitialized,focusId,getNodes,setCenter]);
  useEffect(()=>{if(!selectedId)return;let next=0;const frame=requestAnimationFrame(()=>{next=requestAnimationFrame(()=>{const node=getNodes().find(item=>item.id===selectedId);if(node)void setCenter(node.position.x+NODE_WIDTH/2,node.position.y+(node.measured?.height??260)/2,{zoom:.85,duration:250});});});return()=>{cancelAnimationFrame(frame);cancelAnimationFrame(next);};},[selectedId,page,getNodes,setCenter]);
  useEffect(()=>{
    const handler=(event:Event)=>{const id=(event as CustomEvent<string>).detail;requestAnimationFrame(()=>{const n=getNodes().find(n=>n.id===id);if(n)setCenter(n.position.x+NODE_WIDTH/2,n.position.y+(n.measured?.height??260)/2,{zoom:Math.max(viewport.zoom,.85),duration:350});});};
    window.addEventListener('wanxiang-focus-entity',handler);return()=>window.removeEventListener('wanxiang-focus-entity',handler);
  },[getNodes,setCenter,viewport.zoom]);
  useEffect(()=>{
    const isInput=(e:KeyboardEvent)=>isEditingTarget(e.target);
    const down=(e:KeyboardEvent)=>{if(e.defaultPrevented||isInput(e)||useStudio.getState().editor||hasBlockingLayer())return;if(e.code==='Space'){e.preventDefault();setSpace(true);}if(e.key==='Delete'||e.key==='Backspace'){if(!selectedIds.length)return;e.preventDefault();modal.confirm({title:`从当前图移除 ${selectedIds.length} 个实体？`,icon:null,content:'仅移除画布节点，不会删除数据库表或工作区关系。可通过撤销恢复。',okText:'移除',cancelText:'取消',onOk:()=>useStudio.getState().removeEntities(selectedIds)});}if(e.key.toLowerCase()==='f'&&!e.ctrlKey&&!e.metaKey){e.preventDefault();fit();}};
    const up=(e:KeyboardEvent)=>{if(e.code==='Space')setSpace(false);};const blur=()=>setSpace(false);
    window.addEventListener('keydown',down);window.addEventListener('keyup',up);window.addEventListener('blur',blur);
    return()=>{window.removeEventListener('keydown',down);window.removeEventListener('keyup',up);window.removeEventListener('blur',blur);};
  },[selectedIds,modal,fit]);
  useEffect(()=>()=>{layoutRequest.current='';workerRef.current?.terminate();},[]);
  async function autoLayout(mode:'RIGHT'|'DOWN'|'SOURCE'|'SELECTED'){
    const state=useStudio.getState(),current=activeDiagram(state),snapshot=state.data?.snapshot;
    if(!current?.nodes.length||!snapshot)return;
    const currentVisibleNodes=current.nodes.slice(page*CANVAS_PAGE_SIZE,(page+1)*CANVAS_PAGE_SIZE);
    const visibleIds=new Set(currentVisibleNodes.map(node=>node.entityId));
    const currentRelations=(state.data?.relationships??[]).filter(relation=>visibleIds.has(relation.sourceEntityId)&&visibleIds.has(relation.targetEntityId)&&current.relationFilter.includes(relation.reviewStatus as 'observed'|'confirmed'|'suggested')&&!current.hiddenRelationshipIds.includes(relation.id));
    const layoutSignature=(view:typeof current)=>JSON.stringify({id:view.id,fieldMode:view.fieldMode,nodes:view.nodes.slice(page*CANVAS_PAGE_SIZE,(page+1)*CANVAS_PAGE_SIZE).map(node=>[node.entityId,node.position.x,node.position.y,node.locked,node.collapsed]),relationFilter:view.relationFilter,hiddenRelationshipIds:view.hiddenRelationshipIds});
    const beforeLayout=layoutSignature(current);const requestId=uid('layout');layoutRequest.current=requestId;setLayoutBusy(true);
    const heights:Record<string,number>={};
    const entities=new Map(snapshot.entities.map(entity=>[entity.id,entity]));
    const incidentRelations=canvasRelationIndex(state.data?.relationships??[]).byEntity;
    const layoutNodes=currentVisibleNodes.map(n=>({...n,locked:n.locked||(mode==='SELECTED'&&!selectedIds.includes(n.entityId))}));
    const children=currentVisibleNodes.map(n=>{
      const e=entities.get(n.entityId)!;const shown=visibleFields(e,incidentRelations.get(e.id)??[],n.collapsed?'summary':current.fieldMode,n.pinnedFieldIds);
      const isSummary=n.collapsed||current.fieldMode==='summary';
      // Measure from the requested display mode, not the previous React Flow size.
      const h=nodeHeight(shown.fields.length,isSummary)+(!isSummary&&shown.hidden.length?28:0)+20;
      heights[e.id]=h;
      return{id:e.id,width:NODE_WIDTH,height:h};
    });
    const links=currentRelations.filter(relation=>relation.sourceEntityId!==relation.targetEntityId).map(relation=>({source:relation.sourceEntityId,target:relation.targetEntityId}));
    const layoutEdges=links.map((link,i)=>({id:`relation-${i}`,sources:[link.source],targets:[link.target]}));
    try{
      let proposals:Record<string,Point>;
      if(mode==='SOURCE'){
        proposals={};let offsetY=0;for(const source of snapshot.sources){const group=children.filter(child=>entities.get(child.id)?.sourceId===source.id);if(!group.length)continue;const ids=new Set(group.map(child=>child.id));const positions=clusteredLayout(group,links.filter(link=>ids.has(link.source)&&ids.has(link.target)));for(const child of group)proposals[child.id]={x:positions[child.id].x,y:positions[child.id].y+offsetY};offsetY+=Math.max(...group.map(child=>positions[child.id].y+child.height))+160;}
      }else{
        try{
          proposals=await new Promise<Record<string,Point>>((resolve,reject)=>{
            const g=globalThis as typeof globalThis&{__ER_CREATE_LAYOUT_WORKER__?:()=>Worker};
            const worker=g.__ER_CREATE_LAYOUT_WORKER__?g.__ER_CREATE_LAYOUT_WORKER__():new Worker(new URL('../workers/layout.worker.ts',import.meta.url),{type:'module'});
            workerRef.current=worker;const timer=setTimeout(()=>{worker.terminate();reject(new Error('布局计算超时'));},20000);
            worker.onmessage=(e:MessageEvent<{id:string;positions:Record<string,Point>;error?:string}>)=>{if(e.data.id!==requestId)return;clearTimeout(timer);worker.terminate();workerRef.current=null;e.data.error?reject(new Error(e.data.error)):resolve(e.data.positions);};
            worker.onerror=(event)=>{clearTimeout(timer);worker.terminate();workerRef.current=null;reject(new Error(event.message||'布局 Worker 无法启动'));};
            worker.postMessage({id:requestId,direction:mode==='DOWN'?'DOWN':'RIGHT',children,edges:layoutEdges});
          });
        }catch(e){if(layoutRequest.current!==requestId)return;message.warning(`ELK 布局暂不可用，已使用按节点尺寸排列的备用布局：${errorText(e)}`);proposals=clusteredLayout(children,links,undefined,mode==='DOWN'?'DOWN':'RIGHT');}
      }
      if(layoutRequest.current!==requestId)return;
      const latest=activeDiagram(useStudio.getState());
      if(!latest||layoutSignature(latest)!==beforeLayout){message.info('计算期间画布内容已变化，本次布局未覆盖你的操作。请重新整理。');return;}
      const positioned=preserveLockedNodes(layoutNodes,proposals,heights).map(n=>({...n,locked:current.nodes.find(x=>x.entityId===n.entityId)?.locked??false}));
      const changed=new Map(positioned.map(n=>[n.entityId,n]));
      useStudio.getState().updateView({nodes:current.nodes.map(n=>changed.get(n.entityId)||n)});requestAnimationFrame(()=>requestAnimationFrame(fit));message.success('当前组布局已整理，关联表与独立表已分区且保留间距。');
    }finally{if(layoutRequest.current===requestId)setLayoutBusy(false);}
  }
  const toolbarIcon=(name:string,label:string,action:()=>void,disabled=false,active=false)=><Tooltip title={label}><Button type="text" aria-label={label} disabled={disabled} className={active?'tool-active':''} icon={<Icon name={name} size={17}/>} onClick={action}/></Tooltip>;
  const changePage=(next:number)=>{useStudio.getState().setFocus(null);useStudio.getState().selectEntity(null);useStudio.setState({selectedRelationshipId:null});useStudio.getState().setPanel(null);setSelectedIds([]);setPage(next);};
  return <div className={`er-canvas ${isDropTarget?'drop-target':''}`} ref={container} data-testid="er-canvas" onDragOver={e=>{if(e.dataTransfer.types.includes('application/wanxiang-entity')){e.preventDefault();e.dataTransfer.dropEffect='copy';setDropTarget(true);}}} onDragLeave={e=>{if(!container.current?.contains(e.relatedTarget as Node|null))setDropTarget(false);}} onDrop={e=>{e.preventDefault();setDropTarget(false);const id=e.dataTransfer.getData('application/wanxiang-entity');if(id)try{useStudio.getState().addEntities([id],screenToFlowPosition({x:e.clientX,y:e.clientY}));}catch(error){message.error(errorText(error));}}}>
    <ReactFlow<TableFlowNode,RelationFlowEdge>
      nodes={nodes} edges={edges} nodeTypes={nodeTypes} edgeTypes={edgeTypes} colorMode={theme}
      nodesConnectable={canWrite()} onNodesChange={onChanges} onConnect={onConnect} connectionMode={ConnectionMode.Loose}
      onNodeDragStart={startDrag} onNodeDragStop={stopDrag}
      onSelectionDragStart={startDrag} onSelectionDragStop={stopDrag}
      onNodeClick={(e,node)=>{if(!e.shiftKey)useStudio.getState().selectEntity(node.id,true);}}
      onEdgeClick={(_,edge)=>{if(edge.data)useStudio.getState().selectRelationship(edge.data.relationshipId);}}
      onPaneClick={()=>{useStudio.getState().selectEntity(null);useStudio.setState({selectedRelationshipId:null});useStudio.getState().setPanel(null);setSelectedIds([]);}}
      defaultViewport={{x:diagram.viewport?.x??0,y:diagram.viewport?.y??0,zoom:diagram.viewport?.zoom??1}} fitView={pageCount>1||diagram.version<=1} fitViewOptions={{padding:.14,maxZoom:1,minZoom:.15}}
      onMoveEnd={(_,view)=>useStudio.getState().setViewport(view)}
      minZoom={.15} maxZoom={1.8} deleteKeyCode={null} multiSelectionKeyCode="Shift" selectionOnDrag={!panMode&&!space} panOnDrag={panMode||space?true:[1,2]} panActivationKeyCode="Space" selectionKeyCode="Shift"
      onlyRenderVisibleElements snapToGrid={diagram.snapToGrid!==false} snapGrid={[12,12]} zoomOnDoubleClick={false} connectionRadius={22} proOptions={{hideAttribution:true}}
    >
      {diagram.showGrid!==false&&<Background variant={BackgroundVariant.Dots} gap={24} size={1} color="var(--canvas-dot)"/>}
      {minimap&&<MiniMap position="bottom-right" style={{width:160,height:102,background:'var(--surface)',bottom:30,right:12}} nodeColor="var(--minimap-node)" maskColor="var(--minimap-mask)" pannable zoomable/>}
    </ReactFlow>
    <div className="canvas-tools" role="toolbar" aria-label="画布工具栏">
      {!libraryOpen&&toolbarIcon('panels','展开对象库',()=>useStudio.getState().setLibraryOpen(true))}
      <div className="tool-group">{toolbarIcon('cursor','选择工具',()=>useStudio.getState().setPanMode(false),false,!panMode)}{toolbarIcon('hand','平移工具（或按住空格）',()=>useStudio.getState().setPanMode(true),false,panMode)}</div>
      <span className="tool-divider"/>
      <Dropdown trigger={['click']} menu={{items:[{key:'RIGHT',label:'从左到右'},{key:'DOWN',label:'从上到下'},{key:'SOURCE',label:'按数据源分组'},{key:'SELECTED',label:`仅整理选中实体（${selectedIds.length}）`,disabled:!selectedIds.length}],onClick:({key})=>void autoLayout(key as 'RIGHT'|'DOWN'|'SOURCE'|'SELECTED')}}><Button type="text" loading={layoutBusy} disabled={!nodes.length} icon={<Icon name="layout" size={16}/>}>自动布局<Icon name="down" size={11}/></Button></Dropdown>
      <Dropdown trigger={['click']} menu={{selectable:true,selectedKeys:[diagram.fieldMode],items:[{key:'key',label:'关键字段'},{key:'all',label:'全部字段（每节点最多 12 行）'},{key:'summary',label:'仅表摘要'}],onClick:({key})=>{if(key===diagram.fieldMode)return;useStudio.getState().updateView({fieldMode:key as 'key'|'all'|'summary'});void autoLayout('RIGHT');}}}><Button type="text" icon={<Icon name="table" size={15}/>}>{diagram.fieldMode==='key'?'关键字段':diagram.fieldMode==='all'?'全部字段':'仅表摘要'}<Icon name="down" size={11}/></Button></Dropdown>
      <Popover title="显示的关系类型" trigger="click" content={<div className="filter-popover"><Checkbox.Group options={relationFilterOptions} value={diagram.relationFilter} onChange={v=>useStudio.getState().updateView({relationFilter:v as ('observed'|'confirmed'|'suggested')[]})}/><p>仅影响当前图的显示，不改变关系状态。</p></div>}>{toolbarIcon('filter','筛选关系',()=>{})}</Popover>
      <span className="tool-divider"/>{toolbarIcon('undo','撤销画布操作（Ctrl/Cmd + Z）',()=>useStudio.getState().undo(),!past)}{toolbarIcon('redo','重做画布操作',()=>useStudio.getState().redo(),!future)}
      <span className="tool-divider"/>
      <Popover title="画布显示" trigger="click" content={<div className="canvas-settings"><Checkbox checked={diagram.showGrid!==false} onChange={e=>useStudio.getState().updateView({showGrid:e.target.checked})}>显示网格</Checkbox><Checkbox checked={diagram.snapToGrid!==false} onChange={e=>useStudio.getState().updateView({snapToGrid:e.target.checked})}>拖动时吸附网格</Checkbox><Checkbox checked={diagram.showEdgeLabels!==false} onChange={e=>useStudio.getState().updateView({showEdgeLabels:e.target.checked})}>显示关系标签</Checkbox><Checkbox checked={minimap} onChange={e=>useStudio.getState().setMinimap(e.target.checked)}>显示缩略图</Checkbox><p>网格与标签设置随当前关系图保存。</p></div>}><Button type="text" aria-label="画布显示设置" icon={<Icon name="settings" size={17}/>}/></Popover>
      {toolbarIcon('expand','专注画布',()=>window.dispatchEvent(new CustomEvent(STUDIO_ACTION_EVENT,{detail:'presentation'})))}
    </div>
    {selectedIds.length>1&&<div className="selection-toolbar" role="toolbar" aria-label="选中实体操作"><strong>已选 {selectedIds.length} 项</strong><Dropdown trigger={['click']} menu={{items:[{key:'left',label:'左对齐'},{key:'top',label:'顶对齐'},{key:'horizontal',label:'水平等距分布',disabled:selectedIds.filter(id=>!diagram.nodes.find(n=>n.entityId===id)?.locked).length<3}],onClick:({key})=>{const positions=alignNodes(diagram.nodes,selectedIds,key as 'left'|'top'|'horizontal');if(Object.keys(positions).length)useStudio.getState().updatePositions(positions,true);else message.info('至少选择两个未固定的实体；等距分布需要三个。');}}}><Button type="text" size="small" icon={<Icon name="layout" size={14}/>}>对齐<Icon name="down" size={10}/></Button></Dropdown><Tooltip title="固定选中实体的位置"><Button type="text" size="small" aria-label="固定选中实体" icon={<Icon name="lock" size={15}/>} onClick={()=>useStudio.getState().setLocked(selectedIds,true)}/></Tooltip><Tooltip title="取消固定"><Button type="text" size="small" aria-label="取消固定选中实体" icon={<Icon name="unlock" size={15}/>} onClick={()=>useStudio.getState().setLocked(selectedIds,false)}/></Tooltip><Button type="text" size="small" danger icon={<Icon name="close" size={14}/>} onClick={()=>modal.confirm({title:`从当前图移除 ${selectedIds.length} 个实体？`,icon:null,content:'不会删除元数据或工作区关系，可通过撤销恢复。',okText:'从本图移除',cancelText:'取消',onOk:()=>{useStudio.getState().removeEntities(selectedIds);setSelectedIds([]);}})}>移除</Button></div>}
    <div className="canvas-badge"><span className="tiny-grid"><Icon name="table" size={13}/></span>{nodes.filter(n=>!n.hidden).length}{pageCount>1?` / ${diagram.nodes.length}`:''} 个实体<span className="count-separator"/> {edges.filter(e=>!e.hidden).length} 条关系</div>
    {pageCount>1&&<div className="canvas-page-controls" aria-label="画布分组浏览"><Button size="small" disabled={page===0} onClick={()=>changePage(page-1)}>上一组</Button><Select size="small" aria-label="选择画布分组" value={page} options={Array.from({length:pageCount},(_,index)=>({value:index,label:`第 ${index+1} / ${pageCount} 组`}))} onChange={changePage}/><Button size="small" disabled={page>=pageCount-1} onClick={()=>changePage(page+1)}>下一组</Button></div>}
    {!!focusId&&<div className="focus-banner"><Icon name="eye" size={14}/><span>聚焦：{data.snapshot.entities.find(e=>e.id===focusId)?.displayName??focusId}</span><Button type="link" size="small" onClick={()=>useStudio.getState().setFocus(null)}>显示全部</Button></div>}
    {!nodes.length&&<div className="canvas-empty"><div className="empty-graph-icon"><Icon name="layout" size={46}/></div><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={<><h3>从一张数据表开始</h3><p>{data.snapshot.entities.length?'从左侧对象库选择一张数据表加入画布。':'暂无已采集数据表，请先在数据源管理完成采集。'}</p></>}/>{data.snapshot.entities.length>0&&<Button type="primary" icon={<Icon name="plus" size={16}/>} onClick={()=>useStudio.getState().setLibraryOpen(true)}>添加数据表</Button>}</div>}
    {isDropTarget&&<div className="drop-hint"><Icon name="plus" size={18}/>松开，将数据表添加到这里</div>}
    <div className="canvas-bottom"><div className="zoom-tools">{toolbarIcon('minus','缩小',()=>void zoomOut({duration:150}))}<button className="zoom-value" onClick={()=>void setViewport({...viewport,zoom:1},{duration:200})} title="恢复 100%">{Math.round(viewport.zoom*100)}%</button>{toolbarIcon('plus','放大',()=>void zoomIn({duration:150}))}<span className="tool-divider"/>{toolbarIcon('fit','适应画布（F）',fit)}{toolbarIcon('grid','显示缩略图',()=>useStudio.getState().setMinimap(!minimap),false,minimap)}</div><div className="graph-legend"><span><i className="legend-line catalog"/>数据库外键</span><span><i className="legend-line logical"/>逻辑关系</span><span><i className="legend-line suggested"/>待确认</span></div></div>
  </div>;
}
export function ERCanvas(){const id=useStudio(s=>s.data?.activeDiagramId);return <ReactFlowProvider key={id}><Canvas/></ReactFlowProvider>;}
