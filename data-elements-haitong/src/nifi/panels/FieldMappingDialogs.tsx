import {useEffect,useState} from 'react';
import {Button,Empty,Modal,Progress,Space} from 'antd';
import {DraftTable} from '../../components/DraftTable';
import {getNifiOverlayContainer} from './overlayContainer';
import {findUniqueCaseInsensitiveFieldMatches} from '../utils/fieldNameMatching';
import {displayPath,normalizePath,type FieldInfo,type LocalRecommendation} from './fieldMappingPresentation';
import type {MappingRule} from '../api/fieldMapping';
export function RecommendModal({ open, recommendations, onCancel, onApply }: { open: boolean; recommendations: LocalRecommendation[]; onCancel: () => void; onApply: (rows: LocalRecommendation[]) => void }) {
  const [selected, setSelected] = useState<React.Key[]>([]);
  useEffect(() => {
    setSelected(recommendations.filter((r) => r.confidence >= 0.85).map((r) => r.id));
  }, [recommendations]);
  const picked = recommendations.filter((r) => selected.includes(r.id));
  return (
    <Modal open={open} onCancel={onCancel} title="自动映射" width={760} onOk={() => onApply(picked)} okText={`应用 ${picked.length} 条已选映射`} getContainer={getNifiOverlayContainer}>
      {recommendations.length === 0 ? <Empty description="暂无可推荐映射" /> : <DraftTable fields={{from:{kind:'text',value:row=>row.fromLabel||row.from},to:{kind:'text',value:row=>row.toLabel||row.to},confidence:{kind:'number',value:row=>row.confidence}}}
        size="small"
        rowKey="id"
        dataSource={recommendations}
        scroll={{ y: 'min(500px, calc(100vh - 280px))' }}
        rowSelection={{ selectedRowKeys: selected, onChange: setSelected, preserveSelectedRowKeys: true }}
        columns={[
          { title: '源字段', key:'from', dataIndex: 'from', render: (_v: string, record: LocalRecommendation) => <FieldLabelCell path={record.from} label={record.fromLabel} /> },
          { title: '', width: 30, render: () => '→' },
          { title: '目标字段', key:'to', dataIndex: 'to', render: (_v: string, record: LocalRecommendation) => <FieldLabelCell path={record.to} label={record.toLabel} /> },
          { title: '推荐依据', dataIndex: 'reason' },
          { title: '可信度', key:'confidence', dataIndex: 'confidence', width: 110, render: (v: number) => <Progress percent={Math.round(v * 100)} size="small" showInfo={false} /> },
        ]}
      />}
    </Modal>
  );
}

function FieldLabelCell({ path, label }: { path: string; label?: string }) {
  if (!label) return <>{displayPath(path)}</>;
  return <span className="fm-recommend-cell"><span className="fm-recommend-label">{label}</span><span className="fm-recommend-name">{displayPath(path)}</span></span>;
}

export function TemplateModal({ open, sourceFields, targetFields, onCancel, onApply }: { open: boolean; sourceFields: FieldInfo[]; targetFields: FieldInfo[]; onCancel: () => void; onApply: (rules: MappingRule[]) => void }) {
  const sameNameRules = findUniqueCaseInsensitiveFieldMatches(sourceFields, targetFields)
    .map(({ source, target }) => ({ from: source.path, to: target.path }));
  return (
    <Modal open={open} title="映射模板" width={620} onCancel={onCancel} footer={null} getContainer={getNifiOverlayContainer}>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Button block disabled={sameNameRules.length === 0} onClick={() => onApply(sameNameRules)}>同名字段自动映射（{sameNameRules.length} 条）</Button>
        <Button block onClick={() => onApply([{ constant: 'haitong', to: normalizePath('source_system') }])}>添加 source_system 常量模板</Button>
        <Button block onClick={() => onApply([{ fromList: [normalizePath('first_name'), normalizePath('last_name')], to: normalizePath('full_name'), transform: { fn: 'concat', args: [' '] } }])}>添加姓名合并模板</Button>
      </Space>
    </Modal>
  );
}

