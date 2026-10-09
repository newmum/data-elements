import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Checkbox,
  Drawer,
  Dropdown,
  Empty,
  Input,
  Modal,
  Popover,
  Progress,
  Select,
  Skeleton,
  Space,
  Spin,
  Table,
  Tag,
  Tooltip,
  Typography,
  App as AntdApp,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  AppstoreOutlined,
  BulbOutlined,
  CheckCircleFilled,
  CodeOutlined,
  DeleteOutlined,
  EyeOutlined,
  MoreOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  SearchOutlined,
  SwapOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import CodeMirror from '@uiw/react-codemirror';
import { json } from '@codemirror/lang-json';
import {
  compileFieldMapping,
  emptyFieldMappingSpec,
  migrateFieldMapping,
  previewFieldMapping,
  recommendFieldMapping,
  validateFieldMapping,
  type FieldMappingSpec,
  type MappingIssue,
  type MappingRule,
  type LookupSpec,
  type PreviewResponse,
  type TransformSpec,
} from '@/api/fieldMapping';
import { useCanvasStore } from '@/stores/canvasStore';
import { findUniqueCaseInsensitiveFieldMatches, identifierMatchKey } from '@/utils/fieldNameMatching';
import { getNifiOverlayContainer } from './overlayContainer';
import { isRecordLookup } from '../utils/recordLookup';

interface Props {
  value: unknown;
  onChange: (value: string) => void;
  columnMeta?: ColumnMeta;
  columnsLoading?: boolean;
  columnsError?: string;
  onReloadColumns?: () => void;
}

type ViewMode = 'list' | 'visual' | 'dsl';
type RowKind = 'field' | 'many' | 'constant' | 'expression' | 'lookup' | 'conditional';
type FieldSide = 'source' | 'target';
type FieldType = 'STRING' | 'INT' | 'DECIMAL' | 'DATETIME' | 'DATE' | 'BOOL' | 'JSON' | 'BLOB' | 'GEO';

interface FieldInfo {
  name: string;
  path: string;
  label?: string;
  type: FieldType;
  fullType?: string;
  isPk?: boolean;
  nullable?: boolean;
}

interface ColumnMeta {
  sourceLabels: Record<string, string>;
  targetLabels: Record<string, string>;
}

interface LocalRecommendation {
  id: string;
  from: string;
  fromLabel?: string;
  to: string;
  toLabel?: string;
  expression?: string;
  comment?: string;
  reason: string;
  confidence: number;
}

interface EdgePath {
  id: string;
  d: string;
  state: 'idle' | 'active' | 'disabled';
}

const TYPE_META: Record<FieldType, { label: string; bg: string; color: string }> = {
  STRING: { label: 'T', bg: '#E8F0FB', color: '#1A4FA0' },
  INT: { label: 'N', bg: '#FDF2E8', color: '#C26414' },
  DECIMAL: { label: 'N', bg: '#FDF2E8', color: '#C26414' },
  DATETIME: { label: 'D', bg: '#F0E8FB', color: '#7340C2' },
  DATE: { label: 'D', bg: '#F0E8FB', color: '#7340C2' },
  BOOL: { label: 'B', bg: '#E8F8F0', color: '#1F8A4C' },
  JSON: { label: 'J', bg: '#F4F0FB', color: '#5340C2' },
  BLOB: { label: 'X', bg: '#F0F2F6', color: '#5A6675' },
  GEO: { label: 'G', bg: '#E8F8F8', color: '#0E8A8A' },
};

const TRANSFORM_OPTIONS = [
  { value: '', label: '无转换' },
  { value: 'upper', label: 'upper 转大写' },
  { value: 'lower', label: 'lower 转小写' },
  { value: 'trim', label: 'trim 去空格' },
  { value: 'toInt', label: 'toInt 转整数' },
  { value: 'toLong', label: 'toLong 转长整数' },
  { value: 'toDouble', label: 'toDouble 转浮点' },
  { value: 'toDecimal', label: 'toDecimal 转小数' },
  { value: 'coalesce', label: 'coalesce 默认值' },
  { value: 'concat', label: 'concat 拼接' },
  { value: 'enumMap', label: 'enumMap 枚举映射' },
];

const ODS_UUID_EXPRESSION =
  "CAST(TIMESTAMPDIFF(SECOND, TIMESTAMP '1970-01-01 00:00:00', LOCALTIMESTAMP) AS VARCHAR) || CAST(100000000 + RAND_INTEGER(900000000) AS VARCHAR)";

function odsRecommendation(field: FieldInfo): LocalRecommendation | null {
  const target = field.name.toUpperCase();
  if (target === 'ODS_UUID') {
    return {
      id: 'ods-uuid',
      from: '',
      to: field.path,
      toLabel: field.label,
      expression: ODS_UUID_EXPRESSION,
      comment: 'NiFi生成正数ODS主键（秒级时间戳加随机尾数）',
      reason: 'ODS系统字段自动生成',
      confidence: 1,
    };
  }
  if (target === 'ODS_RKSJ') {
    return {
      id: 'ods-rksj',
      from: '',
      to: field.path,
      toLabel: field.label,
      expression: 'LOCALTIMESTAMP',
      comment: 'NiFi写入当前入库时间',
      reason: 'ODS系统字段自动生成',
      confidence: 1,
    };
  }
  if (target === 'ODS_GXSJ') {
    return {
      id: 'ods-gxsj',
      from: '',
      to: field.path,
      toLabel: field.label,
      expression: 'LOCALTIMESTAMP',
      comment: 'NiFi写入当前更新时间',
      reason: 'ODS系统字段自动生成',
      confidence: 1,
    };
  }
  return null;
}

const ADD_MENU_ITEMS: Array<{ key: RowKind; label: string }> = [
  { key: 'field', label: '字段映射' },
  { key: 'many', label: '多字段合并' },
  { key: 'constant', label: '常量赋值' },
  { key: 'expression', label: '表达式映射' },
  { key: 'lookup', label: '字典查询映射' },
  { key: 'conditional', label: '条件映射' },
];

const TYPE_COMPAT_GROUPS: { types: Set<FieldType> }[] = [
  { types: new Set<FieldType>(['INT', 'DECIMAL']) },
  { types: new Set<FieldType>(['DATETIME', 'DATE']) },
];

function typeMatchScore(a: FieldType, b: FieldType): { score: number; reason: string } {
  if (a === b) return { score: 2, reason: '中文名+类型匹配（贪心配对）' };
  for (const grp of TYPE_COMPAT_GROUPS) {
    if (grp.types.has(a) && grp.types.has(b)) return { score: 1.5, reason: '中文名+兼容类型配对' };
  }
  return { score: 1, reason: '中文名匹配（贪心配对）' };
}

function exactIdentifierRecommendations(
  sourceFields: FieldInfo[],
  targetFields: FieldInfo[],
): LocalRecommendation[] {
  return findUniqueCaseInsensitiveFieldMatches(sourceFields, targetFields).map(({ source, target, differsOnlyByCase }) => ({
    id: `exact-name-${source.path}-${target.path}`,
    from: source.path,
    fromLabel: source.label,
    to: target.path,
    toLabel: target.label,
    reason: differsOnlyByCase ? '英文名忽略大小写匹配' : '英文名完全匹配',
    confidence: 1,
  }));
}

export default function FieldMappingEditor(props: Props) {
  const [ready, setReady] = useState(false);

  // 延迟挂载重内容组件，避免 Drawer 弹出时因大量 Hook 同步计算阻塞首帧渲染
  useEffect(() => {
    const id = requestAnimationFrame(() => {
      requestAnimationFrame(() => setReady(true));
    });
    return () => cancelAnimationFrame(id);
  }, []);

  if (!ready) {
    return <FieldMappingEditorSkeleton />;
  }

  return <FieldMappingEditorContent {...props} />;
}

