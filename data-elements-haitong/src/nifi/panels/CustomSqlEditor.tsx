import { forwardRef, useEffect, useImperativeHandle, useMemo, useRef, useState } from 'react';
import { App, Badge, Divider, Modal, Select, Tag, Tooltip, Typography } from 'antd';
import { CheckCircleOutlined, DatabaseOutlined, ExclamationCircleOutlined, PlayCircleOutlined, TableOutlined } from '@ant-design/icons';
import CodeMirror, { type ReactCodeMirrorRef } from '@uiw/react-codemirror';
import { sql } from '@codemirror/lang-sql';
import { placeholder } from '@codemirror/view';
import type { CanvasNode } from '@/types/dsl';
import { probeColumns } from '@/api/pipelines';
import { recommendFieldMapping } from '@/api/fieldMapping';
import { getApiErrorMessage } from '@/api/response';
import { generateSelectSql, quoteSqlIdentifier, type SqlColumn } from './customSqlGeneration';
import { insertSqlField, replaceSqlStatement } from './sqlEditorTransactions';
import { getNifiOverlayContainer } from './overlayContainer';

const { Text } = Typography;

interface CustomSqlEditorProps {
  value: string;
  onChange: (value: string) => void;
  executionMode?: unknown;
  onExecutionModeChange: (value: string) => void;
  sourceNode?: CanvasNode | null;
  sourceNodes?: CanvasNode[];
  sinkNode?: CanvasNode | null;
  onOutputColumnsChange?: (columns: Array<{ columnName: string }>) => void;
  onGeneratingChange?: (generating: boolean) => void;
}

export interface CustomSqlEditorHandle { generate: () => void }
type ColumnInfo = SqlColumn;
type SourceTableInfo = { key: string; table: string; columns: ColumnInfo[]; node: CanvasNode; recordInput?: boolean };

const SQL_PLACEHOLDER = `-- 自定义 SQL 使用示例，按实际场景修改
-- 场景 1：对上游记录继续加工，FROM 固定写 FLOWFILE
SELECT *
FROM FLOWFILE;

-- 场景 2：直接在来源库聚合查询，字段别名建议与目标字段一致
SELECT data_type AS username, COUNT(*) AS num
FROM da_prop_t
GROUP BY data_type;

-- 场景 3：来源库多表 JOIN / 字典表查询
SELECT a.id, a.name, d.dict_name AS status_name
FROM table_a a
LEFT JOIN dict_table d ON d.dict_value = a.status;

-- 场景 4：先执行清理语句，再用最后一条 SELECT/WITH 输出结果
DELETE FROM target_table;
SELECT username, num
FROM source_table;`;

function normalizeColumns(raw: unknown) {
  if (!Array.isArray(raw)) return [] as ColumnInfo[];
  return raw
    .map((col: any) => ({
      name: String(col?.columnName ?? col?.name ?? '').trim(),
      comment: String(col?.columnComment ?? col?.comment ?? '').trim(),
      dataType: String(col?.dataType ?? col?.typeName ?? '').trim(),
    }))
    .filter((col) => col.name);
}

function columnsFrom(node?: CanvasNode | null, key = 'sourceColumns') {
  return normalizeColumns(node?.config?.[key]);
}

function tableName(node?: CanvasNode | null) {
  const cfg = node?.config ?? {};
  return String(cfg.table ?? cfg.tableName ?? cfg.targetTable ?? '').trim();
}

function sourceType(node: CanvasNode) {
  return `${node.config.dbType || node.manifestKey.replace(/^source\./, '')} ${node.config.compatibleMode || ''}`;
}

