import { useMemo, useState } from 'react';
import { Input, Tabs, Popover } from 'antd';
import {
  SearchOutlined,
  DatabaseOutlined,
  FunctionOutlined,
  BranchesOutlined,
  CloudUploadOutlined,
  AppstoreOutlined,
  FilterOutlined,
  SwapOutlined,
  ConsoleSqlOutlined,
} from '@ant-design/icons';
import { useComponentManifests } from '@/api/manifests';
import type { ComponentCategory, ComponentManifest } from '@/types/manifest';
import { COMPONENT_ICONS } from './databaseIcons';

const CAT_ICON: Record<ComponentCategory, React.ReactNode> = {
  source: <DatabaseOutlined />,
  transform: <FunctionOutlined />,
  branch: <BranchesOutlined />,
  sink: <CloudUploadOutlined />,
};

const MANIFEST_ICON: Record<string, React.ReactNode> = {
  'transform.filter': <FilterOutlined />,
  'transform.field-mapping': <SwapOutlined />,
  'transform.field-enrichment': <SwapOutlined />,
  'transform.sql': <ConsoleSqlOutlined />,
};

const SOURCE_POPULARITY_ORDER: Record<string, number> = {
  'source.mysql': 10,
  'source.dameng': 20,
  'source.gaussdb': 30,
  'source.kingbase': 40,
  'source.postgresql': 50,
  'source.sqlserver': 60,
  'source.oracle': 70,
  'source.mariadb': 70,
  'source.kafka': 80,
  'source.elasticsearch': 90,
  'source.clickhouse': 100,
  'source.db2': 110,
  'source.oceanbase': 120,
  'source.tdsql-mysql': 130,
  'source.tdsql-pg': 140,
  'source.highgo': 160,
  'source.gbase8s': 170,
  'source.gbase8a': 180,
  'source.oscar': 190,
};

function getCanvasPopupContainer(trigger: HTMLElement) {
  return (trigger.closest('.canvas-shell') as HTMLElement | null) ?? document.body;
}

function manifestSort(a: ComponentManifest, b: ComponentManifest) {
  if (a.category === 'source' && b.category === 'source') {
    const ia = SOURCE_POPULARITY_ORDER[a.key] ?? 999;
    const ib = SOURCE_POPULARITY_ORDER[b.key] ?? 999;
    if (ia !== ib) return ia - ib;
  }
  return a.label.localeCompare(b.label, 'zh-CN');
}

interface NodeSelectorContentProps {
  onSelect: (manifest: ComponentManifest) => void;
  defaultCategory?: ComponentCategory;
}

function SelectorContent({ onSelect, defaultCategory = 'source' }: NodeSelectorContentProps) {
  const { data: manifests = [], isLoading, isError } = useComponentManifests();
  const [tab, setTab] = useState<ComponentCategory>(defaultCategory);
  const [keyword, setKeyword] = useState('');

  const tabs: { key: ComponentCategory; label: string }[] = [
    { key: 'source', label: '数据源' },
    { key: 'transform', label: '加工' },
    { key: 'sink', label: '目标' },
    { key: 'branch', label: '分流' },
  ];

  const filtered = useMemo(
    () =>
      manifests
        .filter((m) => m.category === tab)
        // Existing transform.field-mapping nodes stay executable and editable,
        // but new flows use the unified field-enrichment component so users do
        // not have to choose between two overlapping mapping components.
        .filter((m) => m.key !== 'transform.field-mapping')
        .filter(
          (m) =>
            !keyword ||
            m.label.toLowerCase().includes(keyword.toLowerCase()) ||
            m.key.toLowerCase().includes(keyword.toLowerCase()),
        )
        .sort(manifestSort),
    [manifests, tab, keyword],
  );

  return (
    <div className="node-selector">
      <div className="node-selector__search">
        <Input
          allowClear
          autoFocus
          prefix={<SearchOutlined />}
          placeholder="搜索节点…"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
        />
      </div>
      <div className="node-selector__tabs">
        <Tabs
          size="small"
          activeKey={tab}
          onChange={(k) => setTab(k as ComponentCategory)}
          items={tabs.map((t) => ({ key: t.key, label: t.label }))}
        />
      </div>
      <div className="node-selector__grid">
        {filtered.length === 0 ? (
          <div
            style={{
              gridColumn: '1 / -1',
              color: 'var(--text-tertiary)',
              padding: '16px 8px',
              fontSize: 12,
              lineHeight: 1.6,
            }}
          >
            {isLoading ? (
              <div style={{ textAlign: 'center' }}>正在加载组件清单…</div>
            ) : isError || manifests.length === 0 ? (
              <div style={{ textAlign: 'center', color: '#ff4d4f' }}>
                无法加载组件清单<br />
                请确认后端服务已启动
              </div>
            ) : tab === 'branch' ? (
              <>
                <div style={{ fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>
                  分流节点是什么？
                </div>
                <div>
                  分流节点用于按条件将一条数据流路由到不同的下游分支（例如根据字段值、属性或正则匹配，把记录分发到多条加工/落库链路），对应 NiFi 的
                  <code style={{ margin: '0 4px' }}>RouteOnAttribute</code>/
                  <code style={{ margin: '0 4px' }}>RouteOnContent</code> 等处理器。
                </div>
                <div style={{ marginTop: 8, color: 'var(--text-tertiary)' }}>
                  该类型组件即将上线，敬请期待。
                </div>
              </>
            ) : (
              <div style={{ textAlign: 'center' }}>暂无匹配组件</div>
            )}
          </div>
        ) : (
          filtered.map((m) => (
            <div
              key={m.key}
              className={`node-selector__card node-selector__card--${m.category}`}
              onClick={() => onSelect(m)}
              title={m.description ?? m.key}
            >
              <div className="node-selector__card-icon">
                {COMPONENT_ICONS[m.key] ?? COMPONENT_ICONS[m.category] ?? MANIFEST_ICON[m.key] ?? CAT_ICON[m.category] ?? <AppstoreOutlined />}
              </div>
              <div className="node-selector__card-label">{m.label}</div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

interface NodeSelectorProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSelect: (manifest: ComponentManifest) => void;
  defaultCategory?: ComponentCategory;
  children: React.ReactElement;
  placement?:
    | 'top' | 'bottom' | 'left' | 'right'
    | 'topLeft' | 'topRight' | 'bottomLeft' | 'bottomRight'
    | 'leftTop' | 'leftBottom' | 'rightTop' | 'rightBottom';
}

export default function NodeSelector({
  open,
  onOpenChange,
  onSelect,
  defaultCategory,
  children,
  placement = 'rightTop',
}: NodeSelectorProps) {
  return (
    <Popover
      open={open}
      onOpenChange={onOpenChange}
      trigger="click"
      placement={placement}
      autoAdjustOverflow
      getPopupContainer={getCanvasPopupContainer}
      classNames={{ root: 'node-selector-popover' }}
      destroyOnHidden
      styles={{ body: { padding: 0 } }}
      content={
        <SelectorContent
          defaultCategory={defaultCategory}
          onSelect={(m) => {
            onSelect(m);
            onOpenChange(false);
          }}
        />
      }
    >
      {children}
    </Popover>
  );
}