function FieldMappingEditorContent({ value, onChange, columnMeta, columnsLoading, columnsError, onReloadColumns }: Props) {
  const { message, modal } = AntdApp.useApp();
  const rootRef = useRef<HTMLDivElement>(null);
  const [spec, setSpec] = useState<FieldMappingSpec>(() => parseLocal(value));
  const [mode, setMode] = useState<ViewMode>('list');
  const [dslText, setDslText] = useState(() => JSON.stringify(spec, null, 2));
  const [issues, setIssues] = useState<{ errors: MappingIssue[]; warnings: MappingIssue[] }>({ errors: [], warnings: [] });
  const [preview, setPreview] = useState<PreviewResponse | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [recommendOpen, setRecommendOpen] = useState(false);
  const [templateOpen, setTemplateOpen] = useState(false);
  const [recommendations, setRecommendations] = useState<LocalRecommendation[]>([]);
  const [previewing, setPreviewing] = useState(false);
  const [recommending, setRecommending] = useState(false);
  const [compiling, setCompiling] = useState(false);
  const [pendingSource, setPendingSource] = useState<string | null>(null);
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null);
  const [customSources, setCustomSources] = useState<FieldInfo[]>([]);
  const [customTargets, setCustomTargets] = useState<FieldInfo[]>([]);
  const [cumulativeSourcePaths, setCumulativeSourcePaths] = useState<string[]>([]);
  const [cumulativeTargetPaths, setCumulativeTargetPaths] = useState<string[]>([]);
  const [edgePaths, setEdgePaths] = useState<EdgePath[]>([]);
  const [hoveredFieldPath, setHoveredFieldPath] = useState<string | null>(null);
  const [hoveredMappingIndex, setHoveredMappingIndex] = useState<number | null>(null);
  const inputSignature = useMemo(() => stableMappingValue(value), [value]);
  const lastLoadedSignature = useRef<string>('');

  useEffect(() => {
    if (lastLoadedSignature.current === inputSignature) return;
    lastLoadedSignature.current = inputSignature;
    let cancelled = false;
    migrateFieldMapping(value)
      .then((next) => {
        if (cancelled) return;
        const normalized = ensureMappingSpec(next);
        const serialized = JSON.stringify(normalized, null, 2);
        setSpec(normalized);
        setDslText(serialized);
        if (serialized !== inputSignature) onChange(serialized);
      })
      .catch(() => {
        const next = ensureMappingSpec(parseLocal(value));
        setSpec(next);
        setDslText(JSON.stringify(next, null, 2));
      });
    return () => { cancelled = true; };
  }, [inputSignature]);

  useEffect(() => {
    const id = window.setTimeout(() => {
      validateFieldMapping(spec)
      .then((r) => setIssues({ errors: r.errors ?? [], warnings: r.warnings ?? [] }))
      .catch((e) => setIssues({ errors: [{ path: 'spec', message: e?.response?.data?.error ?? e.message, severity: 'ERROR', code: 'VALIDATE_FAILED' }], warnings: [] }));
    }, 250);
    return () => window.clearTimeout(id);
  }, [spec]);

  // 从 columnMeta（sourceColumns / targetColumns）构建完整字段列表（主数据源）
  const metaSourceFields = useMemo(() => {
    if (!columnMeta) return [];
    return Object.keys(columnMeta.sourceLabels).map((colName) =>
      toFieldInfo(`/${colName}`, columnMeta.sourceLabels[colName])
    );
  }, [columnMeta]);

  const metaTargetFields = useMemo(() => {
    if (!columnMeta) return [];
    return Object.keys(columnMeta.targetLabels).map((colName) =>
      toFieldInfo(`/${colName}`, columnMeta.targetLabels[colName])
    );
  }, [columnMeta]);

  // 兼容无 columnMeta 时：从 mappings 推导字段路径（兜底数据源）
  useEffect(() => {
    setCumulativeSourcePaths((prev) => {
      const set = new Set(prev);
      inferSourceFields(spec).forEach((p) => set.add(p));
      return Array.from(set);
    });
    setCumulativeTargetPaths((prev) => {
      const set = new Set(prev);
      inferTargetFields(spec).forEach((p) => set.add(p));
      return Array.from(set);
    });
  }, [spec]);

  const mappingSourceFields = useMemo(
    () => cumulativeSourcePaths.map((p) => toFieldInfo(p, columnMeta?.sourceLabels[fieldName(p)])),
    [cumulativeSourcePaths, columnMeta]
  );
  const mappingTargetFields = useMemo(
    () => cumulativeTargetPaths.map((p) => toFieldInfo(p, columnMeta?.targetLabels[fieldName(p)])),
    [cumulativeTargetPaths, columnMeta]
  );

  // 合并优先级：mapping 字段 < meta（columnMeta 完整列） < 自定义字段
  const sourceFields = useMemo(
    () => mergeFields(mergeFields(mappingSourceFields, metaSourceFields), customSources),
    [mappingSourceFields, metaSourceFields, customSources]
  );
  const targetFields = useMemo(
    () => mergeFields(mergeFields(mappingTargetFields, metaTargetFields), customTargets),
    [mappingTargetFields, metaTargetFields, customTargets]
  );
  const rowIssues = useMemo(() => groupIssuesByRow([...issues.errors, ...issues.warnings]), [issues]);
  const allIssues = [...issues.errors, ...issues.warnings];
  const denseMappingMode = sourceFields.length > 120 || targetFields.length > 120 || spec.mappings.length > 120;
  const activeMappingIndexes = useMemo(
    () => denseMappingMode && hoveredMappingIndex == null
      ? new Set<number>()
      : collectRelatedMappingIndexes(spec, hoveredFieldPath ?? pendingSource, hoveredMappingIndex),
    [denseMappingMode, spec, hoveredFieldPath, pendingSource, hoveredMappingIndex]
  );
  const highlightedFieldKeys = useMemo(
    () => denseMappingMode && hoveredMappingIndex == null
      ? new Set<string>()
      : collectRelatedFieldKeys(spec, hoveredFieldPath ?? pendingSource, hoveredMappingIndex),
    [denseMappingMode, spec, hoveredFieldPath, pendingSource, hoveredMappingIndex]
  );

  useLayoutEffect(() => {
    const root = rootRef.current;
    if (!root) return;
    const canvas = root.querySelector<HTMLElement>('.fm-canvas');
    if (!canvas) return;
    if (mode !== 'visual' || denseMappingMode) {
      setEdgePaths([]);
      return;
    }

    const calculate = () => {
      const canvasRect = canvas.getBoundingClientRect();
      const canvasWidth = canvas.clientWidth;
      const paths: EdgePath[] = [];
      spec.mappings.forEach((mapping, index) => {
        const card = root.querySelector<HTMLElement>(`.fm-mapping-card[data-mapping-index="${index}"]`);
        if (!card) return;
        const cardRect = card.getBoundingClientRect();
        const cardIn = { x: Math.max(8, cardRect.left - canvasRect.left), y: cardRect.top + cardRect.height / 2 - canvasRect.top + canvas.scrollTop };
        const cardOut = { x: Math.min(canvasWidth - 8, cardRect.right - canvasRect.left), y: cardIn.y };

        pathsFromMapping(mapping).forEach((from, sourceIndex) => {
          const row = findFieldRow(root, 'source', normalizePath(from));
          if (!row) return;
          const rect = row.getBoundingClientRect();
          const start = { x: 4, y: rect.top + rect.height / 2 - canvasRect.top + canvas.scrollTop };
          paths.push({ id: `s-${index}-${sourceIndex}`, d: bezier(start.x, start.y, cardIn.x, cardIn.y), state: edgeState(mapping, index, activeMappingIndexes) });
        });

        if (mapping.to) {
          const row = findFieldRow(root, 'target', normalizePath(mapping.to));
          if (!row) return;
          const rect = row.getBoundingClientRect();
          const end = { x: canvasWidth - 4, y: rect.top + rect.height / 2 - canvasRect.top + canvas.scrollTop };
          paths.push({ id: `t-${index}`, d: bezier(cardOut.x, cardOut.y, end.x, end.y), state: edgeState(mapping, index, activeMappingIndexes) });
        }
      });
      setEdgePaths(paths);
    };

    const schedule = () => requestAnimationFrame(calculate);
    const resizeObserver = new ResizeObserver(schedule);
    resizeObserver.observe(root);
    resizeObserver.observe(canvas);
    root.querySelectorAll<HTMLElement>('.fm-field-list, .fm-canvas').forEach((el) => el.addEventListener('scroll', schedule, { passive: true }));
    schedule();
    return () => {
      resizeObserver.disconnect();
      root.querySelectorAll<HTMLElement>('.fm-field-list, .fm-canvas').forEach((el) => el.removeEventListener('scroll', schedule));
    };
  }, [spec, sourceFields, targetFields, mode, activeMappingIndexes, denseMappingMode]);

  const commit = (next: FieldMappingSpec) => {
    setSpec(next);
    const text = JSON.stringify(next, null, 2);
    setDslText(text);
    onChange(text);
  };

  const addRule = (kind: RowKind, patch: Partial<MappingRule> = {}) => {
    const base: MappingRule = kind === 'constant'
      ? { constant: '', to: '' }
      : kind === 'expression'
        ? { expression: 'upper(${field:字段名})', to: '' }
        : kind === 'lookup'
          ? { from: '', lookup: { sql: 'SELECT dict_label\nFROM sys_dict_data\nWHERE dict_value = ?', onMissing: 'NULL' }, to: '' }
          : kind === 'many'
            ? { fromList: ['', ''], to: '', transform: { fn: 'concat', args: [' '] } }
            : kind === 'conditional'
              ? { when: '${field:字段名} != null', then: { constant: 'Y' }, else: { constant: 'N' }, to: '' }
              : { from: '', to: '' };
    const mappings = [...spec.mappings, normalizeRule({ ...base, ...patch })];
    commit({ ...spec, mappings });
    setSelectedIndex(mappings.length - 1);
  };

  const updateRule = (index: number, patch: Partial<MappingRule>) => {
    const mappings = spec.mappings.map((mapping, i) => (i === index ? normalizeRule({ ...mapping, ...patch }) : mapping));
    commit({ ...spec, mappings });
  };

  const removeRule = (index: number) => {
    commit({ ...spec, mappings: spec.mappings.filter((_, i) => i !== index) });
    setSelectedIndex(null);
  };

  const moveRule = (index: number, direction: -1 | 1) => {
    const to = index + direction;
    if (to < 0 || to >= spec.mappings.length) return;
    const mappings = [...spec.mappings];
    const [item] = mappings.splice(index, 1);
    mappings.splice(to, 0, item);
    commit({ ...spec, mappings });
    setSelectedIndex(to);
  };

  const createMapping = (from: string, to: string) => addRule('field', { from: normalizePath(from), to: normalizePath(to) });

  const handleSourceClick = (path: string) => {
    setPendingSource(path);
    message.info(`已选择源字段 ${displayPath(path)}，请选择右侧目标字段`);
  };

  const handleTargetClick = (path: string) => {
    if (!pendingSource) return;
    createMapping(pendingSource, path);
    setPendingSource(null);
  };

  const applyDsl = () => {
    try {
      const parsed = JSON.parse(dslText);
      migrateFieldMapping(parsed).then((next) => {
        commit(next);
        setMode('visual');
        message.success('高级配置已应用到可视化编辑器');
      }).catch((e) => message.error(e?.response?.data?.error ?? e.message));
    } catch (e) {
      message.error(`高级配置 JSON 解析失败: ${(e as Error).message}`);
    }
  };

  const openPreview = async () => {
    setPreviewing(true);
    try {
      const data = await previewFieldMapping(spec, buildSampleRows(spec));
      setPreview(data);
      setPreviewOpen(true);
      if (data.resultRows.length === 0) message.info('暂无可预览字段，请先添加映射规则');
    } catch (e: any) {
      message.error(e?.response?.data?.error ?? e?.message ?? '预览失败');
    } finally {
      setPreviewing(false);
    }
  };

  const openRecommend = async () => {
    if (columnsLoading) return;
    if (columnsError) {
      message.warning('字段探查未完成，请重新探查后再推荐');
      return;
    }
    if (sourceFields.length === 0) {
      message.warning('尚未加载来源字段，请检查来源表配置并探查字段');
      return;
    }
    setRecommending(true);
    try {
      let items: LocalRecommendation[] = [];
      const sourceByPath = new Map(sourceFields.map((field) => [field.path, field]));
      const targetByPath = new Map(targetFields.map((field) => [field.path, field]));
      try {
        const response = await recommendFieldMapping(
          sourceFields.map((field) => ({ name: field.label?.trim() || field.name, path: field.path, type: field.type })),
          targetFields.map((field) => ({ name: field.label?.trim() || field.name, path: field.path, type: field.type })),
        );
        response.recommendations.forEach((rec, index) => {
          const source = sourceByPath.get(normalizePath(rec.from));
          const target = targetByPath.get(normalizePath(rec.to));
          items.push({
            id: rec.id || `rec-${index}`,
            from: normalizePath(rec.from),
            fromLabel: source?.label,
            to: normalizePath(rec.to),
            toLabel: target?.label,
            reason: rec.reason,
            confidence: rec.confidence,
          });
        });
      } catch {
        const labeledSources = sourceFields.filter((f) => f.label);
        const labeledTargets = targetFields.filter((f) => f.label);
        const srcByLabel = new Map<string, FieldInfo[]>();
        const tgtByLabel = new Map<string, FieldInfo[]>();
        labeledSources.forEach((f) => {
          const key = f.label!.trim();
          const arr = srcByLabel.get(key) ?? [];
          arr.push(f);
          srcByLabel.set(key, arr);
        });
        labeledTargets.forEach((f) => {
          const key = f.label!.trim();
          const arr = tgtByLabel.get(key) ?? [];
          arr.push(f);
          tgtByLabel.set(key, arr);
        });
        let id = 0;
        const confidenceMap: Record<number, number> = { 2: 0.95, 1.5: 0.85, 1: 0.75 };
        for (const [label, srcGroup] of srcByLabel) {
          const tgtGroup = tgtByLabel.get(label);
          if (!tgtGroup || tgtGroup.length === 0) continue;
          if (srcGroup.length === 1 && tgtGroup.length === 1) {
            items.push({
              id: `rec-${id++}`,
              from: srcGroup[0].path,
              fromLabel: srcGroup[0].label,
              to: tgtGroup[0].path,
              toLabel: tgtGroup[0].label,
              reason: '中文名完全匹配',
              confidence: 1.0,
            });
          } else {
            const remainingSrc = [...srcGroup];
            const remainingTgt = [...tgtGroup];
            for (let si = remainingSrc.length - 1; si >= 0; si--) {
              for (let ti = remainingTgt.length - 1; ti >= 0; ti--) {
                if (identifierMatchKey(remainingSrc[si].name) === identifierMatchKey(remainingTgt[ti].name)) {
                  items.push({
                    id: `rec-${id++}`,
                    from: remainingSrc[si].path,
                    fromLabel: remainingSrc[si].label,
                    to: remainingTgt[ti].path,
                    toLabel: remainingTgt[ti].label,
                    reason: remainingSrc[si].name === remainingTgt[ti].name ? '中文名+英文名完全匹配' : '中文名+英文名忽略大小写匹配',
                    confidence: 1.0,
                  });
                  remainingSrc.splice(si, 1);
                  remainingTgt.splice(ti, 1);
                  break;
                }
              }
            }
            while (remainingSrc.length > 0 && remainingTgt.length > 0) {
              let bestSi = 0;
              let bestTi = 0;
              let bestScore = -1;
              for (let si = 0; si < remainingSrc.length; si++) {
                for (let ti = 0; ti < remainingTgt.length; ti++) {
                  const { score } = typeMatchScore(remainingSrc[si].type, remainingTgt[ti].type);
                  if (score > bestScore) {
                    bestScore = score;
                    bestSi = si;
                    bestTi = ti;
                  }
                }
              }
              items.push({
                id: `rec-${id++}`,
                from: remainingSrc[bestSi].path,
                fromLabel: remainingSrc[bestSi].label,
                to: remainingTgt[bestTi].path,
                toLabel: remainingTgt[bestTi].label,
                reason: typeMatchScore(remainingSrc[bestSi].type, remainingTgt[bestTi].type).reason,
                confidence: confidenceMap[bestScore] ?? 0.75,
              });
              remainingSrc.splice(bestSi, 1);
              remainingTgt.splice(bestTi, 1);
            }
          }
        }
      }
      // A direct identifier match must win over a fuzzy/server recommendation.
      // CASE_ID -> case_id is the same business field even when comments are
      // absent or different. Both original spellings are retained for SQL.
      const directNameItems = exactIdentifierRecommendations(sourceFields, targetFields);
      const directTargetKeys = new Set(directNameItems.map((item) => normalizePath(item.to).toLowerCase()));
      items = [
        ...items.filter((item) => !directTargetKeys.has(normalizePath(item.to).toLowerCase())),
        ...directNameItems,
      ];
      const existingTargets = new Set(items.map((item) => normalizePath(item.to).toLowerCase()));
      targetFields.forEach((field) => {
        const system = odsRecommendation(field);
        if (system && !existingTargets.has(normalizePath(system.to).toLowerCase())) {
          items.push(system);
          existingTargets.add(normalizePath(system.to).toLowerCase());
        }
      });
      setRecommendations(items);
      setRecommendOpen(true);
      if (items.length === 0) message.info('没有发现可推荐的字段映射');
    } catch (e: any) {
      message.error(e?.response?.data?.error ?? e?.message ?? '推荐失败');
    } finally {
      setRecommending(false);
    }
  };

  const showCompiledSql = async () => {
    setCompiling(true);
    try {
      const data = await compileFieldMapping(spec);
      Modal.info({ title: 'QueryRecord SQL', width: 760, content: <pre className="field-mapping-sql-preview">{data.query}</pre> });
    } catch (e: any) {
      message.error(e?.response?.data?.error ?? e?.message ?? '编译 SQL 失败');
    } finally {
      setCompiling(false);
    }
  };

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const cmd = event.ctrlKey || event.metaKey;
      if (!cmd && !['Delete', 'ArrowUp', 'ArrowDown', 'Escape'].includes(event.key)) return;
      if (cmd && event.key.toLowerCase() === 'n') { event.preventDefault(); addRule('field'); }
      if (cmd && event.key.toLowerCase() === 'r') { event.preventDefault(); openRecommend(); }
      if (cmd && event.key.toLowerCase() === 'p') { event.preventDefault(); openPreview(); }
      if (cmd && event.shiftKey && event.key.toLowerCase() === 'v') { event.preventDefault(); setMode((v) => v === 'visual' ? 'dsl' : 'visual'); }
      if (event.key === 'Delete' && selectedIndex != null) { event.preventDefault(); removeRule(selectedIndex); }
      if (cmd && event.key === '/' && selectedIndex != null) {
        event.preventDefault();
        const mapping = spec.mappings[selectedIndex];
        updateRule(selectedIndex, { enabled: mapping.enabled === false });
      }
      if (event.key === 'ArrowUp') { event.preventDefault(); setSelectedIndex((i) => Math.max((i ?? 0) - 1, 0)); }
      if (event.key === 'ArrowDown') { event.preventDefault(); setSelectedIndex((i) => Math.min((i ?? -1) + 1, spec.mappings.length - 1)); }
      if (event.key === 'Escape') setPendingSource(null);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [selectedIndex, spec]);

  // 未配置场景下，模板加载后自动触发一次字段映射推荐
  const pendingAutoRecommend = useCanvasStore((s) => s.pendingAutoRecommend);
  const setPendingAutoRecommend = useCanvasStore((s) => s.setPendingAutoRecommend);
  const autoRecommendTriggeredRef = useRef(false);
  useEffect(() => {
    if (pendingAutoRecommend && !columnsLoading && !columnsError && sourceFields.length > 0 && !autoRecommendTriggeredRef.current) {
      autoRecommendTriggeredRef.current = true;
      openRecommend();
      setPendingAutoRecommend(false);
    }
  }, [pendingAutoRecommend, columnsLoading, columnsError, sourceFields.length]);

  return (
    <div ref={rootRef} className="field-mapping-editor field-mapping-editor-v11">
      <TopBar
        mode={mode}
        setMode={setMode}
        spec={spec}
        issues={issues}
        previewing={previewing}
        recommending={recommending || Boolean(columnsLoading)}
        compiling={compiling}
        onRecommend={openRecommend}
        onPreview={openPreview}
        onTemplate={() => setTemplateOpen(true)}
        onCompile={showCompiledSql}
      />

      {columnsLoading && <Alert type="info" showIcon message="正在探查来源表和目标表字段…" />}
      {columnsError && <Alert type="warning" showIcon message={columnsError}
        action={<Button size="small" onClick={onReloadColumns}>重新探查</Button>} />}

      {allIssues.length > 0 && <ValidationBanner issues={allIssues} hasError={issues.errors.length > 0} />}

      {mode === 'list' ? (
        <MappingListView
          spec={spec}
          sourceFields={sourceFields}
          targetFields={targetFields}
          rowIssues={rowIssues}
          selectedIndex={selectedIndex}
          onSelect={setSelectedIndex}
          onUpdate={updateRule}
          onDelete={removeRule}
          onMove={moveRule}
          onAdd={addRule}
        />
      ) : mode === 'dsl' ? (
        <DslView text={dslText} onChange={setDslText} onApply={applyDsl} onReset={() => setDslText(JSON.stringify(spec, null, 2))} />
      ) : (
        <div className="fm-layout">
          <FieldPanel
            side="source"
            title="源字段"
            subtitle={`${sourceFields.length} 个字段`}
            fields={sourceFields}
            mappings={spec.mappings}
            pendingSource={pendingSource}
            highlightedFieldKeys={highlightedFieldKeys}
            onFieldClick={handleSourceClick}
            onFieldHover={setHoveredFieldPath}
            onAddCustom={(field) => setCustomSources((items) => mergeFields(items, [field]))}
            onQuickMap={(from, to) => createMapping(from, to)}
            targetFields={targetFields}
          />

          <MappingCanvas
            spec={spec}
            sourceFields={sourceFields}
            targetFields={targetFields}
            edgePaths={edgePaths}
            rowIssues={rowIssues}
            selectedIndex={selectedIndex}
            activeMappingIndexes={activeMappingIndexes}
            onSelect={setSelectedIndex}
            onMappingHover={setHoveredMappingIndex}
            onUpdate={updateRule}
            onDelete={removeRule}
            onMove={moveRule}
            onAdd={addRule}
          />

          <FieldPanel
            side="target"
            title="目标字段"
            subtitle={`${targetFields.length} 个字段`}
            fields={targetFields}
            mappings={spec.mappings}
            pendingSource={pendingSource}
            highlightedFieldKeys={highlightedFieldKeys}
            onFieldClick={handleTargetClick}
            onFieldHover={setHoveredFieldPath}
            onAddCustom={(field) => setCustomTargets((items) => mergeFields(items, [field]))}
          />
        </div>
      )}

      <BottomBar spec={spec} issues={issues} onChange={commit} />

      <PreviewDrawer open={previewOpen} data={preview} onClose={() => setPreviewOpen(false)} />
      <RecommendModal open={recommendOpen} recommendations={recommendations} onCancel={() => setRecommendOpen(false)} onApply={(picked) => {
        const newMappings = picked.map((r) => r.expression
          ? { expression: r.expression, to: normalizePath(r.to), comment: r.comment }
          : { from: normalizePath(r.from), to: normalizePath(r.to) });
        const doApply = () => {
          commit({ ...spec, mappings: newMappings });
          setRecommendOpen(false);
          message.success(`已覆盖应用 ${picked.length} 条推荐映射`);
        };
        if (spec.mappings.length > 0) {
          modal.confirm({
            title: '覆盖确认',
            content: `当前已有 ${spec.mappings.length} 条映射规则，应用推荐映射将覆盖原有映射，是否继续？`,
            onOk: doApply,
          });
        } else {
          doApply();
        }
      }} />
      <TemplateModal open={templateOpen} sourceFields={sourceFields} targetFields={targetFields} onCancel={() => setTemplateOpen(false)} onApply={(mappings) => {
        commit({ ...spec, mappings: [...spec.mappings, ...mappings] });
        setTemplateOpen(false);
      }} />
    </div>
  );
}

