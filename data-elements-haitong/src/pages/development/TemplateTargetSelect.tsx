import { Alert, Button, Select, Space } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { platformApi } from '../../api/platformApi';
import { useSessionRevision } from '../../nifi/api/bridgeSession';

export type TargetChoice = { tid: string; value: string; label: string; dbType: string; kind: string };

export default function TemplateTargetSelect({ kind, value, onChange, selected, excluded = [] }: {
  kind: string;
  value?: string;
  onChange?: (value: string) => void;
  selected?: TargetChoice;
  excluded?: string[];
}) {
  const revision = useSessionRevision();
  const [keyword, setKeyword] = useState('');
  const [pageNum, setPageNum] = useState(1);
  const choices = useQuery({
    queryKey: ['template-target-choices', revision, kind, keyword, pageNum],
    queryFn: ({ signal }) => platformApi<{ list: TargetChoice[]; total: number }>('/nifi/canvas/targets',
      { kind, keyword, pageNum, pageSize: 20 }, { signal }),
    retry: false,
  });
  const options = [...(choices.data?.list || [])];
  if (selected && !options.some(option => option.value === selected.value)) options.unshift(selected);
  return <>
    <Select value={value || undefined} onChange={onChange} showSearch filterOption={false}
      placeholder="搜索并选择数据源管理中已配置的目标库"
      onSearch={text => { setKeyword(text); setPageNum(1); }} loading={choices.isFetching}
      options={options.map(option => ({ ...option, disabled: excluded.includes(option.value) && option.value !== value }))}
      notFoundContent={choices.isFetching ? '正在查询…' : '没有匹配的数据源'}
      dropdownRender={menu => <>{menu}<Space style={{ padding: '4px 8px' }}>
        <Button size="small" disabled={pageNum <= 1 || choices.isFetching} onClick={() => setPageNum(pageNum - 1)}>上一页</Button>
        <span>{pageNum}</span>
        <Button size="small" disabled={pageNum * 20 >= (choices.data?.total || 0) || choices.isFetching}
          onClick={() => setPageNum(pageNum + 1)}>下一页</Button>
      </Space></>}/>
    {choices.isError && <Alert type="error" showIcon message={choices.error instanceof Error ? choices.error.message : '目标库查询失败'}/>}
  </>;
}
