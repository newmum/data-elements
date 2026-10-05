import { hasBlockingLayer, isEditingTarget } from '../core/interaction';
import { useEffect, useState } from 'react';
import { Alert, App as AntApp, Button, Dropdown, Input, Modal, Spin, Tooltip } from 'antd';
import { useStudio, activeDiagram } from '../store';
import { errorText } from '../core/model';
import { downloadJson } from '../core/export';
import { metadataWritable as canWrite } from '../../../services/metadata';
import { EntityLibrary } from '../components/EntityLibrary';
import { ERCanvas } from '../components/ERCanvas';
import { Inspector } from '../components/Inspector';
import { RelationEditor } from '../components/RelationEditor';
import { DiscoveryDialog } from '../components/DiscoveryDialog';
import { DiagramList } from '../components/DiagramList';
import { Capabilities } from '../components/Capabilities';
import { Icon } from '../components/Icon';
import { BrandMark } from '../components/Brand';
import { SearchPalette } from '../components/SearchPalette';
import { STUDIO_ACTION_EVENT } from '../core/navigation';
import type { StudioPage } from '../core/navigation';

export default function StudioApp({view='canvas'}:{view?:StudioPage}){
  const {message,modal}=AntApp.useApp();const state=useStudio();const diagram=useStudio(activeDiagram);
  const[searchOpen,setSearchOpen]=useState(false);
  const[discoverOpen,setDiscoverOpen]=useState(false);const[helpOpen,setHelpOpen]=useState(false);const[nameDialog,setNameDialog]=useState<{id?:string}|null>(null);const[diagramName,setDiagramName]=useState('');const[nameError,setNameError]=useState('');
  useEffect(()=>{useStudio.setState({page:view});},[view]);
  useEffect(()=>{const act=(event:Event)=>{const action=(event as CustomEvent<string>).detail;if(action==='search')setSearchOpen(true);if(action==='help')setHelpOpen(true);if(action==='new-relation')useStudio.getState().openEditor({});if(action==='discover')setDiscoverOpen(true);if(action==='relations')useStudio.getState().setPanel({type:'relations'});if(action==='export')void exportData();};window.addEventListener(STUDIO_ACTION_EVENT,act);return()=>window.removeEventListener(STUDIO_ACTION_EVENT,act);},[]);
  useEffect(()=>{
    const key=(e:KeyboardEvent)=>{
      if(e.defaultPrevented||hasBlockingLayer())return;const s=useStudio.getState();const typing=isEditingTarget(e.target);
      if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='s'){e.preventDefault();void s.save();return;}
      if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='k'&&!s.editor){e.preventDefault();setSearchOpen(true);return;}
      if(typing||s.editor||hasBlockingLayer())return;
      if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='z'&&s.page==='canvas'){e.preventDefault();e.shiftKey?s.redo():s.undo();}
      if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='y'&&s.page==='canvas'){e.preventDefault();s.redo();}
      
    };window.addEventListener('keydown',key);return()=>window.removeEventListener('keydown',key);
  },[]);
  const openName=(id?:string)=>{setNameDialog(id?{id}:{});setDiagramName(id?state.data?.diagrams.find(d=>d.id===id)?.name??'':'');setNameError('');};
  const confirmName=async()=>{try{if(!diagramName.trim())throw new Error('请输入关系图名称。');if(diagramName.length>80)throw new Error('名称最多80字。');if(nameDialog?.id)await state.renameDiagram(nameDialog.id,diagramName);else await state.createDiagram(diagramName);setNameDialog(null);}catch(e){setNameError(errorText(e));}};
  const exportData=async()=>{try{const current=useStudio.getState();await current.save();const latest=useStudio.getState();downloadJson(latest.data,`元数据关系图-${activeDiagram(latest)?.name||'当前模型'}.json`);message.success('已导出已采集结构与关系视图');}catch(e){message.error(errorText(e));}};
  const openImport=()=>state.setPage('capabilities');
  if(!state.ready)return <div className="boot-screen"><div className="brand"><BrandMark/><strong>数据治理中心</strong></div><Spin/><p>正在读取共享元数据与模型布局…</p></div>;
  if(state.loadError||!state.data)return <div className="boot-screen"><Alert type="error" showIcon title="共享元数据加载失败" description={state.loadError}/><Button onClick={()=>state.initialize()}>重试</Button></div>;
  if(!diagram)return <div className="boot-screen"><BrandMark/><h2>建立第一张数据模型</h2><p>先登记数据库并采集结构，再创建模型，跨来源选择表进行建模。</p><Button type="primary" onClick={()=>state.setPage('diagrams')}>打开数据模型管理</Button><Button onClick={()=>state.setPage('capabilities')}>登记数据源</Button></div>;
  const suggestions=state.data.relationships.filter(r=>r.reviewStatus==='suggested').length;
  const saveLabels={saved:'已保存到当前浏览器',dirty:'待保存',saving:'保存中…',error:'保存失败 · 重试'};
  const menu={items:[{key:'rename',label:'重命名当前关系图',disabled:!canWrite(),icon:<Icon name="edit" size={16}/>},{key:'duplicate',label:'复制当前视图',disabled:!canWrite(),icon:<Icon name="copy" size={16}/>},{type:'divider' as const},{key:'export',label:'导出结构与关系视图',disabled:!canWrite(),icon:<Icon name="export" size={16}/>},],onClick:({key}:{key:string})=>{if(key==='rename')openName(diagram.id);if(key==='export')void exportData();if(key==='import')openImport();if(key==='duplicate')modal.confirm({title:'复制当前模型视图？',content:'复制布局和表范围，工作区关系仍然共享。',onOk:async()=>{await state.duplicateDiagram(diagram.id);message.success('副本已保存到当前浏览器');}});}};
  return <div className="studio-shell er-module" data-feature="er">
    {view==='capabilities'&&<div className="er-module-tools"><Button onClick={openImport} icon={<Icon name="import"/>}>采集元数据</Button><Button onClick={exportData} icon={<Icon name="export"/>}>导出关系工作区</Button></div>}
    {view==='canvas'?<div className={`workspace-body ${state.libraryOpen?'library-visible':''}`}>
      {state.libraryOpen&&<><button className="library-backdrop" aria-label="关闭对象库" onClick={()=>state.setLibraryOpen(false)}/><EntityLibrary/></>}
      <main className="workbench"><div className="content-header"><div className="content-heading"><div className="page-breadcrumb"><button onClick={()=>state.setPage('diagrams')}>数据模型</button><Icon name="chevron" size={10}/>元数据管理<Icon name="chevron" size={10}/><span>ER 关系图</span></div><div className="diagram-title-row"><h1 title={diagram.name}>{diagram.name}</h1><Dropdown menu={menu} trigger={['click']}><Button type="text" size="small" aria-label="关系图更多操作" icon={<Icon name="more" size={20}/>}/></Dropdown></div><div className="content-subtitle"><span>{diagram.description||'可视化探索与维护数据关联'}</span><i/><Tooltip title={state.saveError??'布局按版本保存到当前浏览器。点击立即保存。'}><button className={`save-status save-${state.saveState}`} onClick={()=>void state.save()}><Icon name={state.saveState==='saved'?'check':state.saveState==='error'?'warning':'clock'} size={12}/>{saveLabels[state.saveState]}</button></Tooltip></div></div><div className="header-actions"><Tooltip title="查看整个工作区的关系"><Button className="optional-action" icon={<Icon name="list" size={16}/>} onClick={()=>state.setPanel(state.panel?.type==='relations'?null:{type:'relations'})}>关系列表</Button></Tooltip><Button disabled={!canWrite()} icon={<Icon name="plus" size={16}/>} onClick={()=>state.openEditor({})}>新建关系</Button><Button disabled={!canWrite()} type="primary" className="discover-button" icon={<Icon name="spark" size={17}/>} onClick={()=>setDiscoverOpen(true)} loading={state.job?.status==='running'}>发现关系{suggestions>0&&<span className="suggestions-badge">{suggestions}</span>}</Button></div></div>
        <div className="canvas-area"><ERCanvas/><Inspector onDiscover={()=>setDiscoverOpen(true)}/></div>
        <footer className="workbench-status"><span><Icon name="database" size={11}/>{state.data.snapshot.sources.length} 个数据源 <i/> {state.data.snapshot.entities.length} 个实体 <i/> {state.data.snapshot.entities.reduce((n,e)=>n+e.fields.length,0)} 个字段</span><button onClick={()=>state.setPanel({type:'discovery'})}><span className="pending-dot"/>{suggestions} 条关系等待确认<Icon name="chevron" size={12}/></button><span className="status-local"><Icon name="clock" size={11}/>结构快照 · {new Date(state.data.snapshot.capturedAt).toLocaleDateString('zh-CN')}</span></footer>
      </main>
    </div>:view==='diagrams'?<DiagramList onCreate={()=>openName()} onRename={openName}/>:<Capabilities/>}
    <SearchPalette open={searchOpen} onClose={()=>setSearchOpen(false)}/><RelationEditor/><DiscoveryDialog open={discoverOpen} onClose={()=>setDiscoverOpen(false)}/>
    <Modal open={!!nameDialog} title={nameDialog?.id?'重命名关系图':'新建关系图'} centered width={440} okText={nameDialog?.id?'保存':'创建关系图'} cancelText="取消" onCancel={()=>setNameDialog(null)} onOk={confirmName}><p className="modal-intro">为这个数据视角取一个容易识别的名称。</p><Input autoFocus placeholder="例如：节目版权与归档模型" value={diagramName} maxLength={80} showCount onChange={e=>setDiagramName(e.target.value)} onPressEnter={confirmName} status={nameError?'error':undefined}/>{nameError&&<p className="danger-text">{nameError}</p>}</Modal>
    <Modal open={helpOpen} title={<span className="modal-title"><Icon name="info"/>使用数据治理中心</span>} onCancel={()=>setHelpOpen(false)} footer={<Button type="primary" onClick={()=>setHelpOpen(false)}>开始探索</Button>} width={620} centered><div className="help-content"><h3>从结构，到关系</h3><p>从左侧拖入数据表。拖动表头调整位置，从字段两侧端点拖到另一个字段建立逻辑关系。双击表头查看完整结构；双击列表中的数据表可加入画布。</p><div className="help-legend"><span><i className="legend-line catalog"/>数据库外键：目录定义，只读</span><span><i className="legend-line logical"/>逻辑关系：人工确认后保存到共享台账</span><span><i className="legend-line suggested"/>待确认：依据已采集结构识别的候选</span></div><h3>快捷操作</h3><div className="shortcut-grid"><span>查找表、字段和关系图<kbd>Ctrl / ⌘ + K</kbd></span><span>立即保存<kbd>Ctrl / ⌘ + S</kbd></span><span>撤销画布操作<kbd>Ctrl / ⌘ + Z</kbd></span><span>重做画布操作<kbd>Ctrl / ⌘ + Shift + Z</kbd></span><span>平移画布<kbd>按住 Space</kbd></span><span>适应画布<kbd>F</kbd></span><span>移除选中节点<kbd>Delete</kbd></span><span>退出详情或聚焦<kbd>Esc</kbd></span></div><Alert showIcon type="info" title="保存与备份" description="表和字段读取共享后端的已采集快照，逻辑关系保存到共享台账。仅画布布局按当前账号和租户保存在浏览器；不会写入物理外键。"/></div></Modal>
  </div>;
}