function TopBar({ mode, setMode, spec, issues, previewing, recommending, compiling, onRecommend, onPreview, onTemplate, onCompile }: {
  mode: ViewMode;
  setMode: (mode: ViewMode) => void;
  spec: FieldMappingSpec;
  issues: { errors: MappingIssue[]; warnings: MappingIssue[] };
  previewing: boolean;
  recommending: boolean;
  compiling: boolean;
  onRecommend: () => void;
  onPreview: () => void;
  onTemplate: () => void;
  onCompile: () => void;
}) {
  return (
    <header className="fm-topbar">
      <div className="fm-topbar-left">
        <Typography.Text strong>字段映射</Typography.Text>
        <span className="fm-summary"><strong>{spec.mappings.length}</strong> 条映射</span>
        <ValidationTag issues={issues} />
      </div>
      <div className="fm-topbar-right">
        <Space.Compact size="small">
          <Button type={mode === 'list' ? 'primary' : 'default'} icon={<UnorderedListOutlined />} onClick={() => setMode('list')}>列表</Button>
          <Button type={mode === 'visual' ? 'primary' : 'default'} icon={<SwapOutlined />} onClick={() => setMode('visual')}>可视化</Button>
          <Button type={mode === 'dsl' ? 'primary' : 'default'} icon={<CodeOutlined />} onClick={() => setMode('dsl')}>高级</Button>
        </Space.Compact>
        <Button size="small" icon={<BulbOutlined />} loading={recommending} onClick={onRecommend}>推荐</Button>
        <Button size="small" icon={<AppstoreOutlined />} onClick={onTemplate}>模板</Button>
        <Button size="small" icon={<EyeOutlined />} loading={previewing} onClick={onPreview}>预览</Button>
        <Dropdown menu={{ items: [{ key: 'sql', label: '查看 QueryRecord SQL', onClick: onCompile }] }}>
          <Button size="small" icon={<MoreOutlined />} loading={compiling} />
        </Dropdown>
      </div>
    </header>
  );
}

