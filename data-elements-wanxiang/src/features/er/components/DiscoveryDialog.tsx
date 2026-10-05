import { useEffect, useState } from 'react';
import { Alert, Modal, Radio } from 'antd';
import { useStudio, activeDiagram } from '../store';
import { Icon } from './Icon';
import { CANVAS_PAGE_SIZE } from '../core/model';
export function DiscoveryDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const data = useStudio(s => s.data), diagram = useStudio(activeDiagram), group=useStudio(s=>s.canvasGroup), [scope, setScope] = useState('diagram');
  useEffect(()=>{if(open)setScope((diagram?.nodes.length||0)>300?'group':'diagram');},[open,diagram?.id]);
  if (!data || !diagram) return null;
  const ids = (scope === 'group' ? diagram.nodes.slice(group*CANVAS_PAGE_SIZE,(group+1)*CANVAS_PAGE_SIZE) : scope === 'diagram' ? diagram.nodes : data.snapshot.entities).map(n => 'entityId' in n ? n.entityId : n.id);
  return <Modal open={open} title={<span className="modal-title"><span className="modal-icon"><Icon name="spark" /></span>发现潜在关系</span>} onCancel={onClose} okText={`分析 ${ids.length} 个实体`} cancelText="取消"
    onOk={() => { void useStudio.getState().runDiscovery(ids); onClose(); }} okButtonProps={{ disabled: ids.length < 2 || ids.length > 300 }} width={580} centered>
    <p className="modal-intro">依据已采集的字段名称、类型和单字段主键/唯一键标记寻找关联线索。确认后才写入共享逻辑关系台账。</p>
    <div className="config-section"><h3>分析范围</h3><Radio.Group value={scope} onChange={e => setScope(e.target.value)} className="choice-list">{diagram.nodes.length>CANVAS_PAGE_SIZE&&<Radio value="group"><strong>当前画布分组</strong><span>分析第 {group+1} 组显示的 {Math.min(CANVAS_PAGE_SIZE,diagram.nodes.length-group*CANVAS_PAGE_SIZE)} 张表</span></Radio>}<Radio value="diagram"><strong>当前关系图</strong><span>分析模型中的 {diagram.nodes.length} 个实体</span></Radio><Radio value="all"><strong>整个对象库</strong><span>覆盖 {data.snapshot.sources.length} 个来源、{data.snapshot.entities.length} 个实体（只读取共享后端已有的表与字段）</span></Radio></Radio.Group></div>
    <div className="analysis-explanation"><Icon name="spark" size={20}/><div><strong>结构分析</strong><p>字段命名 → 类型兼容 → 单字段键标记 → 推荐依据</p></div></div>
    <Alert type="info" showIcon title="结构推荐不等于数据验证" description="推荐以虚线显示，需要人工确认。分析不会读取数据库记录，也不会创建物理外键。" />
    {ids.length > 300 && <p className="danger-text">单次分析最多支持 300 个实体；可选择当前画布分组，逐组发现候选关系。</p>}
    {ids.length < 2 && <p className="danger-text">请先将至少两张数据表加入关系图；单次最多分析 300 张表。</p>}
  </Modal>;
}
