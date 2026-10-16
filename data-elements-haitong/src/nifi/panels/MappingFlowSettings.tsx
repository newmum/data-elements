import { useState } from 'react';
import { AutoComplete, Button, Checkbox, Input, Modal, Space, Typography } from 'antd';
import type { UnifiedMappingDraft } from '../api/fieldMapping';
import { Choice } from './UnifiedRuleForms';
import { getNifiOverlayContainer } from './overlayContainer';

export default function MappingFlowSettings({ draft, onApply, onClose }: {
  draft: UnifiedMappingDraft; onApply: (draft: UnifiedMappingDraft) => void; onClose: () => void;
}) {
  const [policies, setPolicies] = useState(() => ({ ...draft.policies }));
  const [exclude, setExclude] = useState(Array.isArray(draft.exclude) ? draft.exclude.join(',') : '');
  const patch = (key: string, value: unknown) => setPolicies(current => ({ ...current, [key]: value }));
  return <Modal className="fm-rule-modal" open title="流程设置" width={640} getContainer={getNifiOverlayContainer} onCancel={onClose}
    footer={<Space><Button onClick={onClose}>取消</Button><Button type="primary" onClick={() => {
      onApply({ ...draft, policies, exclude: exclude.split(',').map(name => name.trim()).filter(Boolean) });
      onClose();
    }}>应用设置</Button></Space>}>
    <div className="unified-rule-section">
      <Typography.Text type="secondary">适用于当前节点的全部治理规则。单个字段的空值和转换失败处理，在该字段的治理规则中设置。</Typography.Text>
      <section className="unified-rule-section"><div className="fm-rule-section-title">异常处理</div><div className="unified-form-grid">
        <Choice label="记录处理失败时" value={policies.recordErrorMode ?? 'FAIL_FLOWFILE'} choices={{ FAIL_FLOWFILE: '整批失败，保留原输入', ROUTE_INVALID: '成功记录继续，隔离错误记录' }} onChange={value => patch('recordErrorMode', value)} />
        <Choice label="源字段不存在时" value={policies.onMissingSource ?? 'NULL'} choices={{ NULL: '以 NULL 进入规则', FAIL: '记录报错' }} onChange={value => patch('onMissingSource', value)} />
      </div></section>
      <section className="unified-rule-section"><div className="fm-rule-section-title">字段处理</div>
        <Checkbox checked={policies.caseSensitive !== false} onChange={e => patch('caseSensitive', e.target.checked)}>字段名区分大小写</Checkbox>
        <Checkbox checked={!!policies.allowUnmappedPassthrough} onChange={e => patch('allowUnmappedPassthrough', e.target.checked)}>保留未配置规则的源字段</Checkbox>
        {!!policies.allowUnmappedPassthrough && <div className="unified-form-field"><Typography.Text>需要排除的源字段</Typography.Text><Input aria-label="需要排除的源字段" value={exclude} onChange={e => setExclude(e.target.value)} placeholder="多个字段名用英文逗号分隔" /></div>}
      </section>
      <section className="unified-rule-section"><div className="fm-rule-section-title">时间与地区</div><div className="unified-form-grid">
        <div className="unified-form-field"><Typography.Text>时区</Typography.Text><AutoComplete aria-label="流程时区" value={String(policies.zoneId ?? 'Asia/Shanghai')} options={['Asia/Shanghai', 'UTC', 'Asia/Hong_Kong'].map(value => ({ value }))} onChange={value => patch('zoneId', value)} /></div>
        <div className="unified-form-field"><Typography.Text>语言地区</Typography.Text><Input aria-label="语言地区" value={String(policies.locale ?? 'zh-CN')} onChange={e => patch('locale', e.target.value)} /></div>
      </div></section>
    </div>
  </Modal>;
}