function ValidationTag({ issues }: { issues: { errors: MappingIssue[]; warnings: MappingIssue[] } }) {
  if (issues.errors.length === 0 && issues.warnings.length === 0) {
    return <Tag color="success" icon={<CheckCircleFilled />}>全部通过</Tag>;
  }
  const label = `${issues.errors.length ? `${issues.errors.length} 错误` : ''}${issues.warnings.length ? ` ${issues.warnings.length} 警告` : ''}`.trim();
  return (
    <Popover content={<div className="field-mapping-issue-list">{[...issues.errors, ...issues.warnings].map((i, idx) => <div key={idx}>{i.message}</div>)}</div>}>
      <Tag color={issues.errors.length ? 'error' : 'warning'}>{label}</Tag>
    </Popover>
  );
}

function ValidationBanner({ issues, hasError }: { issues: MappingIssue[]; hasError: boolean }) {
  return (
    <div className={`fm-validation-strip ${hasError ? 'is-error' : 'is-warn'}`}>
      <span>{hasError ? '需修正' : '建议'}</span>
      <Typography.Text ellipsis>{issues.slice(0, 3).map((issue) => issue.message).join('；')}</Typography.Text>
    </div>
  );
}

function DslView({ text, onChange, onApply, onReset }: { text: string; onChange: (v: string) => void; onApply: () => void; onReset: () => void }) {
  return (
    <div className="field-mapping-dsl-view">
      <CodeMirror className="fm-dsl-editor" value={text} height="100%" extensions={[json()]} onChange={onChange} basicSetup={{ lineNumbers: true, highlightActiveLine: true }} />
      <div className="fm-dsl-actions">
        <Button size="small" onClick={onReset}>重置</Button>
        <Button size="small" type="primary" onClick={onApply}>应用</Button>
      </div>
    </div>
  );
}

function MappingListView({ spec, sourceFields, targetFields, rowIssues, selectedIndex, onSelect, onUpdate, onDelete, onMove, onAdd }: {
  spec: FieldMappingSpec;
  sourceFields: FieldInfo[];
  targetFields: FieldInfo[];
  rowIssues: Map<number, MappingIssue[]>;
  selectedIndex: number | null;
  onSelect: (index: number) => void;
  onUpdate: (index: number, patch: Partial<MappingRule>) => void;
  onDelete: (index: number) => void;
  onMove: (index: number, direction: -1 | 1) => void;
  onAdd: (kind: RowKind) => void;
}) {
  const sourceLabelByPath = useMemo(() => buildFieldLabelMap(sourceFields), [sourceFields]);
  const targetLabelByPath = useMemo(() => buildFieldLabelMap(targetFields), [targetFields]);
  const listViewRef = useRef<HTMLElement>(null);
  const [tableScrollY, setTableScrollY] = useState(240);
  const [configIndex, setConfigIndex] = useState<number | null>(null);

  // The table body must use the height left after the header and bottom policy bar.
  // A fixed 430px body overflowed the available drawer area and visually hid the
  // last mappings behind the policy controls.
  useLayoutEffect(() => {
    const view = listViewRef.current;
    if (!view) return undefined;
    const syncHeight = () => setTableScrollY(Math.max(160, Math.floor(view.clientHeight - 104)));
    const observer = new ResizeObserver(syncHeight);
    observer.observe(view);
    syncHeight();
    return () => observer.disconnect();
  }, []);

  const columns: ColumnsType<{ key: number; index: number; mapping: MappingRule; kind: RowKind; issues: MappingIssue[] }> = [
    {
      title: '序号',
      dataIndex: 'index',
      width: 70,
      render: (index: number) => <span className="fm-list-index">{index + 1}</span>,
    },
    {
      title: '映射类型',
      dataIndex: 'kind',
      width: 112,
      render: (kind: RowKind, row) => <MappingKindTag kind={kind} mapping={row.mapping} />,
    },
    {
      title: '来源表字段',
      dataIndex: 'mapping',
      ellipsis: true,
      render: (mapping: MappingRule) => <MappingSourceCell mapping={mapping} labelByPath={sourceLabelByPath} />,
    },
    {
      title: '目标表字段',
      dataIndex: 'mapping',
      ellipsis: true,
      render: (mapping: MappingRule) => <MappingTargetCell mapping={mapping} labelByPath={targetLabelByPath} />,
    },
    {
      title: '转换 / 配置',
      dataIndex: 'mapping',
      width: 128,
      render: (mapping: MappingRule, row) => (
        <Button size="small" type="link" onClick={(event) => { event.stopPropagation(); setConfigIndex(row.index); }}>
          {mappingConfigActionLabel(mapping)}
        </Button>
      ),
    },
    {
      title: '校验',
      dataIndex: 'issues',
      width: 120,
      render: (issues: MappingIssue[]) => issues.length
        ? <Tag color={issues.some((issue) => issue.severity === 'ERROR') ? 'error' : 'warning'}>{issues.length} 条问题</Tag>
        : <Tag color="success">通过</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'mapping',
      width: 96,
      render: (mapping: MappingRule, row) => (
        <Checkbox checked={mapping.enabled !== false} onClick={(e) => e.stopPropagation()} onChange={(e) => onUpdate(row.index, { enabled: e.target.checked ? undefined : false })}>启用</Checkbox>
      ),
    },
    {
      title: '操作',
      dataIndex: 'index',
      width: 150,
      render: (index: number) => (
        <Space size={4}>
          <Button size="small" type="text" disabled={index === 0} onClick={(e) => { e.stopPropagation(); onMove(index, -1); }}>上移</Button>
          <Button size="small" type="text" disabled={index === spec.mappings.length - 1} onClick={(e) => { e.stopPropagation(); onMove(index, 1); }}>下移</Button>
          <Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={(e) => { e.stopPropagation(); onDelete(index); }} />
        </Space>
      ),
    },
  ];
  const rows = spec.mappings.map((mapping, index) => ({ key: index, index, mapping, kind: ruleKind(mapping), issues: rowIssues.get(index) ?? [] }));

  return (
    <section ref={listViewRef} className="fm-list-view">
      <div className="fm-list-head">
        <div>
          <Typography.Text strong>映射关系列表</Typography.Text>
          <span className="fm-list-summary">{spec.mappings.length} 条映射 · 源字段 {sourceFields.length} 个 · 目标字段 {targetFields.length} 个</span>
        </div>
        <Dropdown trigger={['click']} menu={{ items: ADD_MENU_ITEMS, onClick: ({ key }) => onAdd(key as RowKind) }}>
          <Button size="small" icon={<PlusOutlined />}>添加映射规则</Button>
        </Dropdown>
      </div>
      <Table
        size="small"
        className="fm-list-table"
        rowKey="key"
        columns={columns}
        dataSource={rows}
        pagination={rows.length > 30 ? { pageSize: 30, showSizeChanger: true, showTotal: (total) => `共 ${total} 条映射` } : false}
        scroll={{ x: 960, y: tableScrollY }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无映射规则，可点击右上角添加" /> }}
        rowClassName={(row) => row.index === selectedIndex ? 'is-selected' : ''}
        onRow={(row) => ({ onClick: () => onSelect(row.index) })}
      />
      <MappingConfigModal
        open={configIndex !== null}
        mapping={configIndex === null ? undefined : spec.mappings[configIndex]}
        mappingIndex={configIndex}
        sourceLabelByPath={sourceLabelByPath}
        targetLabelByPath={targetLabelByPath}
        onClose={() => setConfigIndex(null)}
      />
    </section>
  );
}

function MappingSourceCell({ mapping, labelByPath }: { mapping: MappingRule; labelByPath: Map<string, string> }) {
  const kind = ruleKind(mapping);
  if (kind === 'constant') return <FieldSummary title={String(mapping.constant ?? '') || '空常量'} subtitle="常量值" />;
  if (kind === 'expression') return <FieldSummary title={mapping.expression ?? ''} subtitle="表达式" />;
  if (kind === 'conditional') return <FieldSummary title={mapping.when ?? ''} subtitle="条件判断" />;
  if (mapping.fromList?.length) {
    return (
      <Space size={4} wrap>
        {mapping.fromList.map((path) => <FieldLabelCell key={path} path={path} label={fieldLabel(path, labelByPath)} />)}
      </Space>
    );
  }
  return <FieldLabelCell path={mapping.from ?? ''} label={fieldLabel(mapping.from, labelByPath)} />;
}

function MappingKindTag({ kind, mapping }: { kind: RowKind; mapping: MappingRule }) {
  const tag = <Tag color={kind === 'field' ? 'blue' : kind === 'lookup' ? 'purple' : 'geekblue'}>{rowKindLabel(kind)}</Tag>;
  if (kind !== 'lookup') return tag;

  const missing = mapping.lookup?.onMissing === 'KEEP_SOURCE'
    ? '未查到时保留来源字段原值'
    : mapping.lookup?.onMissing === 'FAIL'
      ? '未查到时使当前记录失败'
      : '未查到时写入 NULL';
  const resultColumn = mapping.lookup?.resultColumn?.trim();
  const detail = resultColumn ? '通过字典查询取得映射结果（返回列：' + resultColumn + '）。' : '通过字典查询取得映射结果。';
  return (
    <Tooltip title={<div><div>{detail}</div><div>{missing}。</div></div>}>
      <span>{tag}</span>
    </Tooltip>
  );
}

function MappingTargetCell({ mapping, labelByPath }: { mapping: MappingRule; labelByPath: Map<string, string> }) {
  return <FieldLabelCell path={mapping.to ?? ''} label={fieldLabel(mapping.to, labelByPath)} />;
}

function MappingConfigModal({ open, mapping, mappingIndex, sourceLabelByPath, targetLabelByPath, onClose }: {
  open: boolean;
  mapping?: MappingRule;
  mappingIndex: number | null;
  sourceLabelByPath: Map<string, string>;
  targetLabelByPath: Map<string, string>;
  onClose: () => void;
}) {
  const content = mapping ? JSON.stringify(buildMappingConfig(mapping, sourceLabelByPath, targetLabelByPath), null, 2) : '';
  return (
    <Modal
      getContainer={getNifiOverlayContainer}
      open={open}
      title={mappingIndex === null ? '映射配置' : `映射配置 · 第 ${mappingIndex + 1} 条`}
      width={760}
      onCancel={onClose}
      footer={<Button onClick={onClose}>关闭</Button>}
      destroyOnClose
    >
      <Typography.Paragraph type="secondary" className="fm-mapping-config-hint">
        这里展示当前规则的完整配置；枚举映射参数和 SQL 查询语句均可完整查看。
      </Typography.Paragraph>
      <Input.TextArea
        className="fm-mapping-config-json"
        value={content}
        readOnly
        spellCheck={false}
        autoSize={{ minRows: 14, maxRows: 24 }}
      />
    </Modal>
  );
}

function mappingConfigActionLabel(mapping: MappingRule) {
  if (mapping.lookup) return '查看 SQL';
  if (mapping.transform?.fn === 'enumMap') return '查看枚举';
  if (mapping.transform?.fn) return '查看转换';
  if (mapping.expression) return '查看表达式';
  if (mapping.when) return '查看条件';
  if (mapping.constant !== undefined) return '查看常量';
  return '查看映射';
}

function buildMappingConfig(mapping: MappingRule, sourceLabelByPath: Map<string, string>, targetLabelByPath: Map<string, string>) {
  const sourcePaths = mapping.fromList?.length ? mapping.fromList : mapping.from ? [mapping.from] : [];
  const config: Record<string, unknown> = {
    mappingType: rowKindLabel(ruleKind(mapping)),
    sourceFields: sourcePaths.map((path) => ({ name: fieldLabel(path, sourceLabelByPath) || fieldName(path), path })),
    targetField: mapping.to ? { name: fieldLabel(mapping.to, targetLabelByPath) || fieldName(mapping.to), path: mapping.to } : undefined,
    enabled: mapping.enabled !== false,
  };
  if (mapping.transform) {
    config.transform = {
      function: mapping.transform.fn,
      args: mapping.transform.args ?? [],
      onError: mapping.transform.onError,
    };
  }
  if (mapping.lookup) config.lookup = mapping.lookup;
  if (mapping.expression) config.expression = mapping.expression;
  if (mapping.constant !== undefined) config.constant = mapping.constant;
  if (mapping.when) {
    config.condition = { when: mapping.when, then: mapping.then, else: mapping.else };
  }
  if (mapping.comment) config.comment = mapping.comment;
  return config;
}

function FieldSummary({ title, subtitle }: { title: string; subtitle?: string }) {
  return (
    <div className="fm-list-field-summary">
      <span className="fm-list-field-title">{title || '-'}</span>
      {subtitle && <span className="fm-list-field-subtitle">{subtitle}</span>}
    </div>
  );
}

function FieldPanel({ side, title, subtitle, fields, mappings, pendingSource, highlightedFieldKeys, targetFields = [], onFieldClick, onFieldHover, onAddCustom, onQuickMap }: {
  side: FieldSide;
  title: string;
  subtitle: string;
  fields: FieldInfo[];
  mappings: MappingRule[];
  pendingSource: string | null;
  highlightedFieldKeys: Set<string>;
  targetFields?: FieldInfo[];
  onFieldClick: (path: string) => void;
  onFieldHover: (path: string | null) => void;
  onAddCustom: (field: FieldInfo) => void;
  onQuickMap?: (from: string, to: string) => void;
}) {
  const [keyword, setKeyword] = useState('');
  const [adding, setAdding] = useState(false);
  const [newField, setNewField] = useState('');
  const [newLabel, setNewLabel] = useState('');
  const filtered = fields.filter((field) => !keyword || field.name.toLowerCase().includes(keyword.toLowerCase()) || (field.label ?? '').toLowerCase().includes(keyword.toLowerCase()));
  const sourceMap = collectMappedSources(mappings);
  const targetMap = collectMappedTargets(mappings);

  const addField = () => {
    const name = newField.trim();
    if (!name) return;
    onAddCustom(toFieldInfo(normalizePath(name), newLabel.trim() || undefined));
    setNewField('');
    setNewLabel('');
    setAdding(false);
  };

  return (
    <aside className={`fm-field-panel fm-field-panel-${side}`}>
      <header className="fm-panel-header">
        <div className="fm-panel-title">{title}</div>
        <div className="fm-panel-subtitle">{subtitle}</div>
      </header>
      <div className="fm-panel-search"><Input size="small" allowClear prefix={<SearchOutlined />} placeholder="搜索字段..." value={keyword} onChange={(e) => setKeyword(e.target.value)} /></div>
      <div className="fm-field-list">
        {filtered.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无字段" /> : filtered.map((field) => (
          <FieldRow
            key={field.path}
            side={side}
            field={field}
            active={pendingSource === field.path || highlightedFieldKeys.has(fieldKey(field.path))}
            mappedNames={side === 'source' ? sourceMap[field.path] ?? [] : targetMap[field.path] ?? []}
            onClick={() => onFieldClick(field.path)}
            onMouseEnter={() => onFieldHover(field.path)}
            onMouseLeave={() => onFieldHover(null)}
            menuTargets={targetFields}
            onQuickMap={onQuickMap}
          />
        ))}
      </div>
      <div className="fm-add-custom">
        {adding ? (
          <div className="fm-add-custom-form">
            <Input size="small" autoFocus value={newField} placeholder="字段名（必填）" onChange={(e) => setNewField(e.target.value)} onPressEnter={addField} />
            <Input size="small" value={newLabel} placeholder="中文名（可选）" onChange={(e) => setNewLabel(e.target.value)} onPressEnter={addField} />
            <Space.Compact style={{ width: '100%' }}>
              <Button size="small" type="primary" block onClick={addField}>添加</Button>
              <Button size="small" block onClick={() => { setAdding(false); setNewField(''); setNewLabel(''); }}>取消</Button>
            </Space.Compact>
          </div>
        ) : (
          <Button size="small" block icon={<PlusOutlined />} onClick={() => setAdding(true)}>添加自定义{side === 'source' ? '源' : '目标'}字段</Button>
        )}
      </div>
    </aside>
  );
}

