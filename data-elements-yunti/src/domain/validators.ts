import type { Flow, FlowNode } from './flow-dsl';
import {
  buildAdjacency,
  canReach,
  duplicateEdges,
  reachableFrom,
  selfLoops,
  topoSort,
} from './graph-utils';
import { genId } from '@/utils/id';

export type Severity = 'error' | 'warning';

export interface Problem {
  id: string;
  severity: Severity;
  message: string;
  nodeId?: string;
  edgeId?: string;
  fixHint?: string;
}

function problem(p: Omit<Problem, 'id'>): Problem {
  return { id: genId('pb'), ...p };
}

/** Fields that must be present (non-empty) per node type. */
const requiredFields: Record<string, { field: string; label: string }[]> = {
  trigger: [{ field: 'path', label: '接口路径' }],
  'sql-query': [
    { field: 'datasource', label: '数据源' },
    { field: 'sql', label: 'SQL 语句' },
  ],
  'sql-exec': [
    { field: 'datasource', label: '数据源' },
    { field: 'sql', label: 'SQL 语句' },
  ],
  'kafka-consume': [
    { field: 'datasource', label: '数据源' },
    { field: 'topic', label: '主题' },
  ],
  'kafka-produce': [
    { field: 'datasource', label: '数据源' },
    { field: 'topic', label: '主题' },
  ],
  'es-query': [
    { field: 'datasource', label: '数据源' },
    { field: 'index', label: '索引' },
  ],
  'http-call': [{ field: 'url', label: '请求 URL' }],
  redis: [
    { field: 'datasource', label: '数据源' },
    { field: 'key', label: 'Key' },
  ],
  script: [{ field: 'code', label: '脚本内容' }],
  transform: [{ field: 'sourceVar', label: '源变量' }],
  filter: [
    { field: 'sourceVar', label: '源变量' },
    { field: 'predicate', label: '过滤条件' },
  ],
  aggregate: [{ field: 'sourceVar', label: '源变量' }],
  sort: [{ field: 'sourceVar', label: '源变量' }],
  paginate: [{ field: 'sourceVar', label: '源变量' }],
  join: [
    { field: 'leftVar', label: '左侧变量' },
    { field: 'rightVar', label: '右侧变量' },
  ],
  condition: [{ field: 'expr', label: '条件表达式' }],
  switch: [{ field: 'expr', label: '匹配表达式' }],
  foreach: [{ field: 'listVar', label: '迭代数组' }],
  subflow: [{ field: 'flowId', label: '子编排' }],
  'set-var': [{ field: 'name', label: '变量名' }],
  const: [{ field: 'name', label: '常量名' }],
};

function isEmpty(value: unknown): boolean {
  return value === undefined || value === null || value === '';
}

function checkRequired(node: FlowNode): Problem[] {
  const specs = requiredFields[node.type];
  if (!specs) return [];
  const problems: Problem[] = [];
  for (const { field, label } of specs) {
    if (isEmpty((node.data as Record<string, unknown>)[field])) {
      problems.push(
        problem({
          severity: 'error',
          nodeId: node.id,
          message: `「${node.name ?? node.type}」缺少必填项：${label}`,
          fixHint: `请在配置面板填写「${label}」`,
        }),
      );
    }
  }
  return problems;
}

/** Validate a Flow DSL, returning all structural + config problems. */
export function validateFlow(flow: Flow): Problem[] {
  const problems: Problem[] = [];

  // --- singleton / terminal counts ---
  const triggers = flow.nodes.filter((n) => n.type === 'trigger');
  const responses = flow.nodes.filter((n) => n.type === 'response');
  if (triggers.length === 0) {
    problems.push(problem({ severity: 'error', message: '缺少 API 触发器节点（trigger）' }));
  } else if (triggers.length > 1) {
    problems.push(
      problem({ severity: 'error', message: '只能有一个 API 触发器节点（trigger）' }),
    );
  }
  if (responses.length === 0) {
    problems.push(problem({ severity: 'error', message: '缺少响应节点（response）' }));
  }

  // --- edge legality ---
  for (const e of selfLoops(flow)) {
    problems.push(
      problem({ severity: 'error', edgeId: e.id, message: '存在自环连线（节点连向自身）' }),
    );
  }
  for (const e of duplicateEdges(flow)) {
    problems.push(problem({ severity: 'error', edgeId: e.id, message: '存在重复连线' }));
  }

  // --- DAG / cycle ---
  const { cyclic } = topoSort(flow);
  if (cyclic.length > 0) {
    problems.push(
      problem({
        severity: 'error',
        nodeId: cyclic[0],
        message: `编排存在环，无法编译（涉及 ${cyclic.length} 个节点）`,
        fixHint: '移除导致环的连线，保持有向无环图（DAG）',
      }),
    );
  }

  // --- reachability (skip cycle case to avoid noise) ---
  if (triggers.length === 1 && cyclic.length === 0) {
    const fromTrigger = reachableFrom(flow, [triggers[0].id]);
    const toResponse = canReach(
      flow,
      responses.map((r) => r.id),
    );
    const adj = buildAdjacency(flow);
    for (const n of flow.nodes) {
      if (n.type === 'note') continue;
      const isolated = (adj.in.get(n.id)?.length ?? 0) === 0 && (adj.out.get(n.id)?.length ?? 0) === 0;
      if (isolated && n.type !== 'trigger') {
        problems.push(
          problem({ severity: 'warning', nodeId: n.id, message: '孤立节点：未连接任何连线' }),
        );
        continue;
      }
      if (!fromTrigger.has(n.id) && n.type !== 'trigger') {
        problems.push(
          problem({ severity: 'warning', nodeId: n.id, message: '该节点无法从触发器到达' }),
        );
      }
      if (!toResponse.has(n.id)) {
        problems.push(
          problem({
            severity: 'warning',
            nodeId: n.id,
            message: '该节点无法到达任何响应节点',
          }),
        );
      }
    }
  }

  // --- per-node required config ---
  for (const n of flow.nodes) {
    problems.push(...checkRequired(n));
  }

  return problems;
}

/** True if the flow has no error-severity problems (compilable). */
export function hasErrors(problems: Problem[]): boolean {
  return problems.some((p) => p.severity === 'error');
}