function sourceTablesFrom(nodes: CanvasNode[]) {
  const tables: SourceTableInfo[] = [];
  nodes.forEach((node) => {
    const outputColumns = columnsFrom(node, 'outputColumns');
    if (outputColumns.length > 0) {
      tables.push({ key: node.id, table: node.label || 'SQL 输出', columns: outputColumns, node, recordInput: true });
      return;
    }
    const raw = node.config.table || node.config.tableName || node.config.tables || node.config.sourceTables;
    const configuredNames = (Array.isArray(raw) ? raw : String(raw ?? '').split(/[,;\n]/)).map(String).map((name) => name.trim()).filter(Boolean);
    const byTable = node.config.sourceColumnsByTable as Record<string, unknown> | undefined;
    const names = Array.from(new Set([...configuredNames, ...Object.keys(byTable ?? {})]));
    names.forEach((table) => {
      const perTable = normalizeColumns(byTable?.[table]);
      tables.push({ key: `${node.id}:${table}`, table, node,
        columns: perTable.length ? perTable : names.length === 1 ? columnsFrom(node) : [] });
    });
  });
  const seen = new Set<string>();
  return tables.filter((item) => {
    const key = item.key;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function stripIdentifier(value: string) {
  return value.trim().replace(/^[`"\[]|[`"\]]$/g, '');
}

function splitSqlStatements(sqlText: string) {
  const statements: string[] = [];
  let current = '';
  let quote: string | null = null;
  for (let i = 0; i < sqlText.length; i += 1) {
    const ch = sqlText[i];
    const next = sqlText[i + 1];
    if (quote) {
      current += ch;
      if (ch === quote) quote = null;
      continue;
    }
    if (ch === '-' && next === '-') {
      const end = sqlText.indexOf('\n', i + 2);
      current += end >= 0 ? sqlText.slice(i, end + 1) : sqlText.slice(i);
      if (end < 0) break;
      i = end;
      continue;
    }
    if (ch === '\'' || ch === '"' || ch === '`') {
      quote = ch;
      current += ch;
      continue;
    }
    if (ch === ';') {
      if (current.trim()) statements.push(current.trim());
      current = '';
    } else {
      current += ch;
    }
  }
  if (current.trim()) statements.push(current.trim());
  return statements;
}

function lastSelectStatement(sqlText: string) {
  const statements = splitSqlStatements(sqlText);
  return [...statements].reverse().find((stmt) => /^\s*(with|select)\b/i.test(stmt)) ?? sqlText;
}

function findTopLevelSelectList(sqlText: string) {
  const text = sqlText.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/--.*$/gm, ' ');
  const lower = text.toLowerCase();
  let depth = 0;
  let quote: string | null = null;
  let selectStart = -1;
  for (let i = 0; i < text.length; i += 1) {
    const ch = text[i];
    if (quote) {
      if (ch === quote) quote = null;
      continue;
    }
    if (ch === '\'' || ch === '"' || ch === '`') {
      quote = ch;
      continue;
    }
    if (ch === '(') depth += 1;
    if (ch === ')') depth = Math.max(0, depth - 1);
    if (depth === 0 && lower.startsWith('select', i) && !/[\w]/.test(lower[i - 1] ?? '') && !/[\w]/.test(lower[i + 6] ?? '')) {
      selectStart = i + 6;
      break;
    }
  }
  if (selectStart < 0) return '';
  depth = 0;
  quote = null;
  for (let i = selectStart; i < text.length; i += 1) {
    const ch = text[i];
    if (quote) {
      if (ch === quote) quote = null;
      continue;
    }
    if (ch === '\'' || ch === '"' || ch === '`') {
      quote = ch;
      continue;
    }
    if (ch === '(') depth += 1;
    if (ch === ')') depth = Math.max(0, depth - 1);
    if (depth === 0 && lower.startsWith('from', i) && !/[\w]/.test(lower[i - 1] ?? '') && !/[\w]/.test(lower[i + 4] ?? '')) {
      return text.slice(selectStart, i);
    }
  }
  return text.slice(selectStart);
}

function projectedColumnNames(sqlText: string) {
  const names = new Set<string>();
  const selectList = findTopLevelSelectList(lastSelectStatement(sqlText));
  if (!selectList) return names;
  const parts: string[] = [];
  let current = '';
  let depth = 0;
  let quote: string | null = null;
  for (const ch of selectList) {
    if (quote) {
      current += ch;
      if (ch === quote) quote = null;
      continue;
    }
    if (ch === '\'' || ch === '"' || ch === '`') {
      quote = ch;
      current += ch;
      continue;
    }
    if (ch === '(') depth += 1;
    if (ch === ')') depth = Math.max(0, depth - 1);
    if (ch === ',' && depth === 0) {
      parts.push(current.trim());
      current = '';
    } else {
      current += ch;
    }
  }
  if (current.trim()) parts.push(current.trim());
  parts.forEach((part) => {
    const asMatch = /\bas\s+([`"\[]?[\w\u4e00-\u9fa5]+[`"\]]?)\s*$/i.exec(part);
    if (asMatch) {
      names.add(stripIdentifier(asMatch[1]));
      return;
    }
    const tailMatch = /(?:^|[\s.])([`"\[]?[\w\u4e00-\u9fa5]+[`"\]]?)\s*$/.exec(part);
    if (tailMatch && !/[()]/.test(part)) {
      names.add(stripIdentifier(tailMatch[1]));
    }
  });
  return names;
}

const CustomSqlEditor = forwardRef<CustomSqlEditorHandle, CustomSqlEditorProps>(function CustomSqlEditor({
  value,
  onChange,
  executionMode,
  onExecutionModeChange,
  sourceNode,
  sourceNodes,
  sinkNode,
  onOutputColumnsChange,
  onGeneratingChange,
}, ref) {
  const { message } = App.useApp();
  const editorRef = useRef<ReactCodeMirrorRef>(null);
  const generationSequence = useRef(0);
  const generationBusy = useRef(false);
  const [choosingSource, setChoosingSource] = useState(false);
  const [selectedSource, setSelectedSource] = useState('');
  const [probedColumns, setProbedColumns] = useState<Record<string, ColumnInfo[]>>({});
  const [probedTargets, setProbedTargets] = useState<ColumnInfo[]>([]);
  const mode = typeof executionMode === 'string' && executionMode ? executionMode : 'AUTO';
  const upstreamSources = sourceNodes?.length ? sourceNodes : sourceNode ? [sourceNode] : [];
  const sourceTables = useMemo(() => sourceTablesFrom(upstreamSources).map((item) => ({ ...item,
    columns: item.columns.length ? item.columns : probedColumns[item.key] ?? [],
  })), [upstreamSources, probedColumns]);
  const targetColumns = useMemo(() => {
    const columns = columnsFrom(sinkNode, 'targetColumns');
    return columns.length ? columns : probedTargets;
  }, [sinkNode, probedTargets]);
  const projectedNames = useMemo(() => projectedColumnNames(value ?? ''), [value]);
  const projectedKeys = new Set(Array.from(projectedNames, (name) => name.toLowerCase()));
  const projectedSignature = Array.from(projectedNames).sort().join('|');

  useEffect(() => {
    onOutputColumnsChange?.(Array.from(projectedNames).map((columnName) => ({ columnName })));
    // Callback identity changes with drawer renders; SQL output only changes with the projection signature.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectedSignature]);

  useEffect(() => {
    if (mode !== 'AUTO') onExecutionModeChange('AUTO');
  }, [mode, onExecutionModeChange]);

  useEffect(() => () => { generationSequence.current += 1; }, []);

  const insertAtCursor = (name: string, databaseType: string) => {
    const view = editorRef.current?.view;
    if (!view) return;
    const identifier = /^[A-Za-z_][A-Za-z0-9_]*$/.test(name) ? name : quoteSqlIdentifier(name, databaseType);
    view.dispatch(insertSqlField(view.state, identifier));
    view.focus();
  };

  const generate = async (source: SourceTableInfo) => {
    if (generationBusy.current) return;
    generationBusy.current = true;
    onGeneratingChange?.(true);
    const sequence = ++generationSequence.current;
    const active = () => sequence === generationSequence.current;
    try {
      let columns = source.columns;
      if (!columns.length) {
        const result = await probeColumns(source.node.manifestKey, source.node.config, source.table);
        if (!active()) return;
        columns = normalizeColumns(result.columns);
        if (!result.success || !columns.length) throw new Error(result.error || '来源表未探查到字段');
        setProbedColumns((previous) => ({ ...previous, [source.key]: columns }));
      }
      let targets = targetColumns;
      if (!targets.length && sinkNode && tableName(sinkNode)) {
        const result = await probeColumns(sinkNode.manifestKey, sinkNode.config, tableName(sinkNode));
        if (!active()) return;
        targets = normalizeColumns(result.columns);
        if (!result.success || !targets.length) throw new Error(result.error || '目标表未探查到字段');
        setProbedTargets(targets);
      }
      const schema = (items: ColumnInfo[]) => items.map((col) => ({ name: col.comment || col.name, path: `/${col.name}`, type: col.dataType }));
      const recommended = targets.length ? await recommendFieldMapping(schema(columns), schema(targets)) : null;
      if (!active()) return;
      const result = generateSelectSql(source.table, columns, targets, sourceType(source.node), recommended?.recommendations, source.recordInput);
      const view = editorRef.current?.view;
      if (!view) return;
      view.dispatch(replaceSqlStatement(view.state, result.sql));
      view.focus();
      if (targets.length && result.unmatched.length) {
        message.info(`已生成 ${columns.length} 个字段，匹配 ${result.mappedCount} 个目标字段；其余 ${result.unmatched.length} 个未添加别名`);
      } else message.success(`已生成 ${columns.length} 个字段的查询语句${targets.length ? `，匹配 ${result.mappedCount} 个目标字段` : ''}`);
    } catch (error) {
      if (active()) message.error(getApiErrorMessage(error, '自动生成语句失败'));
    } finally {
      if (active()) { generationBusy.current = false; onGeneratingChange?.(false); }
    }
  };

  useImperativeHandle(ref, () => ({ generate: () => {
    if (generationBusy.current) return;
    if (!sourceTables.length) { message.warning('请先连接并配置来源表'); return; }
    if (sourceTables.length === 1) { void generate(sourceTables[0]); return; }
    if (!sourceTables.some((item) => item.key === selectedSource)) setSelectedSource(sourceTables[0].key);
    setChoosingSource(true);
  } }));

  return (
    <div className="custom-sql-editor">
      <div className="custom-sql-editor__meta">
        <div>
          <Text type="secondary"><DatabaseOutlined /> 来源表字段</Text>
          <div className="custom-sql-editor__tables">
            {sourceTables.length === 0 ? (
              <Text type="secondary">暂无字段，请先在来源库配置中探查字段</Text>
            ) : sourceTables.map((item) => (
              <div key={item.key} className="custom-sql-editor__table-block">
                <Tag icon={<TableOutlined />} color="default">{item.table}</Tag>
                <div className="custom-sql-editor__chips">
                  {item.columns.slice(0, 40).map((col) => (
                    <Tooltip key={`${item.table}.${col.name}`} title={[col.comment, col.dataType].filter(Boolean).join(' / ') || col.name}>
                      <Tag onMouseDown={(event) => event.preventDefault()} onClick={() => {
                        insertAtCursor(col.name, item.recordInput ? 'calcite' : sourceType(item.node));
                      }}>
                        {col.name}
                      </Tag>
                    </Tooltip>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
        {targetColumns.length > 0 && (
          <div>
            <Text type="secondary"><PlayCircleOutlined /> 目标字段</Text>
            <div className="custom-sql-editor__chips">
              {targetColumns.map((col) => {
                const matched = projectedKeys.has(col.name.toLowerCase());
                return (
                  <Tooltip key={col.name} title={matched ? 'SQL 已输出该字段' : 'SQL 尚未输出该字段'}>
                    <Tag
                      color={matched ? 'success' : 'warning'}
                      icon={matched ? <CheckCircleOutlined /> : <ExclamationCircleOutlined />}
                      onMouseDown={(event) => event.preventDefault()}
                      onClick={() => insertAtCursor(col.name, sourceTables[0]?.recordInput ? 'calcite' : sourceTables[0] ? sourceType(sourceTables[0].node) : 'sql')}
                    >
                      {col.name}
                    </Tag>
                  </Tooltip>
                );
              })}
            </div>
            <div className="custom-sql-editor__coverage">
              <Badge status="success" text={`已覆盖 ${targetColumns.filter((col) => projectedKeys.has(col.name.toLowerCase())).length}`} />
              <Badge status="default" text={`未覆盖 ${targetColumns.filter((col) => !projectedKeys.has(col.name.toLowerCase())).length}`} />
            </div>
          </div>
        )}
      </div>

      <Divider style={{ margin: '10px 0' }} />
      <CodeMirror
        ref={editorRef}
        className="custom-sql-editor__code"
        value={value ?? ''}
        height="100%"
        extensions={[sql(), placeholder(SQL_PLACEHOLDER)]}
        onChange={onChange}
        basicSetup={{ lineNumbers: true, highlightActiveLine: true, foldGutter: true }}
      />
      <Modal title="选择来源表" open={choosingSource} okText="生成语句" cancelText="取消" getContainer={getNifiOverlayContainer}
        onCancel={() => setChoosingSource(false)} onOk={() => {
          const source = sourceTables.find((item) => item.key === selectedSource);
          if (!source) return;
          setChoosingSource(false);
          void generate(source);
        }}>
        <Select aria-label="生成语句的来源表" style={{ width: '100%', margin: '12px 0' }} value={selectedSource}
          onChange={setSelectedSource} options={sourceTables.map((item) => ({ value: item.key, label: `${item.node.label} · ${item.table}` }))} />
      </Modal>
      <div className="custom-sql-editor__foot">
        <Text type="secondary">
          系统会自动判断执行方式：SQL 中包含 FLOWFILE 时按记录内加工执行，否则在来源库执行。多条 SQL 时，前置语句作为准备动作，最后一条 SELECT/WITH 作为输出结果。
        </Text>
      </div>
    </div>
  );
});

export default CustomSqlEditor;
