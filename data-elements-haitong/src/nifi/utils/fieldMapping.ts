import type { CanvasDsl } from '@/types/dsl';

export interface FieldMappingResult {
  from: string;
  to: string;
  fromLabel?: string;
  toLabel?: string;
  expression?: string;
  comment?: string;
}

function buildLabelMap(columns: unknown): Record<string, string> {
  const map: Record<string, string> = {};
  if (Array.isArray(columns)) {
    columns.forEach((col: any) => {
      if (col?.columnName && col?.columnComment) map[col.columnName] = col.columnComment;
    });
  }
  return map;
}

const ODS_UUID_EXPRESSION =
  "CAST(TIMESTAMPDIFF(MILLISECOND, TIMESTAMP '1970-01-01 00:00:00', LOCALTIMESTAMP) AS VARCHAR) || CAST(100000 + RAND_INTEGER(900000) AS VARCHAR)";

function buildOdsSystemMapping(toName: string, toLabel?: string): FieldMappingResult | null {
  const target = toName.toUpperCase();
  if (target === 'ODS_UUID') {
    return {
      from: '',
      to: '/ODS_UUID',
      toLabel,
      expression: ODS_UUID_EXPRESSION,
      comment: 'NiFi生成纯数字ODS主键',
    };
  }
  if (target === 'ODS_RKSJ') {
    return {
      from: '',
      to: '/ODS_RKSJ',
      toLabel,
      expression: 'LOCALTIMESTAMP',
      comment: 'NiFi写入当前入库时间',
    };
  }
  if (target === 'ODS_GXSJ') {
    return {
      from: '',
      to: '/ODS_GXSJ',
      toLabel,
      expression: 'LOCALTIMESTAMP',
      comment: 'NiFi写入当前更新时间',
    };
  }
  return null;
}

/**
 * 对 DSL 中所有字段映射/字段增强节点，基于字段中文名（columnComment）
 * 同名自动生成映射规则。
 * 返回每个 field-mapping 节点的映射列表（仅 label 匹配成功的）。
 */
export function autoMapByLabels(dsl: CanvasDsl): Record<string, FieldMappingResult[]> {
  const result: Record<string, FieldMappingResult[]> = {};
  const nodeById = Object.fromEntries(dsl.nodes.map((n) => [n.id, n]));

  // 筛选出所有字段映射节点
  const fmNodes = dsl.nodes.filter((n) => ['transform.field-mapping', 'transform.field-enrichment'].includes(n.manifestKey));

  fmNodes.forEach((fmNode) => {
    // 找到上游来源节点
    const incomingEdge = dsl.edges.find((e) => e.target === fmNode.id);
    const outgoingEdge = dsl.edges.find((e) => e.source === fmNode.id);

    const sourceNode = incomingEdge ? nodeById[incomingEdge.source] : null;
    const sinkNode = outgoingEdge ? nodeById[outgoingEdge.target] : null;

    const sourceLabels = buildLabelMap((sourceNode as any)?.config?.sourceColumns);
    const targetLabels = buildLabelMap((sinkNode as any)?.config?.targetColumns);

    // 按中文名匹配：源字段 label 与目标字段 label 完全相同
    const sourceByLabel = new Map<string, string>();
    Object.entries(sourceLabels).forEach(([name, label]) => sourceByLabel.set(label, name));

    const mappings: FieldMappingResult[] = [];
    Object.entries(targetLabels).forEach(([toName, toLabel]) => {
      const systemMapping = buildOdsSystemMapping(toName, toLabel);
      if (systemMapping) {
        mappings.push(systemMapping);
        return;
      }
      const fromName = sourceByLabel.get(toLabel);
      if (fromName) {
        mappings.push({
          from: `/${fromName}`,
          to: `/${toName}`,
          fromLabel: toLabel,
          toLabel,
        });
      }
    });

    if (mappings.length > 0) result[fmNode.id] = mappings;
  });

  return result;
}