function FieldRow({ side, field, active, mappedNames, menuTargets, onClick, onMouseEnter, onMouseLeave, onQuickMap }: {
  side: FieldSide;
  field: FieldInfo;
  active: boolean;
  mappedNames: string[];
  menuTargets: FieldInfo[];
  onClick: () => void;
  onMouseEnter: () => void;
  onMouseLeave: () => void;
  onQuickMap?: (from: string, to: string) => void;
}) {
  const menu = side === 'source' && onQuickMap ? {
    items: [
      {
        key: 'same',
        label: `映射到同名目标 ${field.name}`,
        disabled: !menuTargets.some((target) => target.name === field.name),
        onClick: () => onQuickMap(field.path, normalizePath(field.name)),
      },
      {
        key: 'target',
        label: '映射到...',
        children: menuTargets.map((target) => ({ key: target.path, label: target.name, onClick: () => onQuickMap(field.path, target.path) })),
      },
    ],
  } : undefined;
  const row = (
    <button className={`fm-field-row ${active ? 'is-active' : ''} ${mappedNames.length ? 'is-mapped' : ''}`} data-side={side} data-field-path={field.path} onClick={onClick} onMouseEnter={onMouseEnter} onMouseLeave={onMouseLeave}>
      {side === 'target' && <MappingCount count={mappedNames.length} />}
      <span className="fm-port" data-port="true" />
      <span className="fm-field-info">
        {field.label ? <span className="fm-field-label">{field.label}</span> : null}
        <span className="fm-field-name">{field.name}</span>
      </span>
      {field.isPk && <span className="fm-pk-tag">PK</span>}
      <TypeBadge field={field} />
      {side === 'source' && <MappingCheck names={mappedNames} />}
    </button>
  );
  return menu ? <Dropdown menu={menu} trigger={['contextMenu']}>{row}</Dropdown> : row;
}

function MappingCheck({ names }: { names: string[] }) {
  if (!names.length) return <span className="fm-mapped-check" />;
  return <Tooltip title={`已映射到：${names.join(', ')}`}><span className="fm-mapped-check">✓</span></Tooltip>;
}

function MappingCount({ count }: { count: number }) {
  if (!count) return <span className="fm-source-count" />;
  return <span className={`fm-source-count ${count > 1 ? 'is-many' : ''}`}>✓ {count}</span>;
}

function TypeBadge({ field }: { field: FieldInfo }) {
  const meta = TYPE_META[field.type] ?? TYPE_META.STRING;
  return <Tooltip title={`${field.fullType ?? field.type}${field.nullable === false ? ' · NOT NULL' : ''}`}><span className="fm-type-badge" style={{ background: meta.bg, color: meta.color }}>{meta.label}</span></Tooltip>;
}

function MappingCanvas({ spec, sourceFields, targetFields, edgePaths, rowIssues, selectedIndex, activeMappingIndexes, onSelect, onMappingHover, onUpdate, onDelete, onMove, onAdd }: {
  spec: FieldMappingSpec;
  sourceFields: FieldInfo[];
  targetFields: FieldInfo[];
  edgePaths: EdgePath[];
  rowIssues: Map<number, MappingIssue[]>;
  selectedIndex: number | null;
  activeMappingIndexes: Set<number>;
  onSelect: (index: number) => void;
  onMappingHover: (index: number | null) => void;
  onUpdate: (index: number, patch: Partial<MappingRule>) => void;
  onDelete: (index: number) => void;
  onMove: (index: number, direction: -1 | 1) => void;
  onAdd: (kind: RowKind) => void;
}) {
  return (
    <section className="fm-canvas">
      <EdgeLayer paths={edgePaths} />
      <div className="fm-card-list">
        {spec.mappings.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="从左侧选择源字段，再点击右侧目标字段创建映射" />
        ) : spec.mappings.map((mapping, index) => (
          <MappingCard
            key={index}
            index={index}
            mapping={mapping}
            issues={rowIssues.get(index) ?? []}
            sourceFields={sourceFields}
            targetFields={targetFields}
            selected={selectedIndex === index}
            active={activeMappingIndexes.has(index)}
            onSelect={() => onSelect(index)}
            onHover={(active) => onMappingHover(active ? index : null)}
            onUpdate={(patch) => onUpdate(index, patch)}
            onDelete={() => onDelete(index)}
            onMoveUp={() => onMove(index, -1)}
            onMoveDown={() => onMove(index, 1)}
          />
        ))}
        <Dropdown
          trigger={['click']}
          menu={{
            items: ADD_MENU_ITEMS,
            onClick: ({ key }) => onAdd(key as RowKind),
          }}
        >
          <Button className="fm-add-card" icon={<PlusOutlined />}>添加映射规则</Button>
        </Dropdown>
      </div>
    </section>
  );
}

