import { useEffect, useMemo } from 'react';
import { Alert, Input, Segmented, Typography } from 'antd';
import { CheckCircleOutlined, FilterOutlined } from '@ant-design/icons';
import type { CanvasNode } from '@/types/dsl';
import { ConditionBuilder, compileConditions, upstreamColumnOptions, type BusinessCondition } from './ConditionBuilder';

interface FilterEditorProps {
  value: string;
  onChange: (value: string) => void;
  rules: BusinessCondition[];
  onRulesChange: (value: BusinessCondition[]) => void;
  sourceNodes?: CanvasNode[];
  keepMode?: 'MATCHED' | 'UNMATCHED';
  onKeepModeChange?: (value: 'MATCHED' | 'UNMATCHED') => void;
}

export default function FilterEditor({ value, onChange, rules, onRulesChange, sourceNodes = [], keepMode = 'MATCHED', onKeepModeChange }: FilterEditorProps) {
  const columns = useMemo(() => upstreamColumnOptions(sourceNodes), [sourceNodes]);
  const generated = useMemo(() => {
    const base = compileConditions(rules);
    return base && keepMode === 'UNMATCHED' ? `NOT (${base})` : base;
  }, [rules, keepMode]);
  const effectiveCondition = generated || value.trim();

  useEffect(() => {
    if (generated && generated !== value) onChange(generated);
  }, [generated, value, onChange]);

  return (
    <div className="filter-editor filter-editor--business">
      <div className="filter-editor__heading">
        <div>
          <Typography.Title level={5}><FilterOutlined /> 设置保留哪些数据</Typography.Title>
          <Typography.Text type="secondary">逐行选择字段和判断方式，系统会自动生成过滤规则。</Typography.Text>
        </div>
        <Segmented
          value={keepMode}
          options={[{ value: 'MATCHED', label: '保留匹配数据' }, { value: 'UNMATCHED', label: '排除匹配数据' }]}
          onChange={(next) => onKeepModeChange?.(next as 'MATCHED' | 'UNMATCHED')}
        />
      </div>
      <ConditionBuilder value={rules} onChange={onRulesChange} columns={columns} />
      <div className="filter-editor__sql-preview">
        <div className="filter-editor__sql-title">生成的 SQL</div>
        <Input.TextArea
          readOnly
          value={effectiveCondition ? `SELECT * FROM FLOWFILE WHERE ${effectiveCondition}` : ''}
          placeholder="条件配置完成后自动生成 SQL"
          autoSize={{ minRows: 2, maxRows: 4 }}
        />
      </div>
      {generated ? (
        <Alert type="success" showIcon icon={<CheckCircleOutlined />} message={`过滤规则已就绪，共 ${rules.length} 条条件`} />
      ) : (
        <Alert type="info" showIcon message="请至少添加一条完整条件" />
      )}
    </div>
  );
}
