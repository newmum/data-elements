import { normalizeSourceManifestKey, sinkDbType, buildJdbcUrl } from "./registeredDatasourceType";
﻿import { useEffect, useMemo, useState, useCallback, useRef } from 'react';
import { AutoComplete, Divider, Drawer, Form, Input, InputNumber, Select, Switch, Collapse, Button, Space, Spin, App as AntdApp, Tooltip, Modal, Segmented } from 'antd';
import { syncModes, syncSettings, syncSettingsPatch, syncSettingsError } from '../utils/syncSettings';
import {
  DatabaseOutlined,
  FunctionOutlined,
  BranchesOutlined,
  CloudUploadOutlined,
  AppstoreOutlined,
  CloseOutlined,
  ExperimentOutlined,
  SaveOutlined,
  SwapOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import CodeMirror from '@uiw/react-codemirror';
import { sql } from '@codemirror/lang-sql';
import { json } from '@codemirror/lang-json';
import type { ComponentCategory, ComponentManifest, FieldSchema } from '@/types/manifest';
import { useCanvasStore } from '@/stores/canvasStore';
import { useComponentManifests } from '@/api/manifests';
import { fetchNodeTemplate, probeColumns, probeTables, testConnection, type ColumnsProbeResult, type MetadataTable, type ProbedColumn } from '@/api/pipelines';
import { getApiErrorMessage, hiveMetadataConfigError } from '@/api/response';
import { fetchDatabases, fetchDatabaseDetail, type ApiPullItem, type DbDictItem } from '@/api/dataassets';
import FieldMappingEditor from './FieldMappingEditor';
import MaterializeTableModal from './MaterializeTableModal';
import { getNifiOverlayContainer } from './overlayContainer';
import { resolveDatasourceId } from '@/api/datasourceIdentity';
import { upstreamTableSources } from './materializeSources';
import { columnLabels, columnsForMapping, mappingColumnNodes, missingMappingColumnProbes } from './fieldMappingColumns';
import CustomSqlEditor, { type CustomSqlEditorHandle } from './CustomSqlEditor';
import FilterEditor from './FilterEditor';
import BranchEditor, { type BranchRoute } from './BranchEditor';
import type { BusinessCondition } from './ConditionBuilder';
import { COMPONENT_ICONS } from './databaseIcons';

const CAT_ICON: Record<ComponentCategory, React.ReactNode> = {
  source: <DatabaseOutlined />,
  transform: <FunctionOutlined />,
  branch: <BranchesOutlined />,
  sink: <CloudUploadOutlined />,
};

function showProbeColumnsSummary(title: string, results: ColumnsProbeResult[], fallbackTables: string[]) {
  const rows = results.flatMap((result, resultIndex) => {
    const table = result.table ?? fallbackTables[resultIndex] ?? '';
    return (result.columns ?? []).map((column) => ({ table, column }));
  });
  const maxRows = 80;
  Modal.info({
    title,
    width: 860,
    content: (
      <div className="probe-columns-summary">
        <p style={{ margin: '0 0 10px', color: '#64748b' }}>
          共探查 {results.length} 张表，{rows.length} 个字段{rows.length > maxRows ? '，下方仅展示部分字段' : ''}
        </p>
        <div style={{ maxHeight: 420, overflow: 'auto' }}>
          <table className="probe-columns-table">
            <thead>
              <tr>
                <th>表名</th>
                <th>字段名</th>
                <th>注释</th>
                <th>类型</th>
                <th>长度</th>
                <th>小数位</th>
                <th>主键</th>
                <th>可空</th>
              </tr>
            </thead>
            <tbody>
              {rows.slice(0, maxRows).map(({ table, column }, index) => (
                <tr key={`${table}-${column.columnName}-${index}`}>
                  <td>{table}</td>
                  <td>{column.columnName}</td>
                  <td>{column.columnComment || '-'}</td>
                  <td>{column.dataType || column.typeName || '-'}</td>
                  <td>{column.columnSize ?? '-'}</td>
                  <td>{column.decimalDigits ?? '-'}</td>
                  <td>{column.primaryKey == null ? '-' : column.primaryKey ? '是' : '否'}</td>
                  <td>{column.nullable == null ? '-' : column.nullable ? '是' : '否'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    ),
    okText: '知道了',
  });
}

interface FieldProps {
  field: FieldSchema;
  value: unknown;
  onChange: (v: unknown) => void;
  disabled?: boolean;
}

function FieldEditor({ field, value, onChange, disabled }: FieldProps) {
  switch (field.type) {
    case 'text':
      return <Input value={(value as string) ?? ''} placeholder={field.placeholder} onChange={(e) => onChange(e.target.value)} disabled={disabled} />;
    case 'password':
      return <Input.Password value={(value as string) ?? ''} placeholder={field.placeholder} onChange={(e) => onChange(e.target.value)} disabled={disabled} />;
    case 'number':
      return <InputNumber value={value as number} placeholder={field.placeholder} style={{ width: '100%' }} onChange={(v) => onChange(v)} disabled={disabled} />;
    case 'select':
      return <Select value={value as string} placeholder={field.placeholder} options={field.options} allowClear onChange={(v) => onChange(v)} disabled={disabled} />;
    case 'switch':
      return <Switch checked={Boolean(value)} onChange={(v) => onChange(v)} disabled={disabled} />;
    case 'sql-editor':
      return (
        <CodeMirror
          value={(value as string) ?? ''}
          height="180px"
          extensions={[sql()]}
          onChange={(v) => onChange(v)}
          editable={!disabled}
          basicSetup={{ lineNumbers: true, highlightActiveLine: true }}
        />
      );
    case 'json-editor':
    case 'columns-mapper': {
      const text = typeof value === 'string' ? value : JSON.stringify(value ?? {}, null, 2);
      return (
        <CodeMirror
          value={text}
          height="180px"
          extensions={[json()]}
          onChange={(v) => onChange(v)}
          editable={!disabled}
          basicSetup={{ lineNumbers: true, highlightActiveLine: true }}
        />
      );
    }
    case 'field-mapping-dsl':
      return <FieldMappingEditor value={value} onChange={onChange as (v: string) => void} />;
    case 'table-picker':
      return <Input value={(value as string) ?? ''} placeholder={field.placeholder ?? '表名'} onChange={(e) => onChange(e.target.value)} disabled={disabled} />;
    case 'duration': {
      const raw = typeof value === 'string' ? value : (field.default as string) ?? '60 sec';
      const m = /^\s*(\d+(?:\.\d+)?)\s*(ms|sec|min|hour|day)\s*$/i.exec(raw);
      const n = m ? Number(m[1]) : 60;
      const unit = m ? m[2].toLowerCase() : 'sec';
      const emit = (nn: number, uu: string) => onChange(`${nn} ${uu}`);
      return (
        <Space.Compact style={{ width: '100%' }}>
          <InputNumber
            value={n}
            min={0}
            style={{ width: '60%' }}
            onChange={(v) => emit(typeof v === 'number' ? v : 0, unit)}
            disabled={disabled}
          />
          <Select
            value={unit}
            style={{ width: '40%' }}
            onChange={(u) => emit(n, u)}
            disabled={disabled}
            options={[
              { value: 'ms', label: '毫秒' },
              { value: 'sec', label: '秒' },
              { value: 'min', label: '分钟' },
              { value: 'hour', label: '小时' },
              { value: 'day', label: '天' },
            ]}
          />
        </Space.Compact>
      );
    }
    default:
      return <Input value={(value as string) ?? ''} onChange={(e) => onChange(e.target.value)} disabled={disabled} />;
  }
}

function dbOptionText(item: DbDictItem) {
  return [item.label, item.dbName, item.database, item.dbType, item.host].filter(Boolean).join(' ');
}

const LINEWELL_JDBC_WRITER = 'LINEWELL_PUT_DATABASE_RECORD';
const NATIVE_JDBC_WRITER = 'NIFI_PUT_DATABASE_RECORD';

function supportsLinewellOracleWriter(dbType?: unknown) {
  const normalized = sinkDbType(String(dbType ?? ''));
  return normalized === 'Oracle' || normalized === 'OCEANBASE_ORACLE';
}

function effectiveJdbcWriterType(dbType: unknown, configuredWriter: unknown) {
  const configured = String(configuredWriter ?? '').trim().toUpperCase();
  if (configured === LINEWELL_JDBC_WRITER || configured === 'LINEWELL') return LINEWELL_JDBC_WRITER;
  if (configured === NATIVE_JDBC_WRITER || configured === 'NATIVE') return NATIVE_JDBC_WRITER;
  // Preserve the existing automatic default only for a newly selected target.
  // An explicit user choice of the Linewell writer is valid for every JDBC database.
  return supportsLinewellOracleWriter(dbType) ? LINEWELL_JDBC_WRITER : NATIVE_JDBC_WRITER;
}

/**
 * The target database is the source of truth when a JDBC target is generated
 * or switched.  Oracle and OceanBase Oracle require the Linewell component's
 * MERGE capability; every other JDBC target starts with NiFi's native,
 * append/insert-safe writer.  Keeping the pair together prevents a stale
 * writer from the previously selected database leaking into the new target.
 */
function jdbcWriterDefaults(dbType: unknown): Pick<Record<string, unknown>, 'writerType' | 'statementType'> {
  const linewellWriter = supportsLinewellOracleWriter(dbType);
  return {
    writerType: linewellWriter ? LINEWELL_JDBC_WRITER : NATIVE_JDBC_WRITER,
    statementType: linewellWriter ? 'MERGE' : 'INSERT',
  };
}

function normalizeOracleConnectionType(value?: string) {
  const normalized = String(value ?? '').trim().toUpperCase().replace(/[\s_-]+/g, '');
  if (normalized === 'SID') return 'SID';
  if (normalized === 'SERVICENAME' || normalized === 'SERVICE') return 'SERVICE_NAME';
  return undefined;
}

function firstText(...values: unknown[]) {
  for (const value of values) {
    const text = String(value ?? '').trim();
    if (text) return text;
  }
  return undefined;
}

/**
 * Choose the interface definition registered for the logical source table.
 * API datasource registration supports more than one interface/table, so the
 * legacy single apiUrl fields are only a fallback for historical records.
 */
function apiPullItemForTable(item: DbDictItem, table?: unknown): ApiPullItem | undefined {
  const expectedTable = String(table ?? '').trim();
  const definitions = item.apiPullItems ?? [];
  if (!definitions.length) return undefined;
  return definitions.find((definition) => {
    const candidate = String(definition.tableName ?? '').trim();
    return Boolean(expectedTable && candidate && candidate.toLowerCase() === expectedTable.toLowerCase());
  }) ?? (definitions.length === 1 ? definitions[0] : definitions[0]);
}

function apiPullUrl(definition: ApiPullItem | undefined, fallback?: string) {
  const direct = firstText(definition?.requestUrl, definition?.url, definition?.apiUrl, fallback);
  if (direct) return direct;
  const baseUrl = firstText(definition?.baseUrl);
  const endpointPath = firstText(definition?.endpointPath);
  if (!baseUrl) return endpointPath;
  if (!endpointPath) return baseUrl;
  return `${baseUrl.replace(/\/+$/, '')}${endpointPath.startsWith('/') ? endpointPath : `/${endpointPath}`}`;
}

function apiPullHeaders(definition: ApiPullItem | undefined, fallback?: string) {
  const raw = definition?.requestHeadersJson ?? definition?.commonHeadersJson ?? definition?.requestHeaders ?? fallback;
  if (typeof raw === 'string') return raw.trim() || undefined;
  if (!raw) return undefined;
  try {
    if (Array.isArray(raw)) {
      const headers: Record<string, string> = {};
      raw.forEach((entry) => {
        const name = firstText(entry.key, entry.name);
        if (name) headers[name] = String(entry.value ?? '');
      });
      return Object.keys(headers).length ? JSON.stringify(headers) : undefined;
    }
    return JSON.stringify(raw);
  } catch {
    return undefined;
  }
}

function buildHuaweiMrsHiveJdbcUrl(item: DbDictItem, host?: string, port?: string, database?: string) {
  const db = String(database || item.database || item.dbName || 'default').trim() || 'default';
  const quorum = String(item.zookeeperQuorum || '').trim();
  const endpoint = quorum || `${String(host || '').trim()}:${String(port || '10000').trim()}`;
  if (!endpoint || endpoint === ':10000') return undefined;
  const parts = [`jdbc:hive2://${endpoint}/${db}`];
  const append = (key: string, value: unknown) => {
    const text = String(value ?? '').trim();
    if (text) parts.push(`${key}=${text}`);
  };
  if (quorum) {
    append('serviceDiscoveryMode', item.serviceDiscoveryMode || 'zooKeeper');
    append('zooKeeperNamespace', item.zookeeperNamespace || 'hiveserver2');
  }
  if (String(item.authMode || '').toUpperCase() === 'KERBEROS') {
    append('auth', 'KERBEROS');
    append('sasl.qop', item.saslQop || 'auth-conf');
    append('principal', item.principal);
    append('ssl', item.ssl);
    append('user.principal', item.userPrincipal);
    append('user.keytab', item.keytabPath);
  } else if (quorum) {
    append('auth', 'none');
  }
  const extra = String(item.extraParams || '').trim();
  if (extra) parts.push(extra.replace(/^;+/, ''));
  return parts.join(';');
}

function splitUpdateKeys(value: unknown) {
  return String(value ?? '')
    .split(',')
    .map((key) => key.trim())
    .filter(Boolean);
}

function primaryKeyText(columns: ProbedColumn[]) {
  return columns
    .filter((column) => column.primaryKey && column.columnName && column.columnName.toUpperCase() !== 'ODS_UUID')
    .map((column) => column.columnName.trim())
    .filter(Boolean)
    .join(',');
}

function hasStableTargetKey(columns: unknown): boolean {
  if (!Array.isArray(columns)) return false;
  return columns.some((column: any) => {
    const name = String(column?.columnName ?? column?.name ?? '').trim();
    if (!name || name.toUpperCase() === 'ODS_UUID') return false;
    const flag = (value: unknown) => value === true || value === 1 || value === '1' || value === 'true';
    return flag(column?.primaryKey)
      || flag(column?.primary_key)
      || flag(column?.isPrimaryKey)
      || flag(column?.isPk)
      || flag(column?.isUnique)
      || flag(column?.is_unique);
  });
}

function isStructuredSource(manifestKey: string | undefined): boolean {
  return manifestKey === 'source.ftp'
    || manifestKey === 'source.sftp'
    || manifestKey === 'source.api'
    || manifestKey === 'source.kafka';
}

/**
 * FTP/SFTP push-receive tasks can be configured before the upstream system has
 * deposited its first file.  In that state there is nothing physical to infer,
 * but the task already owns an authoritative registered table schema.  Reuse
 * that schema first so “探查字段” remains useful for the table being prepared
 * for access; file probing remains the fallback for unregistered tables.
 */
function registeredSourceColumnsForTables(
  config: Record<string, unknown>,
  tables: string[],
): Map<string, ProbedColumn[]> {
  const result = new Map<string, ProbedColumn[]>();
  const byTable = config.sourceColumnsByTable && typeof config.sourceColumnsByTable === 'object'
    ? config.sourceColumnsByTable as Record<string, unknown>
    : {};
  const defaultColumns = Array.isArray(config.sourceColumns)
    ? config.sourceColumns as ProbedColumn[]
    : [];

  tables.forEach((table) => {
    const matchedKey = Object.keys(byTable).find((key) => key.toLowerCase() === table.toLowerCase());
    const columns = matchedKey && Array.isArray(byTable[matchedKey])
      ? byTable[matchedKey] as ProbedColumn[]
      : tables.length === 1 ? defaultColumns : [];
    if (columns.some((column) => String(column?.columnName ?? '').trim())) {
      result.set(table, columns);
    }
  });
  return result;
}

function shouldUseDetectedPrimaryKey(currentKeys: unknown, columns: ProbedColumn[]) {
  const detectedKeys = primaryKeyText(columns);
  if (!detectedKeys) return false;
  const keys = splitUpdateKeys(currentKeys);
  if (keys.length === 0) return true;
  if (keys.length === 1 && keys[0].toLowerCase() === 'tid') return true;
  const columnNames = new Set(columns.map((column) => column.columnName?.toLowerCase()).filter(Boolean));
  return columnNames.size > 0 && keys.some((key) => !columnNames.has(key.toLowerCase()));
}

function buildTargetColumnsPatch(config: Record<string, unknown>, columns: ProbedColumn[]) {
  const patch: Record<string, unknown> = { targetColumns: columns };
  if (shouldUseDetectedPrimaryKey(config.updateKeys, columns)) {
    patch.updateKeys = primaryKeyText(columns);
  } else {
    const normalizedKeys = splitUpdateKeys(config.updateKeys)
      .map((key) => columns.find((column) => column.columnName?.toLowerCase() === key.toLowerCase())?.columnName ?? key)
      .join(',');
    if (normalizedKeys && normalizedKeys !== config.updateKeys) patch.updateKeys = normalizedKeys;
  }
  return patch;
}

function isVisible(field: FieldSchema, config: Record<string, unknown>): boolean {
  if (!field.visibleWhen) return true;
  if (field.visibleWhen.key === 'tableMode') {
    return (config.tableMode ?? 'SINGLE') === field.visibleWhen.equals;
  }
  if (field.group === 'incremental' && field.visibleWhen.key === 'syncMode') {
    const syncMode = syncSettings(config).syncMode;
    return syncMode === 'INCREMENTAL' || syncMode === 'FULL_THEN_INCR';
  }
  if (field.key === 'deleteTargetData') return ['FULL', 'FULL_THEN_INCR'].includes(syncSettings(config).syncMode);
  return config[field.visibleWhen.key] === field.visibleWhen.equals;
}

const GROUP_LABELS: Record<string, string> = {
  connection: '连接配置',
  mrs: '华为 MRS 服务发现',
  security: 'Kerberos 认证',
  schema: '数据结构',
  query: '查询条件',
  incremental: '增量配置',
  schedule: '调度策略',
  advanced: '高级选项',
  default: '基础配置',
  sync: '同步方式',
};

const FIELD_MAPPING_DEFAULT = JSON.stringify({
  version: '1.0',
  passthroughUnmapped: false,
  passthroughCaseSensitive: true,
  onMissingSource: 'NULL',
  onTypeMismatch: 'CAST',
  mappings: [],
}, null, 2);

function withVisibleWhen(field: FieldSchema, visibleWhen: FieldSchema['visibleWhen']): FieldSchema {
  return { ...field, visibleWhen };
}

function expandIncrementalField(field: FieldSchema): FieldSchema[] {
  if (field.key === 'schedulingPeriod') {
    return [
      {
        key: 'schedulingStrategy',
        label: '调度策略',
        type: 'select',
        default: 'TIMER_DRIVEN',
        group: 'schedule',
        options: [
          { label: '按周期执行', value: 'TIMER_DRIVEN' },
          { label: 'Cron 表达式', value: 'CRON_DRIVEN' },
        ],
      },
      // 调度周期：保留后端原选择框（预设周期下拉），交互不变。
      {
        ...field,
        label: '调度周期 / Cron',
        help: '按周期执行时填写 60 sec、5 min；Cron 策略时填写 NiFi Cron 表达式。',
        visibleWhen: { key: 'schedulingStrategy', equals: 'TIMER_DRIVEN' },
      },
      // Cron 表达式：自定义输入框。
      {
        key: 'schedulingPeriod',
        type: 'text',
        label: 'Cron 表达式',
        group: 'schedule',
        default: '0 0 0 * * ?',
        placeholder: '0 0 * * * ? 或 0 0/5 * * * ?',
        help: 'Cron 驱动模式：填写 NiFi Cron 表达式（6 段，含秒）。例如每天 0 点执行：0 0 0 * * ?',
        visibleWhen: { key: 'schedulingStrategy', equals: 'CRON_DRIVEN' },
      },
    ];
  }
  if (field.key !== 'incrementalColumn') return [field];
  return [
    {
      ...field,
      label: '主增量字段',
      required: false,
      group: 'incremental',
      visibleWhen: { key: 'syncMode', equals: 'INCREMENTAL' },
      placeholder: field.placeholder ?? '如 update_time / id / version',
      help: '登记表已配置“抽取时间”时会自动回填；未配置时可手工选择。时间戳字段建议配合次级字段避免同秒多条漏数。',
    },
    {
      key: 'tieBreakerColumn',
      label: '次级字段',
      type: 'text',
      group: 'incremental',
      visibleWhen: { key: 'syncMode', equals: 'INCREMENTAL' },
      placeholder: '可选，通常填写主键 id',
      help: '主增量字段精度不足时用于排序裁断。',
    },
    {
      key: 'initialStrategy',
      label: '首次启动策略',
      type: 'select',
      default: 'START_AT_BEGINNING',
      group: 'incremental',
      visibleWhen: { key: 'syncMode', equals: 'INCREMENTAL' },
      options: [
        { label: '从最早数据开始', value: 'START_AT_BEGINNING' },
        { label: '从当前最大值开始', value: 'START_AT_CURRENT_MAX' },
        { label: '从指定值开始', value: 'START_AT_VALUE' },
      ],
    },
    withVisibleWhen({
      key: 'initialValue',
      label: '起始值',
      type: 'text',
      group: 'incremental',
      placeholder: '如 2026-01-01 00:00:00 或 100000',
      visibleWhen: { key: 'initialStrategy', equals: 'START_AT_VALUE' },
    }, { key: 'initialStrategy', equals: 'START_AT_VALUE' }),
    {
      key: 'lookbackSeconds',
      label: '安全回看窗口(秒)',
      type: 'number',
      default: 5,
      group: 'incremental',
      help: '用于回退水位，减少边界数据漏读。',
    },
    {
      key: 'safetyLagSeconds',
      label: '安全滞后(秒)',
      type: 'number',
      default: 1,
      group: 'incremental',
      help: '避免读取过新的未稳定数据。',
    },
    {
      key: 'batchSize',
      label: '单批最大条数',
      type: 'number',
      default: 5000,
      group: 'incremental',
      help: '编译到 NiFi GenerateTableFetch 的 Partition Size。',
    },
    {
      key: 'maxRowsPerRun',
      label: '单次最大行数',
      type: 'number',
      default: 200000,
      group: 'incremental',
      help: '用于后续任务编排限流，0 表示不限制。',
    },
    {
      key: 'customWherePredicate',
      label: '自定义 WHERE',
      type: 'text',
      group: 'incremental',
      placeholder: "如 tenant_id = 100 AND status IN ('A','B')",
      help: '会编译到 NiFi Where Clause，禁止分号、注释、DDL/DML 与子查询。',
    },
    {
      key: 'softDeleteEnabled',
      label: '软删除同步',
      type: 'switch',
      default: false,
      group: 'incremental',
      help: '开启后不要在 WHERE 中过滤删除标记。',
    },
    {
      key: 'deleteReconcileStrategy',
      label: '删除补偿',
      type: 'select',
      default: 'NONE',
      group: 'incremental',
      options: [
        { label: '无', value: 'NONE' },
        { label: '软删除模式', value: 'SOFT_DELETE_ONLY' },
        { label: '定期对账补删', value: 'PERIODIC_RECONCILE' },
      ],
      help: '当前画布仅保存策略配置，完整对账补删由后续模块执行。',
    },
  ];
}

function isIncrementalVisible(field: FieldSchema, config: Record<string, unknown>): boolean {
  if (field.key === 'initialValue' && syncSettings(config).syncMode === 'FULL_THEN_INCR') return false;
  if (field.group === 'incremental' && !['INCREMENTAL', 'FULL_THEN_INCR'].includes(syncSettings(config).syncMode)) return false;
  return isVisible(field, config);
}

export default function ConfigDrawer() {
  const sqlEditorRef = useRef<CustomSqlEditorHandle>(null);
  const [sqlGenerating, setSqlGenerating] = useState(false);
  const { message } = AntdApp.useApp();
  const drawerNodeId = useCanvasStore((s) => s.drawerNodeId);
  const node = useCanvasStore((s) => (drawerNodeId ? s.nodes[drawerNodeId] : null));
  const openDrawer = useCanvasStore((s) => s.openDrawer);
  const updateConfig = useCanvasStore((s) => s.updateNodeConfig);
  const switchNodeManifest = useCanvasStore((s) => s.switchNodeManifest);
  const setLabel = useCanvasStore((s) => s.setNodeLabel);
  const canvasMode = useCanvasStore((s) => s.canvasMode);
  const { data: manifests } = useComponentManifests();
  const allNodes = useCanvasStore((s) => s.nodes);
  const allEdges = useCanvasStore((s) => s.edges);

  const manifest = useMemo<ComponentManifest | undefined>(
    () => manifests?.find((m) => m.key === node?.manifestKey),
    [manifests, node?.manifestKey],
  );

  const open = drawerNodeId !== null;
  useEffect(() => { setSqlGenerating(false); }, [drawerNodeId]);

  // 先弹出 Drawer，再延迟渲染内容，避免首帧动画卡顿。
  const [bodyReady, setBodyReady] = useState(false);
  useEffect(() => {
    if (open) {
      const id = requestAnimationFrame(() => {
        requestAnimationFrame(() => setBodyReady(true));
      });
      return () => cancelAnimationFrame(id);
    } else {
      setBodyReady(false);
    }
  }, [open]);

  const mappingContext = useMemo(() => node && ['transform.field-mapping', 'transform.field-enrichment'].includes(manifest?.key ?? '')
    ? mappingColumnNodes(node.id, allNodes, allEdges) : undefined,
  [allNodes, allEdges, node?.id, manifest?.key]);

  /** Keep every physical column, including fields without a database comment. */
  const columnMeta = useMemo(() => {
    if (!mappingContext) return undefined;
    return {
      sourceLabels: columnLabels(columnsForMapping(mappingContext.source, 'source')),
      targetLabels: columnLabels(columnsForMapping(mappingContext.target, 'target')),
    };
  }, [mappingContext]);

  // Old saved tasks can have a table but no sourceColumns. Opening the mapping
  // editor must load those columns, without requiring a visit to the source node.
  const mappingProbeKey = JSON.stringify(mappingContext ? missingMappingColumnProbes(mappingContext) : []);
  const [mappingProbeState, setMappingProbeState] = useState({ key: '', loading: false, error: '' });
  const [mappingProbeRetry, setMappingProbeRetry] = useState(0);
  const mappingColumnsLoading = mappingProbeKey !== '[]'
    && (mappingProbeState.key !== mappingProbeKey || mappingProbeState.loading);
  useEffect(() => {
    const requests = JSON.parse(mappingProbeKey) as ReturnType<typeof missingMappingColumnProbes>;
    if (!requests.length) {
      setMappingProbeState({ key: mappingProbeKey, loading: false, error: '' });
      return;
    }
    let cancelled = false;
    setMappingProbeState({ key: mappingProbeKey, loading: true, error: '' });
    Promise.allSettled(requests.map((request) => probeColumns(request.manifestKey, request.config, request.table)))
      .then((results) => {
        if (cancelled) return;
        const failures: string[] = [];
        results.forEach((result, index) => {
          const request = requests[index];
          const data = result.status === 'fulfilled' ? result.value : undefined;
          if (!data?.success || !Object.keys(columnLabels(data.columns)).length) {
            const reason = result.status === 'rejected' ? getApiErrorMessage(result.reason, '字段探查失败')
              : data?.error || '未探查到字段';
            failures.push(`${request.side === 'source' ? '来源表' : '目标表'} ${request.table}：${reason}`);
            return;
          }
          updateConfig(request.nodeId, request.side === 'source' ? {
            sourceColumns: data.columns,
            sourceColumnsByTable: { ...(request.config.sourceColumnsByTable as Record<string, unknown> ?? {}), [request.table]: data.columns },
          } : { targetColumns: data.columns });
        });
        setMappingProbeState({ key: mappingProbeKey, loading: false, error: failures.join('；') });
      });
    return () => { cancelled = true; };
  }, [mappingProbeKey, mappingProbeRetry, updateConfig]);

  const sqlContext = useMemo(() => {
    if (!node || !['transform.sql', 'transform.filter', 'branch.conditions'].includes(manifest?.key ?? '')) return undefined;
    const incomingEdges = Object.values(allEdges).filter((e) => e.target === node.id);
    const outgoingEdge = Object.values(allEdges).find((e) => e.source === node.id);
    return {
      sourceNode: incomingEdges[0] ? allNodes[incomingEdges[0].source] : null,
      sourceNodes: incomingEdges.map((e) => allNodes[e.source]).filter(Boolean),
      sinkNode: outgoingEdge ? allNodes[outgoingEdge.target] : null,
    };
  }, [allNodes, allEdges, node, manifest?.key]);

  // 获取同类别下的所有组件列表，用于切换数据源类型。
  const sameCategoryManifests = useMemo(() => {
    if (!manifests || !node) return [];
    return manifests.filter((m) => m.category === node.category);
  }, [manifests, node?.category]);

  // 切换组件类型。
  const [switchLoading, setSwitchLoading] = useState(false);
  const handleSwitchManifest = (newManifestKey: string) => {
    if (!node || !manifests) return;
    const newManifest = manifests.find((m) => m.key === newManifestKey);
    if (!newManifest || newManifest.key === node.manifestKey) return;

    setSwitchLoading(true);
    message.loading({ content: '正在切换组件类型...', key: 'switchManifest' });
    try {
      switchNodeManifest(node.id, newManifest);
      message.success({ content: `已切换为 ${newManifest.label}`, key: 'switchManifest' });
    } catch (e) {
      message.error({ content: '切换失败', key: 'switchManifest' });
    } finally {
      setSwitchLoading(false);
    }
  };

  const enhancedFields = useMemo(() => {
    const fields = (manifest?.fields ?? []).flatMap(expandIncrementalField);
    if (manifest?.category !== 'source' || !manifest.compile?.processors?.some(p => p.type.endsWith('.GenerateTableFetch'))) return fields;
    return [
      { key: 'syncMode', label: '同步方式', type: 'select', required: true, group: 'sync', options: syncModes.map(item => ({ value: item.value, label: item.label })), help: '修改后请保存并部署；启动使用已部署版本的同步方式。' },
      { key: 'fullSyncStrategy', label: '全量写入策略', type: 'select', default: 'UPSERT', group: 'sync', visibleWhen: { key: 'syncMode', equals: 'PERIODIC_FULL' }, options: [{ value: 'UPSERT', label: '有则更新、无则新增（需要业务主键/唯一键）' }, { value: 'TRUNCATE_RELOAD', label: '每轮清空重载（MySQL / 达梦）' }] },
      { key: 'deleteTargetData', label: '启动前删除目标表存量数据', type: 'switch', default: false, group: 'sync', visibleWhen: { key: 'syncMode', equals: 'FULL' }, help: '仅在明确启动时执行，保存或部署不会清理数据；启动前还须确认目标表。' },
      { key: 'fullOrderColumn', label: '全量分页排序字段', type: 'text', group: 'sync', help: '建议填写主键或稳定唯一字段；未指定且没有主键时，使用单条流式查询，避免无序分页漏数。', placeholder: '如 id，复合排序可用逗号分隔' },
      ...fields,
    ] as FieldSchema[];
  }, [manifest]);

  // The fourth registration step owns the source table's extraction timestamp.
  // Re-read it whenever a registered source node is opened so legacy canvases
  // receive the same value as a newly generated template. If registration has
  // no timestamp, keep any existing manual choice; new templates are blank and
  // therefore require the user to select a field explicitly.
  const registeredTimestampProbe = useRef('');
  useEffect(() => {
    if (!open || !node || node.category !== 'source' || !manifest?.fields?.some((field) => field.key === 'incrementalColumn')) {
      if (!open) registeredTimestampProbe.current = '';
      return;
    }
    const tableId = String(node.config.sourceTableId ?? '').trim();
    if (!tableId) return;
    const probeKey = `${node.id}:${node.manifestKey}:${tableId}`;
    if (registeredTimestampProbe.current === probeKey) return;
    registeredTimestampProbe.current = probeKey;
    let cancelled = false;
    void fetchNodeTemplate(node.manifestKey, tableId)
      .then(({ config }) => {
        if (cancelled) return;
        const registered = String(config.incrementalColumn ?? '').trim();
        const current = String(node.config.incrementalColumn ?? '').trim();
        if (registered && registered !== current) updateConfig(node.id, { incrementalColumn: registered });
      })
      .catch(() => {
        // Keep the editable local configuration if the registration metadata
        // service is temporarily unavailable. Opening the drawer again retries.
        if (!cancelled) registeredTimestampProbe.current = '';
      });
    return () => { cancelled = true; };
  }, [manifest?.fields, node, open, updateConfig]);

  useEffect(() => {
    if (!node || !open || enhancedFields.length === 0) return;
    const patch: Record<string, unknown> = {};
    if (node.category === 'source' && enhancedFields.some(field => field.key === 'syncMode') && !node.config.syncMode) {
      patch.syncMode = syncSettings(node.config).syncMode;
    }
    enhancedFields.forEach((field) => {
      if (field.default === undefined || node.config[field.key] !== undefined) return;
      // schedulingPeriod 在 Timer/Cron 下共用同一 config key，展开成两个字段。
      // 仅对当前可见的那个套用默认值，避免 Cron 的默认把 Timer 的「一天」覆盖掉。
      const isSchedulePeriod = field.key === 'schedulingPeriod' && field.group === 'schedule';
      if (isSchedulePeriod) {
        const prospectiveConfig = { ...node.config, ...patch };
        if (!isVisible(field, prospectiveConfig) || !isIncrementalVisible(field, prospectiveConfig)) return;
      }
      patch[field.key] = ['transform.field-mapping', 'transform.field-enrichment'].includes(manifest?.key ?? '') && field.key === 'mappings'
        ? FIELD_MAPPING_DEFAULT
        : field.default;
    });
    if (Object.keys(patch).length > 0) updateConfig(node.id, patch);
  }, [enhancedFields, node, open, updateConfig]);

  // Historical auto-generated JDBC targets predate the explicit writerType
  // property.  The drawer has always rendered their computed writer correctly,
  // but a visual fallback is not a persisted form value and was therefore
  // rejected by required-field validation on save.  Materialize that same
  // effective value as soon as the legacy node is opened.
  useEffect(() => {
    if (!open || !node || manifest?.key !== 'sink.jdbc') return;
    if (String(node.config.writerType ?? '').trim()) return;
    const patch: Record<string, unknown> = jdbcWriterDefaults(node.config.dbType);
    if (!String(node.config.statementType ?? '').trim()) {
      patch.statementType = jdbcWriterDefaults(node.config.dbType).statementType;
    }
    updateConfig(node.id, patch);
  }, [manifest?.key, node, open, updateConfig]);

  const grouped = useMemo(() => {
    if (!manifest?.fields) return [] as { key: string; label: string; fields: FieldSchema[] }[];
    const map = new Map<string, FieldSchema[]>();
    enhancedFields.forEach((f) => {
      const g = (f as FieldSchema & { group?: string }).group ?? 'default';
      if (!map.has(g)) map.set(g, []);
      map.get(g)!.push(f);
    });
    const order = ['default', 'connection', 'query', 'schema', 'incremental', 'schedule', 'advanced'];
    const sortedKeys = Array.from(map.keys()).sort((a, b) => {
      const ia = order.indexOf(a);
      const ib = order.indexOf(b);
      return (ia < 0 ? 999 : ia) - (ib < 0 ? 999 : ib);
    });
    return sortedKeys.map((k) => ({
      key: k,
      label: GROUP_LABELS[k] ?? k,
      fields: map.get(k)!,
    }));
  }, [enhancedFields, manifest?.fields]);

  const cat: ComponentCategory = node?.category ?? 'source';
  const headerIcon = COMPONENT_ICONS[manifest?.key ?? ''] ?? COMPONENT_ICONS[cat] ?? CAT_ICON[cat] ?? <AppstoreOutlined />;
  const isFieldMappingDrawer = ['transform.field-mapping', 'transform.field-enrichment'].includes(manifest?.key ?? '');
  const isCustomSqlDrawer = manifest?.key === 'transform.sql';
  const isFilterDrawer = manifest?.key === 'transform.filter';
  const isBranchDrawer = manifest?.key === 'branch.conditions';
  const isTopActionDrawer = isFieldMappingDrawer || isCustomSqlDrawer || isFilterDrawer || isBranchDrawer;

  // 本地状态用于节点名称输入。
  const [nodeName, setNodeName] = useState(node?.label ?? '');
  useEffect(() => {
    setNodeName(node?.label ?? '');
  }, [node?.label]);

  // Repair persisted canvases created before OceanBase compatibility modes
  // were classified ahead of generic Oracle/MySQL types.  Keep every existing
  // connection/table setting: only the manifest identity and the explicit
  // OceanBase mode are corrected.  Genuine Oracle nodes never match this
  // migration because their dbType remains Oracle.
  useEffect(() => {
    if (!open || !node || node.category !== 'source' || !manifests) return;
    const canonicalKey = normalizeSourceManifestKey(String(node.config.dbType ?? ''));
    if (canonicalKey !== 'source.oceanbase' || node.manifestKey === canonicalKey) return;
    const oceanBaseManifest = manifests.find((manifest) => manifest.key === canonicalKey);
    if (!oceanBaseManifest) return;
    const dbType = sinkDbType(String(node.config.dbType ?? ''));
    switchNodeManifest(node.id, oceanBaseManifest);
    updateConfig(node.id, {
      ...node.config,
      dbType,
      compatibleMode: dbType === 'OCEANBASE_ORACLE' ? 'ORACLE' : 'MYSQL',
    });
  }, [open, node, manifests, switchNodeManifest, updateConfig]);

  const [testing, setTesting] = useState(false);
  const [probing, setProbing] = useState(false);
  const [dbList, setDbList] = useState<DbDictItem[]>([]);
  const [dbLoading, setDbLoading] = useState(false);
  const [dbLoadError, setDbLoadError] = useState('');
  const [tableLoading, setTableLoading] = useState(false);
  const [tableLoadError, setTableLoadError] = useState('');
  const [metadataTables, setMetadataTables] = useState<MetadataTable[]>([]);
  const [materializeOpen, setMaterializeOpen] = useState(false);
  const [selectingDatabase, setSelectingDatabase] = useState(false);
  const databaseSelectionSequence = useRef(0);
  useEffect(() => {
    databaseSelectionSequence.current++;
    setSelectingDatabase(false);
    setMaterializeOpen(false);
  }, [node?.id, open]);
  const tableSearchTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const tableRequestSequence = useRef(0);
  const legacySourceProbeRef = useRef('');

  useEffect(() => {
    setMetadataTables([]);
    setTableLoadError('');
    setTableLoading(false);
    tableRequestSequence.current++;
    return () => {
      if (tableSearchTimer.current) clearTimeout(tableSearchTimer.current);
      tableRequestSequence.current++;
    };
  }, [open, node?.id, node?.manifestKey, node?.config.database, node?.config.dbName,
    node?.config.selectedDatabaseId, node?.config.hiveProfile, node?.config.jdbcUrl,
    node?.config.host, node?.config.port, node?.config.username, node?.config.password]);

  useEffect(() => {
    if (!open || !node || !(node.category === 'source' || node.category === 'sink')) return;
    setDbLoading(true);
    setDbLoadError('');
    fetchDatabases()
      .then(setDbList)
      .catch((e: any) => {
        const reason = e?.message ?? '数据库列表加载失败';
        setDbLoadError(reason);
        setDbList([]);
      })
      .finally(() => setDbLoading(false));
  }, [open, node?.category, message]);

  const targetDatabaseId = node ? resolveDatasourceId(node.config, dbList) : '';
  const materializeSources = useMemo(() => node ? upstreamTableSources(node.id, allNodes, allEdges) : [], [node?.id, allNodes, allEdges]);

  // Preserve loaded table/column settings while recognizing legacy task datasource IDs.
  useEffect(() => {
    if (!open || !node || !targetDatabaseId) return;
    if (node.config.selectedDatabaseId === targetDatabaseId && node.config.registeredDatasourceId === targetDatabaseId) return;
    updateConfig(node.id, { selectedDatabaseId: targetDatabaseId, registeredDatasourceId: targetDatabaseId });
  }, [open, node, targetDatabaseId, updateConfig]);

  const applyDatabaseSelection = useCallback(async (dbId: string) => {
    if (!node || !manifests) return;
    const listItem = dbList.find((db) => db.value === dbId || db.id === dbId);
    if (!listItem) return;
    const selectionSequence = ++databaseSelectionSequence.current;
    setSelectingDatabase(true);
    let item = listItem;
    try {
      const detail = await fetchDatabaseDetail(dbId);
      if (detail) {
        item = { ...listItem, ...detail };
      }
    } catch (e: any) {
      message.warning(`数据源连接信息读取失败，已保留下拉基础信息：${e?.message ?? String(e)}`);
    }
    if (selectionSequence !== databaseSelectionSequence.current) return;
    setSelectingDatabase(false);
    const sourceManifestKey = node.category === 'source'
      ? normalizeSourceManifestKey(item.dbType, item.ftpProtocol ?? item.protocol)
      : undefined;
    const normalizedDbType = sinkDbType(item.dbType, item.compatibleMode);
    if (!normalizedDbType || (node.category === 'source' && (!sourceManifestKey
      || !manifests.some((candidate) => candidate.key === sourceManifestKey)))) {
      message.error(`当前画布尚未支持此数据源类型：${item.dbType || '未登记类型'}`);
      return;
    }
    const isOracleSource = sourceManifestKey === 'source.oracle';
    const isOceanBaseSource = sourceManifestKey === 'source.oceanbase' || Boolean(normalizedDbType?.startsWith('OCEANBASE'));
    const isFtpSource = sourceManifestKey === 'source.ftp' || sourceManifestKey === 'source.sftp';
    const isApiSource = sourceManifestKey === 'source.api';
    // Keep the API definition tied to its registered logical table.  A
    // datasource can expose several APIs, therefore looking only at a flat
    // apiUrl would silently probe the wrong response schema.
    const apiDefinition = isApiSource ? apiPullItemForTable(item, node.config.table) : undefined;
    const apiTable = isApiSource
      ? firstText(apiDefinition?.tableName, node.config.table, node.config.tableName)
      : undefined;
    const host = isFtpSource ? (item.ftpHost || item.host) : item.host;
    const port = isFtpSource ? (item.ftpPort || item.port) : item.port;
    const database = item.database || item.dbName || item.label;
    // The registration screen has used both jdbcType and connectionType, and
    // older Oracle records commonly keep the endpoint in database.  Preserve
    // that contract only for real Oracle nodes; OceanBase Oracle mode has its
    // own JDBC semantics and must remain an OceanBase node.
    const oracleConnectionType = isOracleSource
      ? normalizeOracleConnectionType(item.connectionType ?? item.jdbcType)
      : undefined;
    const oracleService = isOracleSource
      ? firstText(item.sid, item.serviceName, database)
      : undefined;
    const declaredOceanBaseMode = String(item.compatibleMode ?? '').trim().toUpperCase();
    const oceanBaseCompatibleMode = isOceanBaseSource
      ? (declaredOceanBaseMode === 'ORACLE' || normalizedDbType === 'OCEANBASE_ORACLE' ? 'ORACLE' : 'MYSQL')
      : undefined;
    const isHuaweiMrsHive = normalizedDbType === 'HIVE'
      && (String(item.metadataAccessMode || '').toLowerCase() === 'server-managed-mrs'
        || String(item.hiveConnectionMode || '').toLowerCase() === 'huawei-mrs'
        || Boolean(item.hiveProfile)
        || String(item.authMode || '').toUpperCase() === 'KERBEROS');
    const usesServerManagedHiveProfile = isHuaweiMrsHive
      && Boolean(item.hiveProfile || String(item.metadataAccessMode || '').toLowerCase() === 'server-managed-mrs');
    const kerberos = String(item.authMode || '').toUpperCase() === 'KERBEROS';
    const basePatch: Record<string, unknown> = {
      selectedDatabaseId: dbId,
      // Keep the registered datasource reference and its server-managed MRS marker.
      // The backend re-loads this record under the active tenant before probing Hive.
      registeredDatasourceId: dbId,
      metadataAccessMode: item.metadataAccessMode,
      hiveConnectionMode: item.hiveConnectionMode,
      hiveProfile: item.hiveProfile,
      host,
      port: port ? Number(port) || port : undefined,
      database,
      username: isFtpSource
        ? (item.ftpUsername ?? item.username)
        : (item.username ?? (item as any).dbMetaUser),
      password: isFtpSource
        ? (item.ftpPassword ?? item.password)
        : (item.password ?? (item as any).dbMetaPassword),
      jdbcProperties: item.jdbcProperties,
      connectionType: oracleConnectionType,
      jdbcType: oracleConnectionType,
      sid: oracleService,
      serviceName: oracleService,
      compatibleMode: sourceManifestKey === 'source.kingbase' ? item.compatibleMode : oceanBaseCompatibleMode,
      tenant: isOceanBaseSource ? item.tenant : undefined,
      clusterId: isOceanBaseSource ? item.clusterId : undefined,
    };
    if (isHuaweiMrsHive) {
      Object.assign(basePatch, usesServerManagedHiveProfile ? {
        dbType: 'HIVE',
        hiveProfile: item.hiveProfile || 'default',
        metadataAccessMode: 'server-managed-mrs',
        hiveConnectionMode: 'huawei-mrs',
      } : {
        dbType: 'HIVE',
        authMode: item.authMode || 'KERBEROS',
        zookeeperQuorum: item.zookeeperQuorum,
        serviceDiscoveryMode: item.serviceDiscoveryMode || 'zooKeeper',
        zookeeperNamespace: item.zookeeperNamespace || 'hiveserver2',
        principal: item.principal,
        userPrincipal: item.userPrincipal,
        keytabPath: item.keytabPath,
        krb5ConfPath: item.krb5ConfPath,
        clientConfigDir: item.clientConfigDir,
        saslQop: item.saslQop || 'auth-conf',
        ssl: item.ssl,
        driverLocations: item.driverLocations,
        extraParams: item.extraParams,
        jdbcUrl: item.jdbcUrl || item.jdbcURL || buildHuaweiMrsHiveJdbcUrl(item, host, port, database),
      });
      if (usesServerManagedHiveProfile) {
        ['host', 'port', 'username', 'password', 'jdbcUrl', 'zookeeperQuorum', 'serviceDiscoveryMode',
          'zookeeperNamespace', 'principal', 'userPrincipal', 'keytabPath', 'krb5ConfPath',
          'clientConfigDir', 'saslQop', 'ssl', 'driverLocations', 'extraParams'].forEach((key) => delete basePatch[key]);
      }
      if (kerberos) {
        delete basePatch.username;
        delete basePatch.password;
      }
    }
    if (isFtpSource) {
      basePatch.hostname = host;
      basePatch.remotePath = item.ftpPath || '/';
      basePatch.fileFilterRegex = item.ftpFilePattern || '.*';
      basePatch.passiveMode = item.ftpPassiveMode ?? true;
        basePatch.protocol = item.ftpProtocol || item.protocol || (sourceManifestKey === 'source.sftp' ? 'sftp' : 'ftp');
      delete basePatch.database;
      delete basePatch.host;
    }
    if (isApiSource) {
      const url = apiPullUrl(apiDefinition, item.apiUrl);
      const method = firstText(apiDefinition?.endpointMethod, apiDefinition?.method, apiDefinition?.apiMethod, item.apiMethod, 'GET');
      const headers = apiPullHeaders(apiDefinition, item.commonHeadersJson ?? item.apiHeaders);
      const requestBody = firstText(apiDefinition?.requestTemplateJson, apiDefinition?.requestBody, apiDefinition?.apiBody, item.requestBody, item.apiBody);
      // The probe gateway accepts the api* aliases while the deployed NiFi
      // compiler consumes commonHeadersJson/requestBody.  Store both aliases
      // so testing, probing and deployment all exercise the same registration
      // contract.
      basePatch.url = url;
      basePatch.apiUrl = url;
      basePatch.method = method;
      basePatch.apiMethod = method;
      basePatch.contentType = firstText(apiDefinition?.contentType, item.contentType, 'application/json');
      basePatch.requestBody = requestBody;
      basePatch.apiBody = requestBody;
      basePatch.commonHeadersJson = headers;
      basePatch.apiHeaders = headers;
      basePatch.headers = headers;
      basePatch.apiCollectionPaths = apiDefinition?.apiCollectionPaths ?? apiDefinition?.collectionPaths ?? item.apiCollectionPaths;
      basePatch.collectionPaths = apiDefinition?.collectionPaths ?? apiDefinition?.apiCollectionPaths ?? item.apiCollectionPaths;
      basePatch.apiTimeoutSeconds = apiDefinition?.apiTimeoutSeconds ?? item.apiTimeoutSeconds;
      basePatch.responseConfigJson = apiDefinition?.responseConfigJson;
      basePatch.paginationConfigJson = apiDefinition?.paginationConfigJson;
      basePatch.incrementalConfigJson = apiDefinition?.incrementalConfigJson;
      basePatch.authHeader = item.authHeader;
      basePatch.connectTimeout = item.connectTimeout || '10 sec';
      basePatch.readTimeout = item.readTimeout || '30 sec';
      delete basePatch.database;
      delete basePatch.host;
      delete basePatch.port;
      delete basePatch.username;
      delete basePatch.password;
    }
    Object.keys(basePatch).forEach((key) => basePatch[key] === undefined && delete basePatch[key]);
    // Connection changes invalidate metadata from the previous database.
    Object.assign(basePatch, {
      // For API pull, the registered table name is the selected response
      // contract.  Retaining it lets “探查字段” immediately reuse the result of
      // registration steps 1/2 instead of asking for a JDBC-style table again.
      table: isApiSource ? apiTable : '', tableName: isApiSource ? apiTable : undefined, targetTable: undefined,
      sourceColumns: [], sourceColumnsByTable: {}, targetColumns: [], updateKeys: '',
      jdbcUrl: basePatch.jdbcUrl, username: basePatch.username, password: basePatch.password,
      hiveProfile: basePatch.hiveProfile, hiveConnectionMode: basePatch.hiveConnectionMode,
      metadataAccessMode: basePatch.metadataAccessMode, jdbcProperties: item.jdbcProperties,
      defaultSchema: item.schema || item.defaultSchema, schema: item.schema || item.defaultSchema,
    });
    if (node.category === 'sink') {
      const sinkKey = isHuaweiMrsHive ? 'sink.hive' : 'sink.jdbc';
      if (node.manifestKey !== sinkKey) {
        const sinkManifest = manifests.find((candidate) => candidate.key === sinkKey);
        if (sinkManifest) switchNodeManifest(node.id, sinkManifest);
      }
      // A manually configured Hive LOCATION belongs to the previous target.
      // Selecting another registered Hive database must not reuse that path.
      const previousDbId = String(node.config?.selectedDatabaseId || node.config?.registeredDatasourceId || '');
      if (sinkKey === 'sink.hive' && previousDbId !== String(dbId)) {
        basePatch.hdfsDirectory = '';
      }
      const dbType = normalizedDbType;
      basePatch.dbType = dbType;
      if (sinkKey === 'sink.jdbc') {
        Object.assign(basePatch, jdbcWriterDefaults(dbType));
      }
      const jdbcUrl = usesServerManagedHiveProfile ? undefined : (isHuaweiMrsHive
        ? (item.jdbcUrl || item.jdbcURL || buildHuaweiMrsHiveJdbcUrl(item, host, port, database))
        : (item.jdbcUrl || item.jdbcURL || buildJdbcUrl(dbType, host, port, database, item.connectionType ?? item.jdbcType)));
      if (jdbcUrl) basePatch.jdbcUrl = jdbcUrl;
      updateConfig(node.id, basePatch);
      message.success(`已选择目标库：${item.label || database || host}`);
      return;
    }
    const newManifestKey = sourceManifestKey;
    if (newManifestKey && newManifestKey !== node.manifestKey) {
      const newManifest = manifests.find((m) => m.key === newManifestKey);
      if (newManifest) switchNodeManifest(node.id, newManifest);
    }
    if (!isFtpSource && !isApiSource) {
      basePatch.dbType = normalizedDbType;
      const jdbcUrl = usesServerManagedHiveProfile ? undefined : (isHuaweiMrsHive
        ? (item.jdbcUrl || item.jdbcURL || buildHuaweiMrsHiveJdbcUrl(item, host, port, database))
        : (item.jdbcUrl || item.jdbcURL || buildJdbcUrl(item.dbType, host, port, database, item.connectionType ?? item.jdbcType)));
      if (jdbcUrl) basePatch.jdbcUrl = jdbcUrl;
    }
    updateConfig(node.id, basePatch);
    message.success(`已选择来源库：${item.label || database || host}`);
  }, [dbList, manifests, node, switchNodeManifest, updateConfig, message]);

  const refreshMetadataTables = useCallback(async (keyword = '') => {
    if (!node || !manifest || !(manifest.category === 'source' || manifest.category === 'sink')) return;
    const requestSequence = ++tableRequestSequence.current;
    const configError = hiveMetadataConfigError(manifest.key, node.config);
    if (configError) {
      setMetadataTables([]);
      setTableLoading(false);
      setTableLoadError(configError);
      return;
    }
    const isServerManagedHuaweiMrs =
      (manifest.key === 'source.hive' || manifest.key === 'sink.hive'
        || (manifest.key === 'sink.jdbc' && node.config.dbType === 'HIVE')) &&
      (Boolean(String(node.config.hiveProfile ?? '').trim()) ||
        String(node.config.metadataAccessMode ?? '').toLowerCase() === 'server-managed-mrs' ||
        String(node.config.hiveConnectionMode ?? '').toLowerCase() === 'huawei-mrs');
    // MRS Hive has no browser-side JDBC URL.  Both source and sink nodes use
    // the server-owned profile and the unified Magic metadata gateway.
    const tableHostReady = Boolean(targetDatabaseId) || (isServerManagedHuaweiMrs
      ? Boolean(String(node.config.database ?? node.config.dbName ?? '').trim())
      : manifest.category === 'sink'
      ? Boolean(node.config.jdbcUrl)
      : manifest.key === 'source.ftp' || manifest.key === 'source.sftp'
        ? Boolean(node.config.hostname && node.config.remotePath)
        : manifest.key === 'source.api'
          ? Boolean(node.config.url)
          : manifest.key === 'source.kafka'
            ? Boolean(node.config.bootstrapServers && node.config.topic)
            : Boolean(node.config.host && node.config.database));
    if (!tableHostReady) return;
    setTableLoading(true);
    setTableLoadError('');
    try {
      const result = await probeTables(manifest.key, node.config, keyword, 200);
      if (requestSequence !== tableRequestSequence.current) return;
      if (result.success) setMetadataTables(result.tables ?? []);
      else {
        setMetadataTables([]);
        setTableLoadError(result.error ?? '表元数据探查失败');
        message.warning({ key: 'metadata-table-probe', content: result.error ?? '表元数据探查失败' });
      }
    } catch (e: any) {
      if (requestSequence !== tableRequestSequence.current) return;
      const reason = getApiErrorMessage(e, '表元数据探查失败');
      setMetadataTables([]);
      setTableLoadError(reason);
      message.warning({ key: 'metadata-table-probe', content: `表元数据探查失败: ${reason}` });
    } finally {
      if (requestSequence === tableRequestSequence.current) setTableLoading(false);
    }
  }, [manifest, node, message]);

  const applyTableSelection = useCallback(async (table: string) => {
    if (!node || !manifest) return;
    const key = manifest.category === 'sink' ? 'table' : 'table';
    updateConfig(node.id, { [key]: table });
    if (!table) return;
    const selectionSequence = databaseSelectionSequence.current;
    setProbing(true);
    try {
      const result = await probeColumns(manifest.key, { ...node.config, [key]: table }, table);
      if (selectionSequence !== databaseSelectionSequence.current) return;
      if (!result.success) {
        message.warning(result.error ?? '未探查到字段');
        return;
      }
      if (manifest.category === 'source') {
        updateConfig(node.id, {
          table,
          sourceColumns: result.columns ?? [],
          sourceColumnsByTable: { [result.table ?? table]: result.columns ?? [] },
        });
        message.success(`已选择来源表 ${table}，字段已自动探查`);
      } else {
        updateConfig(node.id, { table, ...buildTargetColumnsPatch(node.config, result.columns ?? []) });
        message.success(`已选择目标表 ${table}，字段已自动探查`);
      }
    } catch (e: any) {
      message.error(`字段探查失败: ${getApiErrorMessage(e, '字段探查失败')}`);
    } finally {
      setProbing(false);
    }
  }, [manifest, node, updateConfig, message]);

  useEffect(() => {
    if (!open || !node || !manifest || manifest.category !== 'source' || !isStructuredSource(manifest.key)) return;
    const table = String(node.config.table ?? '').trim();
    const sourceColumns = Array.isArray(node.config.sourceColumns) ? node.config.sourceColumns : [];
    const probeKey = `${node.id}:${table}`;
    if (!table || sourceColumns.length > 0 || legacySourceProbeRef.current === probeKey) return;
    legacySourceProbeRef.current = probeKey;
    void applyTableSelection(table);
  }, [applyTableSelection, manifest, node, open]);

  const dbOptions = useMemo(() => dbList.map((item) => {
    const database = item.database || item.dbName;
    const hostText = [item.host, item.port].filter(Boolean).join(':');
    const displayLabel = `${item.label || database || item.host || item.value}${database ? ` (${database})` : ''}`;
    return {
      value: item.value || item.id,
      displayLabel,
      label: (
        <div className="db-inline-option">
          <span className="db-inline-option__name">{item.label || database || '-'}</span>
          {item.dbType && <span className="db-inline-option__type">{item.dbType}</span>}
          {(hostText || database) && (
            <span className="db-inline-option__meta">
              {[hostText, database].filter(Boolean).join(' / ')}
            </span>
          )}
        </div>
      ),
      searchText: dbOptionText(item),
    };
  }), [dbList]);

  const tableOptions = useMemo(() => metadataTables.map((item) => {
    const configuredSchema = String(
      node?.category === 'sink'
        ? node?.config?.username ?? node?.config?.database ?? ''
        : node?.config?.defaultSchema ?? node?.config?.database ?? '',
    ).trim();
    const schemaMatchesConnection = item.schemaName
      && configuredSchema
      && item.schemaName.toLowerCase() === configuredSchema.toLowerCase();
    // Hive's database is sent separately; do not put a qualified identifier
    // back into Table Name when choosing a suggestion.
    const value = manifest?.key === 'sink.hive' || manifest?.key === 'source.hive'
      || (node?.category === 'sink' && schemaMatchesConnection)
      ? item.tableName
      : item.schemaName ? `${item.schemaName}.${item.tableName}` : item.tableName;
    const meta = [item.schemaName, item.tableType].filter(Boolean).join(' / ');
    return {
      value,
      displayLabel: value,
      searchText: [item.schemaName, item.tableName, item.tableComment, item.tableType].filter(Boolean).join(' '),
      label: (
        <div className="table-inline-option">
          <div className="table-inline-option__main">
            <span className="table-inline-option__name">{item.tableName}</span>
            {item.schemaName && <span className="table-inline-option__schema">{item.schemaName}</span>}
          </div>
          <div className="table-inline-option__comment">{item.tableComment || '暂无中文注释'}</div>
          {meta && <div className="table-inline-option__meta">{meta}</div>}
        </div>
      ),
    };
  }), [metadataTables, manifest?.key, node?.category, node?.config?.database, node?.config?.defaultSchema, node?.config?.username]);

  const renderSmartField = (field: FieldSchema) => {
    if (!node || !manifest) return null;
    // Existing saved Hive targets predate hiveWriteMode. Render the manifest
    // default for them so the new default is visible immediately; deployment
    // also applies the same default when the legacy DSL has no explicit value.
    const value = field.key === 'initialStrategy' && syncSettings(node.config).syncMode === 'FULL_THEN_INCR'
      ? 'START_AT_BEGINNING'
      : manifest.key === 'sink.jdbc' && field.key === 'writerType'
      ? effectiveJdbcWriterType(node.config.dbType, node.config.writerType)
      : manifest.key === 'sink.jdbc' && field.key === 'statementType'
        && !String(node.config.statementType ?? '').trim()
        && effectiveJdbcWriterType(node.config.dbType, node.config.writerType) === LINEWELL_JDBC_WRITER
        ? 'MERGE'
      : node.config[field.key] ?? (
          (manifest.key === 'sink.hive' && field.key === 'hiveWriteMode')
            ? field.default
            : undefined
        );
    const disabled = isFieldReadOnly(field) || field.key === 'initialStrategy' && syncSettings(node.config).syncMode === 'FULL_THEN_INCR';
    const isDbAttachField = (manifest.category === 'source' && field.key === 'host')
      || (manifest.category === 'sink' && field.key === 'dbType');
    if (isDbAttachField) {
      return (
        <Space.Compact style={{ width: '100%' }}>
          <Select
            showSearch
            allowClear
            loading={dbLoading}
            placeholder="选择库"
            style={{ width: 220 }}
            value={targetDatabaseId || undefined}
            options={dbOptions}
            optionLabelProp="displayLabel"
            filterOption={(input, option) => String((option as any)?.searchText ?? option?.label ?? '').toLowerCase().includes(input.toLowerCase())}
            onChange={(v) => {
              if (v) {
                applyDatabaseSelection(String(v));
              } else {
                updateConfig(node.id, { selectedDatabaseId: undefined, registeredDatasourceId: undefined, targetDbId: undefined, sourceDbId: undefined, datasourceId: undefined, dataSourceId: undefined, dbId: undefined });
              }
            }}
            disabled={disabled}
            dropdownStyle={{ minWidth: 360 }}
            notFoundContent={dbLoadError ? (
              <div className="db-inline-empty is-error">
                <div className="db-inline-empty__title">数据库列表加载失败</div>
                <div className="db-inline-empty__desc">{dbLoadError}</div>
                <div className="db-inline-empty__tip">可以先手动填写连接信息，或稍后重新打开下拉框。</div>
              </div>
            ) : (
              <div className="db-inline-empty">
                {dbLoading ? '正在加载数据库列表...' : '暂无可选数据库'}
              </div>
            )}
          />
          <FieldEditor field={field} value={value} onChange={(v) => {
            const patch: Record<string, unknown> = { [field.key]: v };
            if (manifest.key === 'sink.jdbc' && field.key === 'dbType') {
              // Switching the target database is a new target context, not a
              // writer-only change.  Always reset both fields from its vendor
              // default so Oracle modes cannot retain native INSERT and other
              // databases cannot retain Linewell MERGE from a prior Oracle.
              Object.assign(patch, jdbcWriterDefaults(v));
            }
            updateConfig(node.id, patch);
          }} disabled={disabled} />
        </Space.Compact>
      );
    }
    const isTableField = (manifest.category === 'source' || manifest.category === 'sink')
      && ['table', 'tableName', 'targetTable'].includes(field.key);
    if (isTableField) {
      return (
        <AutoComplete
          allowClear
          value={String(value ?? '')}
          placeholder={field.placeholder ?? '输入或选择数据表'}
          options={tableOptions}
          dropdownRender={(menu) => <>
            {menu}
            {manifest.category === 'sink' && <>
              <Divider style={{ margin: '6px 0' }} />
              <Button type="text" block icon={<PlusOutlined />} style={{ textAlign: 'left', color: '#4f46e5' }}
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => {
                  if (!targetDatabaseId) {
                    message.warning('请先在“选择库”中选择已登记的目标库');
                    return;
                  }
                  setMaterializeOpen(true);
                }}>物化建表</Button>
            </>}
          </>}
          popupClassName="table-picker-dropdown"
          dropdownStyle={{ minWidth: 420 }}
          onFocus={() => {
            if (tableSearchTimer.current) clearTimeout(tableSearchTimer.current);
            refreshMetadataTables(String(value ?? ''));
          }}
          onChange={(text) => {
            updateConfig(node.id, { [field.key]: text });
            tableRequestSequence.current++;
            if (tableSearchTimer.current) clearTimeout(tableSearchTimer.current);
            tableSearchTimer.current = setTimeout(() => refreshMetadataTables(text), 300);
          }}
          onSelect={(selected) => {
            if (tableSearchTimer.current) clearTimeout(tableSearchTimer.current);
            tableRequestSequence.current++;
            setTableLoading(false);
            applyTableSelection(String(selected ?? ''));
          }}
          filterOption={(input, option) => String((option as any)?.searchText ?? option?.value ?? '')
            .toLowerCase()
            .includes(input.toLowerCase())}
          notFoundContent={tableLoading ? <Spin size="small" /> : tableLoadError || '可直接输入表名，候选列表会按输入内容匹配'}
          disabled={disabled || selectingDatabase}
        >
          <Input
            onPressEnter={(e) => applyTableSelection(e.currentTarget.value)}
            suffix={tableLoading || probing ? <Spin size="small" /> : null}
          />
        </AutoComplete>
      );
    }
    const isJdbcWriterType = manifest.key === 'sink.jdbc' && field.key === 'writerType';
    if (isJdbcWriterType) {
      return (
        <Select
          value={effectiveJdbcWriterType(node.config.dbType, node.config.writerType)}
          placeholder={field.placeholder}
          options={field.options}
          disabled={disabled}
          onChange={(nextValue) => {
            const patch: Record<string, unknown> = { [field.key]: nextValue };
            if (String(nextValue) === LINEWELL_JDBC_WRITER) {
              patch.statementType = 'MERGE';
              message.info('已选择 LinewellPutDatabaseRecord，写入方式已自动切换为合并（MERGE）');
            } else {
              // MERGE belongs solely to the Linewell custom processor. Reset
              // every native-writer switch to the safe append/insert mode.
              patch.statementType = 'INSERT';
              message.info('已切换为 NiFi 原生 PutDatabaseRecord，写入方式已自动切换为新增（INSERT）');
            }
            updateConfig(node.id, patch);
          }}
        />
      );
    }
    const isSinkWriteMode = manifest.key === 'sink.jdbc' && field.key === 'statementType';
    if (isSinkWriteMode) {
      const linewellWriter = effectiveJdbcWriterType(node.config.dbType, node.config.writerType)
        === LINEWELL_JDBC_WRITER;
      const options = field.options?.map((option) => !linewellWriter && option.value === 'MERGE'
          ? { ...option, disabled: true }
          : option);
      return (
        <Select
          value={value as string}
          placeholder={field.placeholder}
          options={options}
          allowClear
          disabled={disabled}
          onChange={(nextValue) => {
            if (String(nextValue) === 'MERGE' && !linewellWriter) {
              message.warning('合并（MERGE）仅支持 LinewellPutDatabaseRecord；请先切换目标表写入组件');
              return;
            }
            if (['UPSERT', 'MERGE', 'UPDATE', 'DELETE'].includes(String(nextValue))
              && !hasStableTargetKey(node.config.targetColumns)) {
              message.warning('请先探查目标表，并配置业务主键或唯一键后再选择有则更新、无则新增');
              return;
            }
            updateConfig(node.id, { [field.key]: nextValue });
          }}
        />
      );
    }
    // 调度策略切换：在 Timer / Cron 之间切换时，把共用的 schedulingPeriod 值
    // 在「周期（一天）」与「每天 Cron 表达式」之间自动转换，避免出现 1day 这种非法 Cron。
    const isScheduleStrategyField = field.key === 'schedulingStrategy' && field.group === 'schedule';
    if (isScheduleStrategyField) {
      const timerPeriodField = enhancedFields.find(
        (f) => f.key === 'schedulingPeriod' && f.type !== 'text',
      );
      const looksLikeCron = (v: unknown): boolean => /\*|\?/.test(String(v ?? ''));
      return (
        <Select
          value={value as string}
          placeholder={field.placeholder}
          options={field.options}
          disabled={disabled}
          onChange={(nextValue) => {
            const patch: Record<string, unknown> = { schedulingStrategy: nextValue };
            const current = node.config.schedulingPeriod;
            if (nextValue === 'CRON_DRIVEN' && !looksLikeCron(current)) {
              // 切到 Cron：把周期值（如 1day）替换为每天 0 点的 Cron 表达式。
              patch.schedulingPeriod = '0 0 0 * * ?';
            } else if (nextValue === 'TIMER_DRIVEN' && looksLikeCron(current)) {
              // 切回周期：把 Cron 表达式还原为后端默认的一天周期。
              patch.schedulingPeriod = timerPeriodField?.default ?? '1 day';
            }
            updateConfig(node.id, patch);
          }}
        />
      );
    }
    return (
      <FieldEditor
        field={field}
        value={value}
        onChange={(v) => updateConfig(node.id, field.key === 'syncMode'
          ? syncSettingsPatch(node.config, { ...syncSettings(node.config), syncMode: v as ReturnType<typeof syncSettings>['syncMode'], deleteTargetData: false })
          : { [field.key]: v })}
        disabled={disabled}
      />
    );
  };

  const handleTestConnection = async () => {
    if (!node || !manifest) return;
    setTesting(true);
    const hide = message.loading('正在测试连接...', 0);
    try {
      const r = await testConnection(manifest.key, node.config);
      hide();
      if (r.success) {
        message.success(
          `连接成功 (${r.product ?? 'DB'} ${r.version ?? ''}, 耗时 ${r.elapsedMs ?? '?'} ms)`,
          5,
        );
      } else {
        message.error(`连接失败: ${r.error ?? '未知错误'}`, 8);
      }
    } catch (e: any) {
      hide();
      message.error(`测试连接出错: ${getApiErrorMessage(e, '连接测试失败')}`, 8);
    } finally {
      setTesting(false);
    }
  };

  const tableListFromConfig = (config: Record<string, unknown>, category: ComponentCategory): string[] => {
    if (category === 'sink') {
      const table = String(config.table ?? config.tableName ?? config.targetTable ?? '').trim();
      return table ? [table] : [];
    }
    const mode = String(config.tableMode ?? 'SINGLE').toUpperCase();
    if (mode === 'MULTI') {
      const raw = String(config.tables ?? '').trim();
      return raw.split(/[,，\s\n]+/).map((v) => v.trim()).filter(Boolean);
    }
    const table = String(config.table ?? config.tableName ?? '').trim();
    return table ? [table] : [];
  };

  const handleProbeColumns = async () => {
    if (!node || !manifest) return;
    const tables = tableListFromConfig(node.config, manifest.category);
    if (tables.length === 0) {
      message.error(manifest.category === 'sink' ? '请先填写目标表名' : '请先填写来源表名');
      return;
    }
    const selectionSequence = databaseSelectionSequence.current;
    setProbing(true);
    const hide = message.loading('正在探查字段...', 0);
    try {
      const results: ColumnsProbeResult[] = [];
      const isFtpSource = manifest.category === 'source'
        && (manifest.key === 'source.ftp' || manifest.key === 'source.sftp');
      const registeredColumns = isFtpSource
        ? registeredSourceColumnsForTables(node.config, tables)
        : new Map<string, ProbedColumn[]>();

      // For a file-push task, the source directory may be empty until the
      // upstream system starts delivering files.  Its registered table schema
      // is still the intended access contract, so show it without treating an
      // empty delivery directory as a field-probe failure.
      for (const table of tables) {
        const columns = registeredColumns.get(table);
        if (columns) {
          results.push({ success: true, table, columns });
          continue;
        }
        const result = await probeColumns(manifest.key, node.config, table);
        if (!result.success) {
          throw new Error(table + ': ' + (result.error ?? '未探查到字段'));
        }
        results.push(result);
      }
      hide();
      if (selectionSequence !== databaseSelectionSequence.current) return;
      if (manifest.category === 'source') {
        const sourceColumnsByTable = Object.fromEntries(
          results.map((r) => [r.table ?? tables[results.indexOf(r)], r.columns]),
        );
        updateConfig(node.id, {
          sourceColumns: results[0]?.columns ?? [],
          sourceColumnsByTable,
        });
        const total = results.reduce((sum, r) => sum + (r.columns?.length ?? 0), 0);
        const reusedRegisteredSchema = isFtpSource && registeredColumns.size > 0;
        message.success(
          (reusedRegisteredSchema ? '已展示 FTP 已登记来源表 ' : '已探查 ')
            + results.length + ' 张来源表，共 ' + total + ' 个字段',
        );
        showProbeColumnsSummary(
          reusedRegisteredSchema ? 'FTP 已登记来源表字段' : '来源字段探查结果',
          results,
          tables,
        );
      } else {
        updateConfig(node.id, buildTargetColumnsPatch(node.config, results[0]?.columns ?? []));
        message.success('已探查目标表 ' + (results[0]?.table ?? tables[0]) + '，共 ' + (results[0]?.columns?.length ?? 0) + ' 个字段');
        showProbeColumnsSummary('目标字段探查结果', results, tables);
      }
    } catch (e: any) {
      hide();
      message.error(`字段探查失败: ${getApiErrorMessage(e, '字段探查失败')}`, 8);
    } finally {
      setProbing(false);
    }
  };

  // ---- 只读字段规则 ----
  // 流程运行中时，来源表和目标表字段只读，避免运行期间误改配置。
  const isFieldReadOnly = (_field: FieldSchema): boolean => {
    if (!manifest) return false;
    // 运行中：来源表和目标表不可编辑。
    if (canvasMode === 'MONITOR' && (manifest.category === 'source' || manifest.category === 'sink')) {
      return true;
    }
    // 其他情况：所有字段可编辑。
    return false;
  };

  // ---- 必填字段校验 ----
  const validateRequired = (): string[] => {
    if (!node || !manifest) return [];
    const missing: string[] = [];
    enhancedFields.forEach((field) => {
      if (!field.required) return;
      // 跳过不可见字段。
      if (!isVisible(field, node.config)) return;
      if (!isIncrementalVisible(field, node.config)) return;
      // 跳过只读字段。
      if (isFieldReadOnly(field)) return;
      // 跳过开关字段，false 也是有效值。
      if (field.type === 'switch') return;
      const v = manifest.key === 'sink.jdbc' && field.key === 'writerType'
        ? effectiveJdbcWriterType(node.config.dbType, node.config.writerType)
        : node.config[field.key] ?? field.default;
      if (v === undefined || v === null || v === '') {
        missing.push(field.label);
      }
    });
    return missing;
  };

  const handleSave = () => {
    if (!node || !manifest) return;
    if (manifest.category === 'source' && enhancedFields.some(field => field.key === 'syncMode')) {
      const error = syncSettingsError(node.config, syncSettings(node.config));
      if (error) { message.error(error); return false; }
    }
    const missing = validateRequired();
    if (missing.length > 0) {
      message.error('请填写必填项：' + missing.join('、'), 5);
      // 自动滚动到第一个缺少的必填字段。
      const firstMissing = enhancedFields.find((f) => {
        if (!f.required) return false;
        if (!isVisible(f, node.config)) return false;
        if (!isIncrementalVisible(f, node.config)) return false;
        if (isFieldReadOnly(f)) return false;
        if (f.type === 'switch') return false;
        const v = manifest.key === 'sink.jdbc' && f.key === 'writerType'
          ? effectiveJdbcWriterType(node.config.dbType, node.config.writerType)
          : node.config[f.key] ?? f.default;
        return v === undefined || v === null || v === '';
      });
      if (firstMissing) {
        // 滚动到第一个缺少的必填字段。
        setTimeout(() => {
          const el = document.querySelector(`[data-field-key="${firstMissing.key}"]`);
          if (el) {
            el.scrollIntoView({ behavior: 'smooth', block: 'center' });
          }
        }, 150);
      }
      return false;
    }
    return true;
  };

  const renderFields = (fields: FieldSchema[]) => {
    if (!node || !manifest) return null;
    const isUnifiedFieldMapping = ['transform.field-mapping', 'transform.field-enrichment'].includes(manifest.key);
    return (
      <Form layout="vertical" size="middle">
        {fields.filter((f) => {
          if (manifest.key === 'transform.sql' && f.key === 'executionMode') return false;
          // The unified field component exposes its mode beside the rule editor;
          // rendering it as a normal form row wastes a full line of vertical space.
          if (isUnifiedFieldMapping && f.key === 'executionMode') return false;
          return isVisible(f, node.config) && isIncrementalVisible(f, node.config);
        }).map((field) => (
          <Form.Item
            key={field.key}
            label={
              (manifest.key === 'transform.sql' && field.key === 'sql')
              || (isUnifiedFieldMapping && field.key === 'mappings')
                ? null
                : field.label
            }
            required={
              (manifest.key === 'transform.sql' && field.key === 'sql')
              || (isUnifiedFieldMapping && field.key === 'mappings')
                ? false
                : field.required
            }
            help={['transform.field-mapping', 'transform.field-enrichment'].includes(manifest.key) && field.key === 'mappings' ? undefined : field.help}
            style={{ marginBottom: 14 }}
            data-field-key={field.key}
          >
            {['transform.field-mapping', 'transform.field-enrichment'].includes(manifest.key) && field.key === 'mappings' ? (
              <div className="field-mapping-unified-editor">
                <FieldMappingEditor
                  value={node.config[field.key] ?? FIELD_MAPPING_DEFAULT}
                  onChange={(v) => updateConfig(node.id, { [field.key]: v })}
                  columnMeta={columnMeta}
                  columnsLoading={mappingColumnsLoading}
                  columnsError={mappingProbeState.key === mappingProbeKey ? mappingProbeState.error : ''}
                  onReloadColumns={() => setMappingProbeRetry((value) => value + 1)}
                />
              </div>
            ) : manifest.key === 'transform.sql' && field.key === 'sql' ? (
              <CustomSqlEditor
                key={node.id}
                ref={sqlEditorRef}
                onGeneratingChange={setSqlGenerating}
                value={String(node.config[field.key] ?? field.default ?? '')}
                onChange={(v) => updateConfig(node.id, { [field.key]: v })}
                executionMode={node.config.executionMode}
                onExecutionModeChange={(v) => updateConfig(node.id, { executionMode: v })}
                sourceNode={sqlContext?.sourceNode}
                sourceNodes={sqlContext?.sourceNodes}
                sinkNode={sqlContext?.sinkNode}
                onOutputColumnsChange={(outputColumns) => {
                  const current = Array.isArray(node.config.outputColumns) ? node.config.outputColumns : [];
                  if (JSON.stringify(current) !== JSON.stringify(outputColumns)) updateConfig(node.id, { outputColumns });
                }}
              />
            ) : manifest.key === 'transform.filter' && field.key === 'whereClause' ? (
              <FilterEditor
                value={String(node.config[field.key] ?? field.default ?? '')}
                onChange={(v) => updateConfig(node.id, { [field.key]: v })}
                rules={Array.isArray(node.config.filterRules) ? node.config.filterRules as BusinessCondition[] : []}
                onRulesChange={(filterRules) => updateConfig(node.id, { filterRules })}
                sourceNodes={sqlContext?.sourceNodes}
                keepMode={node.config.keepMode === 'UNMATCHED' ? 'UNMATCHED' : 'MATCHED'}
                onKeepModeChange={(keepMode) => updateConfig(node.id, { keepMode })}
              />
            ) : manifest.key === 'branch.conditions' && field.key === 'routes' ? (
              <BranchEditor
                routes={Array.isArray(node.config.routes) ? node.config.routes as BranchRoute[] : []}
                onChange={(routes) => updateConfig(node.id, { routes })}
                sourceNodes={sqlContext?.sourceNodes}
                defaultRouteName={String(node.config.defaultRouteName ?? '其他')}
                onDefaultRouteNameChange={(defaultRouteName) => updateConfig(node.id, { defaultRouteName })}
              />
            ) : manifest.key === 'branch.conditions' && field.key === 'defaultRouteName' ? null : (
              renderSmartField(field)
            )}
          </Form.Item>
        ))}
      </Form>
    );
  };

  return (
    <Drawer
      open={open}
      getContainer={getNifiOverlayContainer}
      onClose={() => openDrawer(null)}
      width={isFieldMappingDrawer || isCustomSqlDrawer || isFilterDrawer || isBranchDrawer ? 'min(1180px, calc(100vw - 32px))' : 460}
      placement="right"
      mask={false}
      closable={false}
      rootClassName={isFieldMappingDrawer ? 'field-mapping-config-drawer' : isCustomSqlDrawer ? 'custom-sql-config-drawer' : isFilterDrawer ? 'filter-config-drawer' : isBranchDrawer ? 'branch-config-drawer' : undefined}
      styles={{
        body: { padding: 0, display: 'flex', flexDirection: 'column' },
        wrapper: { boxShadow: '-8px 0 24px -8px rgba(0,0,0,0.08)' },
      }}
    >
      {!node || !bodyReady ? (
        <div style={{ padding: 24, display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: 200 }}>
          <Spin>
            <div style={{ padding: 32, color: 'rgba(0,0,0,0.45)' }}>加载配置...</div>
          </Spin>
        </div>
      ) : !manifest ? (
        <div style={{ padding: 24, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: 200, gap: 8 }}>
          <span style={{ color: 'rgba(0,0,0,0.45)', fontSize: 13 }}>
            未找到组件定义：<code>{node.manifestKey}</code>
          </span>
          <Button size="small" onClick={() => openDrawer(null)}>关闭</Button>
        </div>
      ) : (
        <>
          <div className="coze-drawer-header">
            <div className={`coze-drawer-header__icon cat-${cat}`}>{headerIcon}</div>
            <div className="coze-drawer-header__title">
              <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <Input
                  value={nodeName}
                  onChange={(e) => {
                    setNodeName(e.target.value);
                    setLabel(node.id, e.target.value);
                  }}
                  variant="borderless"
                  style={{ fontWeight: 600, fontSize: 16, padding: 0, color: 'var(--text-primary)', flex: 1, minWidth: 100 }}
                  placeholder="输入节点名称"
                />
              </div>
              <div className="coze-drawer-header__key">{manifest.key}</div>
            </div>
            {isFieldMappingDrawer && (
              <div className="field-mapping-header-execution-mode">
                <span>执行方式</span>
                <Segmented
                  size="small"
                  value={String(node.config.executionMode ?? 'AUTO')}
                  options={[
                    { label: '自动优化', value: 'AUTO' },
                    { label: '来源 SQL', value: 'SOURCE_DB' },
                    { label: '记录处理', value: 'RECORD' },
                  ]}
                  onChange={(executionMode) => updateConfig(node.id, { executionMode })}
                />
                <Tooltip title="自动模式会在规则完全兼容来源数据库时合并为一条 SQL；含 NiFi 表达式、跨库字典或需要失败路由时自动使用记录处理。">
                  <span className="field-mapping-execution-tip">?</span>
                </Tooltip>
              </div>
            )}
            {/* 切换组件类型，仅当同类别有多个组件时显示。 */}
            {sameCategoryManifests.length > 1 && (
              <Tooltip title="切换为其他同类组件，配置将重置">
                <Select
                  value={node.manifestKey}
                  onChange={handleSwitchManifest}
                  loading={switchLoading}
                  style={{ width: 160, marginRight: 8 }}
                  placeholder="切换组件"
                  suffixIcon={<SwapOutlined />}
                  options={sameCategoryManifests.map((m) => ({
                    value: m.key,
                    label: m.label,
                  }))}
                />
              </Tooltip>
            )}
            {isCustomSqlDrawer && (
              <Button loading={sqlGenerating} disabled={canvasMode !== 'EDIT' || !bodyReady}
                onClick={() => sqlEditorRef.current?.generate()}>
                自动生成语句
              </Button>
            )}
            {isTopActionDrawer && (
              <Button
                type="primary"
                icon={<SaveOutlined />}
                onClick={() => {
                  if (!handleSave()) return;
                  message.success('组件配置已保存');
                  openDrawer(null);
                }}
              >
                保存
              </Button>
            )}
            <Button type="text" icon={<CloseOutlined />} onClick={() => openDrawer(null)} />
          </div>

          <div className="coze-drawer-body">
            {manifest.description && !isFieldMappingDrawer && (
              <div style={{ color: 'var(--text-tertiary)', fontSize: 12, marginBottom: 12 }}>
                {manifest.description}
              </div>
            )}
            {/* 按配置分组展示字段。 */}
            {grouped.length === 1 ? renderFields(grouped[0].fields) : (
              <Collapse
                defaultActiveKey={grouped.map((g) => g.key)}
                ghost
                size="small"
                items={grouped.map((g) => ({
                  key: g.key,
                  label: <span style={{ fontWeight: 500 }}>{g.label}</span>,
                  children: renderFields(g.fields),
                }))}
              />
            )}
          </div>

          {!isTopActionDrawer && (
            <div className="coze-drawer-footer">
              <Space>
                {(cat === 'source' || cat === 'sink') && (
                  <>
                    <Button icon={<ExperimentOutlined />} loading={testing} disabled={selectingDatabase} onClick={handleTestConnection}>
                      测试连接
                    </Button>
                    <Button loading={probing} disabled={selectingDatabase} onClick={handleProbeColumns}>
                      探查字段
                    </Button>
                  </>
                )}
                <Button onClick={() => openDrawer(null)}>关闭</Button>
                <Button
                  type="primary"
                  icon={<SaveOutlined />}
                  onClick={() => {
                    if (!handleSave()) return;
                    message.success('组件配置已保存');
                    openDrawer(null);
                  }}
                >
                  保存
                </Button>
              </Space>
            </div>
          )}
        </>
      )}
      {node && <MaterializeTableModal key={`${node.id}:${targetDatabaseId}`}
        open={materializeOpen} dbId={targetDatabaseId} sources={materializeSources}
        initialTableName={String(node.config.table || node.config.tableName || node.config.targetTable || '')}
        databaseType={String(node.config.dbType || (node.manifestKey === 'sink.hive' ? 'hive' : ''))}
        databaseLabel={dbList.find((db) => (db.value || db.id) === targetDatabaseId)?.label
          || String(node.config.database || node.config.jdbcUrl || '未选择目标库')}
        onClose={() => setMaterializeOpen(false)} onCreated={async (table) => {
          await refreshMetadataTables('');
          await applyTableSelection(table);
        }} />}
    </Drawer>
  );
}