function EdgeLayer({ paths }: { paths: EdgePath[] }) {
  return (
    <svg className="fm-edge-layer">
      {paths.map((path) => <path key={path.id} className={`fm-edge is-${path.state}`} d={path.d} />)}
    </svg>
  );
}

function MappingCard({ index, mapping, issues, sourceFields, targetFields, selected, active, onSelect, onHover, onUpdate, onDelete, onMoveUp, onMoveDown }: {
  index: number;
  mapping: MappingRule;
  issues: MappingIssue[];
  sourceFields: FieldInfo[];
  targetFields: FieldInfo[];
  selected: boolean;
  active: boolean;
  onSelect: () => void;
  onHover: (active: boolean) => void;
  onUpdate: (patch: Partial<MappingRule>) => void;
  onDelete: () => void;
  onMoveUp: () => void;
  onMoveDown: () => void;
}) {
  const kind = ruleKind(mapping);
  const disabled = mapping.enabled === false;
  return (
    <article className={`fm-mapping-card is-${kind} ${selected ? 'is-selected' : ''} ${active ? 'is-active' : ''} ${disabled ? 'is-disabled' : ''} ${issues.some((i) => i.severity === 'ERROR') ? 'is-error' : issues.length ? 'is-warn' : ''}`} data-mapping-index={index} onClick={onSelect} onMouseEnter={() => onHover(true)} onMouseLeave={() => onHover(false)}>
      {kind !== 'constant' && <span className="fm-card-port fm-card-port-in" data-port="card-in" />}
      <CardBody kind={kind} mapping={mapping} sourceFields={sourceFields} targetFields={targetFields} onUpdate={onUpdate} />
      <span className="fm-card-port fm-card-port-out" data-port="card-out" />
      <div className="fm-card-actions">
        <Tooltip title={disabled ? '启用此映射' : '禁用此映射'}><Button size="small" type="text" icon={disabled ? <PlayCircleOutlined /> : <PauseCircleOutlined />} onClick={(e) => { e.stopPropagation(); onUpdate({ enabled: disabled }); }} /></Tooltip>
        <Tooltip title="上移"><Button size="small" type="text" onClick={(e) => { e.stopPropagation(); onMoveUp(); }}>↑</Button></Tooltip>
        <Tooltip title="下移"><Button size="small" type="text" onClick={(e) => { e.stopPropagation(); onMoveDown(); }}>↓</Button></Tooltip>
        <Tooltip title="删除映射"><Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={(e) => { e.stopPropagation(); onDelete(); }} /></Tooltip>
      </div>
      {issues.length > 0 && <ErrorBadge errors={issues} />}
    </article>
  );
}

function CardBody({ kind, mapping, sourceFields, targetFields, onUpdate }: { kind: RowKind; mapping: MappingRule; sourceFields: FieldInfo[]; targetFields: FieldInfo[]; onUpdate: (patch: Partial<MappingRule>) => void }) {
  if (kind === 'constant') {
    return <><ConstantChip value={mapping.constant} onChange={(constant) => onUpdate({ constant })} /><ArrowOrFx transform={mapping.transform} onChange={(transform) => onUpdate({ transform })} /><FieldSelect side="target" value={mapping.to} fields={targetFields} onChange={(to) => onUpdate({ to })} /></>;
  }
  if (kind === 'many') {
    return <><span className="fm-merge-icon">⊕</span><MultiFieldSelect value={mapping.fromList ?? []} fields={sourceFields} onChange={(fromList) => onUpdate({ fromList })} /><ArrowOrFx transform={mapping.transform ?? { fn: 'concat', args: [' '] }} onChange={(transform) => onUpdate({ transform })} /><FieldSelect side="target" value={mapping.to} fields={targetFields} onChange={(to) => onUpdate({ to })} /></>;
  }
  if (kind === 'expression') {
    return <><span className="fm-fx-large">fx</span><Input className="fm-expression-input" size="small" value={mapping.expression ?? ''} placeholder="upper(${field:dept_code})" onChange={(e) => onUpdate({ expression: e.target.value })} /><FieldSelect side="target" value={mapping.to} fields={targetFields} onChange={(to) => onUpdate({ to })} /></>;
  }
  if (kind === 'lookup') {
    return <LookupBody mapping={mapping} sourceFields={sourceFields} targetFields={targetFields} onUpdate={onUpdate} />;
  }
  if (kind === 'conditional') {
    return <div className="fm-conditional-body"><CondLine label="if" value={mapping.when ?? ''} onChange={(when) => onUpdate({ when })} /><CondLine label="then" value={mapping.then?.constant != null ? String(mapping.then.constant) : ''} onChange={(v) => onUpdate({ then: { constant: v } })} /><CondLine label="else" value={mapping.else?.constant != null ? String(mapping.else.constant) : ''} onChange={(v) => onUpdate({ else: { constant: v } })} /><FieldSelect side="target" value={mapping.to} fields={targetFields} onChange={(to) => onUpdate({ to })} /></div>;
  }
  return <><FieldSelect side="source" value={mapping.from} fields={sourceFields} onChange={(from) => onUpdate({ from })} /><ArrowOrFx transform={mapping.transform} onChange={(transform) => onUpdate({ transform })} /><FieldSelect side="target" value={mapping.to} fields={targetFields} onChange={(to) => onUpdate({ to })} /></>;
}

function FieldSelect({ side, value, fields, onChange }: { side: FieldSide; value?: string; fields: FieldInfo[]; onChange: (path: string) => void }) {
  const options = useMemo(
    () => fields.map((field) => ({
      value: field.path,
      label: field.label ? `${field.label} ${field.name}` : field.name,
      searchText: `${field.path} ${field.label ?? ''} ${field.name}`.toLowerCase(),
    })),
    [fields]
  );
  return (
    <Select
      size="middle"
      showSearch
      value={value ? normalizePath(value) : undefined}
      className={`fm-field-chip fm-field-chip-${side}`}
      placeholder={side === 'source' ? '选择源字段' : '选择目标字段'}
      onChange={onChange}
      popupMatchSelectWidth={260}
      options={options}
      filterOption={(input, option) => String(option?.searchText ?? option?.value ?? '').includes(input.toLowerCase())}
    />
  );
}

function MultiFieldSelect({ value, fields, onChange }: { value: string[]; fields: FieldInfo[]; onChange: (paths: string[]) => void }) {
  const options = useMemo(
    () => fields.map((field) => ({
      value: field.path,
      label: field.label ? `${field.label} ${field.name}` : field.name,
      searchText: `${field.path} ${field.label ?? ''} ${field.name}`.toLowerCase(),
    })),
    [fields]
  );
  return <Select mode="multiple" size="middle" value={value.filter(Boolean).map(normalizePath)} className="fm-field-chip fm-field-chip-source is-multi" placeholder="选择多个源字段" onChange={onChange} options={options} filterOption={(input, option) => String(option?.searchText ?? option?.value ?? '').includes(input.toLowerCase())} />;
}

function ArrowOrFx({ transform, onChange }: { transform?: TransformSpec; onChange: (transform: TransformSpec | undefined) => void }) {
  return (
    <Popover trigger="click" content={<TransformPicker value={transform} onChange={onChange} />}>
      {transform?.fn ? <button className="fm-fx-badge">fx<sup>{transform.fn[0]?.toUpperCase()}</sup></button> : <button className="fm-arrow">→</button>}
    </Popover>
  );
}

function TransformPicker({ value, onChange }: { value?: TransformSpec; onChange: (transform: TransformSpec | undefined) => void }) {
  const [fn, setFn] = useState(value?.fn ?? '');
  const [args, setArgs] = useState(formatArgs(value?.args));
  const transformSignature = `${value?.fn ?? ''}:${JSON.stringify(value?.args ?? [])}`;
  useEffect(() => {
    setFn(value?.fn ?? '');
    setArgs(formatArgs(value?.args));
  }, [transformSignature]);
  return (
    <div className="fm-transform-picker">
      <Typography.Text strong>选择转换函数</Typography.Text>
      <Select size="small" showSearch value={fn} options={TRANSFORM_OPTIONS} onChange={setFn} />
      {fn && (
        <div className="fm-transform-args">
          <Typography.Text type="secondary">函数参数（JSON）</Typography.Text>
          <Input.TextArea
            value={args}
            placeholder={'参数 JSON，例如 [{"0":"未知","1":"男","2":"女"}]'}
            autoSize={{ minRows: 6, maxRows: 12 }}
            spellCheck={false}
            onChange={(event) => setArgs(event.target.value)}
          />
        </div>
      )}
      <Space>
        <Button size="small" onClick={() => onChange(undefined)}>清除</Button>
        <Button size="small" type="primary" onClick={() => onChange(fn ? { fn, args: parseArgs(args) } : undefined)}>应用</Button>
      </Space>
    </div>
  );
}

function ConstantChip({ value, onChange }: { value: unknown; onChange: (value: string | number | boolean | null) => void }) {
  return <Input size="small" className="fm-constant-chip" value={value == null ? '' : String(value)} placeholder="常量值" onChange={(e) => onChange(parseTypedValue(e.target.value))} />;
}

function CondLine({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return <div className="fm-cond-line"><Tag>{label}</Tag><Input size="small" value={value} onChange={(e) => onChange(e.target.value)} /></div>;
}

function LookupBody({ mapping, sourceFields, targetFields, onUpdate }: { mapping: MappingRule; sourceFields: FieldInfo[]; targetFields: FieldInfo[]; onUpdate: (patch: Partial<MappingRule>) => void }) {
  const { message } = AntdApp.useApp();
  const [open, setOpen] = useState(false);
  const lookup: LookupSpec = mapping.lookup ?? { sql: '' };
  const summary = isRecordLookup(lookup) ? '多值翻译' : lookup.resultColumn ? `SQL · ${lookup.resultColumn}` : 'SQL';
  return (
    <>
      <FieldSelect side="source" value={mapping.from} fields={sourceFields} onChange={(from) => onUpdate({ from })} />
      <Popover
        trigger="click"
        placement="bottom"
        destroyTooltipOnHide
        open={open}
        onOpenChange={setOpen}
        content={<LookupConfig lookup={lookup} sourceField={mapping.from} onChange={(next) => onUpdate({ lookup: next })} onApply={() => { setOpen(false); message.success('字典查询映射已应用'); }} />}
      >
        <button className="fm-lookup-badge" title={isRecordLookup(lookup) ? '查看多值翻译配置' : '编辑字典查询语句'} onClick={(e) => e.stopPropagation()}>{summary}</button>
      </Popover>
      <FieldSelect side="target" value={mapping.to} fields={targetFields} onChange={(to) => onUpdate({ to })} />
    </>
  );
}

function LookupConfig({ lookup, sourceField, onChange, onApply }: { lookup: LookupSpec; sourceField?: string; onChange: (lookup: LookupSpec) => void; onApply: () => void }) {
  const { message } = AntdApp.useApp();
  const [sql, setSql] = useState(lookup.sql ?? '');
  const [resultColumn, setResultColumn] = useState(lookup.resultColumn ?? '');
  const [onMissing, setOnMissing] = useState<NonNullable<LookupSpec['onMissing']>>(lookup.onMissing ?? 'NULL');
  const [separator, setSeparator] = useState(lookup.multiValueSeparator ?? ',');
  const recordLookup = isRecordLookup(lookup);
  useEffect(() => {
    setSql(lookup.sql ?? '');
    setResultColumn(lookup.resultColumn ?? '');
    setOnMissing(lookup.onMissing ?? 'NULL');
    setSeparator(lookup.multiValueSeparator ?? ',');
  }, [lookup]);
  const insertTemplate = () => {
    setSql("SELECT dict_label\nFROM sys_dict_data\nWHERE dict_type = 'sys_status'\n  AND dict_value = ?");
    if (!resultColumn) setResultColumn('');
  };
  const apply = () => {
    if (recordLookup) {
      if (!sourceField) { message.warning('请先选择左侧源字段'); return; }
      if (!separator || separator.length > 8) { message.warning('分隔符须为 1–8 个字符'); return; }
      onChange({ ...lookup, multiValueSeparator: separator, onMissing });
      onApply();
      return;
    }
    const nextSql = sql.trim();
    if (!nextSql) {
      message.warning('请填写字典查询 SQL');
      return;
    }
    if (!sourceField) {
      message.warning('请先选择左侧源字段');
      return;
    }
    if (!nextSql.includes('?') && !/\$\{field:[^}]+}/.test(nextSql)) {
      message.warning('SQL 中请使用 ? 作为源字段值占位符');
      return;
    }
    onChange({ ...lookup, sql: nextSql, resultColumn: resultColumn.trim() || undefined, onMissing });
    onApply();
  };
  return (
    <div className="fm-lookup-config" onClick={(e) => e.stopPropagation()}>
      <Typography.Text strong>{recordLookup ? '多值翻译' : '字典查询映射'}</Typography.Text>
      {recordLookup ? <>
        <Typography.Paragraph type="secondary" className="fm-lookup-hint">
          按分隔符逐个翻译编码，保留原有顺序和重复值。编码与名称关联沿用资源登记配置。
        </Typography.Paragraph>
        {lookup.query ? <Input.TextArea value={lookup.query} readOnly autoSize={{ minRows: 3, maxRows: 8 }} />
          : <div>{Object.entries(lookup.values ?? {}).slice(0, 20).map(([code, label]) => <Tag key={code}>{code} → {label}</Tag>)}
            {Object.keys(lookup.values ?? {}).length > 20 && <Typography.Text type="secondary">共 {Object.keys(lookup.values ?? {}).length} 个编码</Typography.Text>}</div>}
        <Input size="small" value={separator} addonBefore="分隔符" onChange={(event) => setSeparator(event.target.value)} />
      </> : <>
      <Typography.Paragraph type="secondary" className="fm-lookup-hint">
        使用 <code>?</code> 作为源字段值占位符，系统会自动绑定左侧已选字段；结果列可留空，默认取 SELECT 第一列。
      </Typography.Paragraph>
      <Input.TextArea
        className="fm-lookup-sql"
        autoSize={{ minRows: 5, maxRows: 12 }}
        value={sql}
        spellCheck={false}
        placeholder="SELECT dict_name FROM sym_dict_t WHERE dict_code = ?"
        onChange={(e) => setSql(e.target.value)}
      />
      </>}
      <div className="fm-lookup-row">
        {!recordLookup && <Input size="small" value={resultColumn} placeholder="结果列名（可选，默认取首列）" onChange={(e) => setResultColumn(e.target.value)} />}
        <Select
          size="small"
          value={onMissing}
          style={{ width: 150 }}
          onChange={(v) => setOnMissing(v)}
          options={[
            { value: 'NULL', label: '查不到置 NULL' },
            { value: 'KEEP_SOURCE', label: '查不到保留原值' },
            { value: 'FAIL', label: '查不到则失败' },
          ]}
        />
      </div>
      <Space className="fm-lookup-actions">
        {!recordLookup && <Button size="small" onClick={insertTemplate}>插入字典模板</Button>}
        <Button size="small" type="primary" onClick={apply}>应用</Button>
      </Space>
    </div>
  );
}

function ErrorBadge({ errors }: { errors: MappingIssue[] }) {
  if (!errors.length) return null;
  return (
    <Popover content={<div className="mapping-error-popover">{errors.map((e, i) => <div key={i}><Tag color={e.severity === 'ERROR' ? 'red' : 'orange'}>{e.code}</Tag>{e.message}</div>)}</div>}>
      <span className="fm-card-error-dot">!</span>
    </Popover>
  );
}

function BottomBar({ spec, issues, onChange }: { spec: FieldMappingSpec; issues: { errors: MappingIssue[]; warnings: MappingIssue[] }; onChange: (spec: FieldMappingSpec) => void }) {
  return (
    <footer className="fm-bottombar">
      <Checkbox checked={!!spec.passthroughUnmapped} onChange={(e) => onChange({ ...spec, passthroughUnmapped: e.target.checked })}>未映射字段直通</Checkbox>
      <span className="fm-bottom-status">{issues.warnings.length} 条警告 · {issues.errors.length} 错误</span>
      <div className="fm-bottom-actions" aria-label="映射处理策略">
        <Tooltip title="控制来源表缺少映射字段时的处理方式"><span className="fm-policy-title">处理策略</span></Tooltip>
        <span className="fm-policy-label">缺少源字段</span>
        <Select size="small" value={spec.onMissingSource ?? 'NULL'} style={{ width: 136 }} onChange={(v) => onChange({ ...spec, onMissingSource: v })} options={[{ value: 'NULL', label: '置 NULL' }, { value: 'SKIP_ROW', label: '跳过行' }, { value: 'FAIL', label: '失败' }]} />
        <span className="fm-policy-label">类型不匹配</span>
        <Select size="small" value={spec.onTypeMismatch ?? 'CAST'} style={{ width: 136 }} onChange={(v) => onChange({ ...spec, onTypeMismatch: v })} options={[{ value: 'CAST', label: '尝试转换' }, { value: 'SKIP_ROW', label: '跳过行' }, { value: 'FAIL', label: '失败' }]} />
      </div>
    </footer>
  );
}

function PreviewDrawer({ open, data, onClose }: { open: boolean; data: PreviewResponse | null; onClose: () => void }) {
  const columns: ColumnsType<Record<string, unknown>> = Object.keys(data?.resultRows?.[0] ?? {}).map((key) => ({ title: key, dataIndex: key, ellipsis: true }));
  const failedRows = data?.rowResults?.filter((row) => !row.success) ?? [];
  return (
    <Drawer title="映射预览" placement="bottom" height={420} open={open} onClose={onClose} getContainer={getNifiOverlayContainer}>
      {!data ? <Empty /> : <>
        <Alert type="info" showIcon message={`样本输出 ${data.resultRows.length} 行`} style={{ marginBottom: 12 }} />
        {failedRows.length > 0 && <Alert type="error" showIcon message="预览中存在失败行" description={failedRows.map((row) => <div key={row.rowIndex}>第 {row.rowIndex + 1} 行：{row.errors.join('；')}</div>)} style={{ marginBottom: 12 }} />}
        {columns.length === 0 ? <Empty description="暂无输出字段" /> : <Table size="small" rowKey={(_, i) => String(i)} columns={columns} dataSource={data.resultRows} pagination={false} scroll={{ x: true, y: 220 }} />}
      </>}
    </Drawer>
  );
}

function RecommendModal({ open, recommendations, onCancel, onApply }: { open: boolean; recommendations: LocalRecommendation[]; onCancel: () => void; onApply: (rows: LocalRecommendation[]) => void }) {
  const [selected, setSelected] = useState<React.Key[]>([]);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });
  useEffect(() => {
    setSelected(recommendations.filter((r) => r.confidence >= 0.85).map((r) => r.id));
    setPagination({ current: 1, pageSize: 10 });
  }, [recommendations]);
  const picked = recommendations.filter((r) => selected.includes(r.id));
  return (
    <Modal open={open} onCancel={onCancel} title="智能推荐字段映射" width={760} onOk={() => onApply(picked)} okText={`应用 ${picked.length} 条已选映射`} getContainer={getNifiOverlayContainer}>
      {recommendations.length === 0 ? <Empty description="暂无可推荐映射" /> : <Table
        size="small"
        rowKey="id"
        dataSource={recommendations}
        scroll={{ y: '500px' }}
        pagination={{
          current: pagination.current,
          pageSize: pagination.pageSize,
          total: recommendations.length,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 条推荐`,
          onChange: (page, pageSize) => setPagination({ current: page, pageSize }),
        }}
        rowSelection={{ selectedRowKeys: selected, onChange: setSelected, preserveSelectedRowKeys: true }}
        columns={[
          { title: '源字段', dataIndex: 'from', render: (_v: string, record: LocalRecommendation) => <FieldLabelCell path={record.from} label={record.fromLabel} /> },
          { title: '', width: 30, render: () => '→' },
          { title: '目标字段', dataIndex: 'to', render: (_v: string, record: LocalRecommendation) => <FieldLabelCell path={record.to} label={record.toLabel} /> },
          { title: '推荐依据', dataIndex: 'reason' },
          { title: '可信度', dataIndex: 'confidence', width: 110, render: (v: number) => <Progress percent={Math.round(v * 100)} size="small" showInfo={false} /> },
        ]}
      />}
    </Modal>
  );
}

function FieldLabelCell({ path, label }: { path: string; label?: string }) {
  if (!label) return <>{displayPath(path)}</>;
  return <span className="fm-recommend-cell"><span className="fm-recommend-label">{label}</span><span className="fm-recommend-name">{displayPath(path)}</span></span>;
}

function TemplateModal({ open, sourceFields, targetFields, onCancel, onApply }: { open: boolean; sourceFields: FieldInfo[]; targetFields: FieldInfo[]; onCancel: () => void; onApply: (rules: MappingRule[]) => void }) {
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

function parseLocal(value: unknown): FieldMappingSpec {
  if (typeof value === 'string' && value.trim()) {
    try {
      const parsed = JSON.parse(value);
      if (parsed?.version === '1.0' && Array.isArray(parsed.mappings)) return parsed;
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) return migrateLegacyLocal(parsed as Record<string, unknown>);
    } catch {
      return emptyFieldMappingSpec();
    }
  }
  if (value && typeof value === 'object' && (value as FieldMappingSpec).version === '1.0') return value as FieldMappingSpec;
  if (value && typeof value === 'object' && !Array.isArray(value)) return migrateLegacyLocal(value as Record<string, unknown>);
  return emptyFieldMappingSpec();
}

function stableMappingValue(value: unknown) {
  if (typeof value === 'string') return value.trim();
  try {
    return JSON.stringify(value ?? null, null, 2);
  } catch {
    return '';
  }
}

function ensureMappingSpec(value: unknown): FieldMappingSpec {
  const parsed = value && typeof value === 'object' ? value as Partial<FieldMappingSpec> : {};
  return {
    ...emptyFieldMappingSpec(),
    ...parsed,
    version: '1.0',
    mappings: Array.isArray(parsed.mappings) ? parsed.mappings.map(normalizeRule) : [],
  };
}

function migrateLegacyLocal(value: Record<string, unknown>): FieldMappingSpec {
  const mappings = Object.entries(value)
    .filter(([from, to]) => typeof to === 'string' && !isPlaceholderRule(from, to))
    .map(([from, to]) => ({ from: normalizePath(from), to: normalizePath(String(to)) }));
  return { ...emptyFieldMappingSpec(), mappings };
}

function isPlaceholderRule(from?: string, to?: unknown) {
  return normalizePath(from ?? '') === '/source_field' && normalizePath(String(to ?? '')) === '/target_field';
}

function normalizeRule(rule: MappingRule): MappingRule {
  const next = { ...rule };
  if (next.from) next.from = normalizePath(next.from);
  if (next.to) next.to = normalizePath(next.to);
  if (next.fromList) next.fromList = next.fromList.map(normalizePath).filter(Boolean);
  return next;
}

function normalizePath(value: string) {
  const raw = (value ?? '').trim();
  if (!raw) return '';
  if (raw.startsWith('/')) return raw;
  if (raw.startsWith('root.')) return `/${raw.slice(5).replaceAll('.', '/')}`;
  return `/${raw.replaceAll('.', '/')}`;
}

function displayPath(value?: string) {
  const raw = (value ?? '').trim();
  if (!raw) return '';
  return raw.startsWith('/') ? raw.slice(1) : raw;
}

function fieldName(path: string) {
  return displayPath(path).split('/').pop() ?? displayPath(path);
}

function toFieldInfo(path: string, label?: string): FieldInfo {
  const normalized = normalizePath(path);
  return { path: normalized, name: fieldName(normalized), label: label || undefined, type: guessType(normalized), isPk: ['id', 'tid'].includes(fieldName(normalized).toLowerCase()) };
}

function guessType(path: string): FieldType {
  const name = fieldName(path).toLowerCase();
  if (name.includes('time') || name.includes('date')) return 'DATETIME';
  if (name === 'id' || name.endsWith('_id') || name === 'tid' || name.includes('count') || name.includes('num') || name.includes('age')) return 'INT';
  if (name.startsWith('is_') || name.includes('enabled')) return 'BOOL';
  return 'STRING';
}

function mergeFields(a: FieldInfo[], b: FieldInfo[]) {
  const map = new Map<string, FieldInfo>();
  [...a, ...b].forEach((field) => { if (field.path) map.set(field.path, field); });
  return Array.from(map.values()).sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'));
}

function ruleKind(rule: MappingRule): RowKind {
  if (rule.when || rule.then || rule.else) return 'conditional';
  if ('constant' in rule) return 'constant';
  if (rule.lookup) return 'lookup';
  if (rule.expression) return 'expression';
  if (rule.fromList) return 'many';
  return 'field';
}

function rowKindLabel(kind: RowKind) {
  const labels: Record<RowKind, string> = {
    field: '字段直连',
    many: '多字段合并',
    constant: '常量赋值',
    expression: '表达式',
    lookup: '字典查询',
    conditional: '条件分支',
  };
  return labels[kind];
}

function buildFieldLabelMap(fields: FieldInfo[]) {
  const map = new Map<string, string>();
  fields.forEach((field) => {
    if (!field.label) return;
    map.set(field.path, field.label);
    map.set(fieldKey(field.path), field.label);
  });
  return map;
}

function fieldLabel(path: string | undefined, labelByPath: Map<string, string>) {
  const normalized = normalizePath(path ?? '');
  if (!normalized) return undefined;
  return labelByPath.get(normalized) ?? labelByPath.get(fieldKey(normalized));
}

function pathsFromMapping(mapping: MappingRule) {
  const paths = new Set<string>();
  if (mapping.from) paths.add(normalizePath(mapping.from));
  mapping.fromList?.forEach((path) => path && paths.add(normalizePath(path)));
  extractExpressionFields(mapping.expression).forEach((path) => paths.add(path));
  extractExpressionFields(mapping.lookup?.sql).forEach((path) => paths.add(path));
  extractExpressionFields(mapping.when).forEach((path) => paths.add(path));
  return Array.from(paths);
}

function extractExpressionFields(expression?: string) {
  const paths: string[] = [];
  if (!expression) return paths;
  const re = /\$\{field:([^}]+)}/g;
  let match: RegExpExecArray | null;
  while ((match = re.exec(expression))) paths.push(normalizePath(match[1]));
  return paths;
}

function inferSourceFields(spec: FieldMappingSpec) {
  const paths = new Set<string>();
  spec.mappings.forEach((mapping) => pathsFromMapping(mapping).forEach((path) => paths.add(path)));
  return Array.from(paths);
}

function inferTargetFields(spec: FieldMappingSpec) {
  return spec.mappings.map((mapping) => mapping.to).filter(Boolean).map((path) => normalizePath(path as string));
}

function collectMappedSources(mappings: MappingRule[]) {
  const out: Record<string, string[]> = {};
  mappings.forEach((mapping) => {
    pathsFromMapping(mapping).forEach((from) => {
      if (!mapping.to) return;
      out[from] = [...(out[from] ?? []), displayPath(mapping.to)];
    });
  });
  return out;
}

function collectMappedTargets(mappings: MappingRule[]) {
  const out: Record<string, string[]> = {};
  mappings.forEach((mapping) => {
    if (!mapping.to) return;
    const to = normalizePath(mapping.to);
    const sources = pathsFromMapping(mapping).map(displayPath);
    out[to] = [...(out[to] ?? []), ...(sources.length ? sources : ['常量'])];
  });
  return out;
}

function edgeState(mapping: MappingRule, index: number, activeMappingIndexes: Set<number>): EdgePath['state'] {
  if (mapping.enabled === false) return 'disabled';
  return activeMappingIndexes.has(index) ? 'active' : 'idle';
}

function collectRelatedMappingIndexes(spec: FieldMappingSpec, path: string | null, mappingIndex: number | null) {
  const indexes = new Set<number>();
  if (mappingIndex != null) indexes.add(mappingIndex);
  if (!path) return indexes;
  const normalized = normalizePath(path);
  spec.mappings.forEach((mapping, index) => {
    const sources = pathsFromMapping(mapping).map(normalizePath);
    const target = mapping.to ? normalizePath(mapping.to) : '';
    const touchesSource = sources.some((source) => source === normalized || fieldKey(source) === fieldKey(normalized));
    const touchesTarget = target && (target === normalized || fieldKey(target) === fieldKey(normalized));
    if (touchesSource || touchesTarget) indexes.add(index);
  });
  return indexes;
}

function collectRelatedFieldKeys(spec: FieldMappingSpec, path: string | null, mappingIndex: number | null) {
  const keys = new Set<string>();
  const normalized = path ? normalizePath(path) : '';
  if (normalized) keys.add(fieldKey(normalized));
  spec.mappings.forEach((mapping, index) => {
    const sources = pathsFromMapping(mapping).map(normalizePath);
    const target = mapping.to ? normalizePath(mapping.to) : '';
    const touchesSource = normalized && sources.some((source) => source === normalized || fieldKey(source) === fieldKey(normalized));
    const touchesTarget = normalized && target && (target === normalized || fieldKey(target) === fieldKey(normalized));
    if (touchesSource || touchesTarget || index === mappingIndex) {
      sources.forEach((source) => keys.add(fieldKey(source)));
      if (target) keys.add(fieldKey(target));
    }
  });
  return keys;
}

function fieldKey(path: string) {
  return fieldName(path).trim().toLowerCase();
}

function buildSampleRows(spec: FieldMappingSpec) {
  const row: Record<string, unknown> = {};
  inferSourceFields(spec).forEach((path) => { row[fieldName(path)] = `${fieldName(path)}_样例`; });
  if (Object.keys(row).length === 0) row.source_field = '样例值';
  return [row];
}

function formatArgs(args: unknown[] | undefined) {
  return args === undefined ? '' : JSON.stringify(args, null, 2);
}

function parseArgs(text: string): unknown[] | undefined {
  const trimmed = text.trim();
  if (!trimmed) return undefined;
  try {
    const parsed = JSON.parse(trimmed);
    return Array.isArray(parsed) ? parsed : [parsed];
  } catch {
    return [trimmed];
  }
}

function parseTypedValue(text: string): string | number | boolean | null {
  const trimmed = text.trim();
  if (trimmed === 'null') return null;
  if (trimmed === 'true') return true;
  if (trimmed === 'false') return false;
  if (trimmed && Number.isFinite(Number(trimmed))) return Number(trimmed);
  return text;
}

function groupIssuesByRow(issues: MappingIssue[]) {
  const grouped = new Map<number, MappingIssue[]>();
  issues.forEach((issue) => {
    const match = /mappings\[(\d+)]/.exec(issue.path ?? '');
    if (!match) return;
    const index = Number(match[1]);
    grouped.set(index, [...(grouped.get(index) ?? []), issue]);
  });
  return grouped;
}

function findFieldRow(root: HTMLElement, side: FieldSide, path: string) {
  return Array.from(root.querySelectorAll<HTMLElement>(`.fm-field-row[data-side="${side}"]`))
    .find((row) => row.dataset.fieldPath === path) ?? null;
}

function bezier(x1: number, y1: number, x2: number, y2: number) {
  const dx = x2 - x1;
  const direction = dx >= 0 ? 1 : -1;
  const offset = Math.min(Math.max(Math.abs(dx) * 0.38, 24), Math.max(Math.abs(dx) / 2, 1));
  return `M ${x1},${y1} C ${x1 + direction * offset},${y1} ${x2 - direction * offset},${y2} ${x2},${y2}`;
}

function FieldMappingEditorSkeleton() {
  return (
    <div className="field-mapping-editor field-mapping-editor-v11">
      <header className="fm-topbar">
        <div className="fm-topbar-left">
          <Typography.Text strong>字段映射</Typography.Text>
          <span className="fm-summary">加载中...</span>
        </div>
      </header>
      <div className="fm-layout is-loading">
        <FieldPanelSkeleton side="source" count={8} />
        <MappingCanvasSkeleton />
        <FieldPanelSkeleton side="target" count={8} />
      </div>
    </div>
  );
}

function FieldPanelSkeleton({ side, count }: { side: FieldSide; count: number }) {
  const label = side === 'source' ? '源字段' : '目标字段';
  return (
    <aside className={`fm-field-panel fm-field-panel-${side} fm-field-panel-skeleton`}>
      <header className="fm-panel-header">
        <div className="fm-panel-title">{label}</div>
        <div className="fm-panel-subtitle">加载中...</div>
      </header>
      <div className="fm-field-list">
        <Skeleton
          active
          title={false}
          paragraph={{ rows: count, width: ['60%', '75%', '50%', '80%', '55%', '70%', '65%', '45%'] }}
        />
      </div>
    </aside>
  );
}

function MappingCanvasSkeleton() {
  return (
    <section className="fm-canvas">
      <div className="fm-card-list" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 200 }}>
        <Spin tip="正在加载字段映射..." />
      </div>
    </section>
  );
}
