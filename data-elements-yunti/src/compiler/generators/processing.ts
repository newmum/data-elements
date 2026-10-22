import type { NodeGenerator } from './types';
import { str, bool, arr, outVar, nodeLabel } from './types';

/** script: inline user 服务脚本, scoped via an IIFE. */
export const scriptGenerator: NodeGenerator = (node, ctx) => {
  const v = outVar(node, 'scriptResult');
  ctx.declareVar(v);
  ctx.buf.comment(`脚本 ${nodeLabel(node)}`);
  ctx.buf.line(`var ${v} = (() => {`);
  ctx.buf.block(() => {
    ctx.buf.line(str(node, 'code', 'return null;'));
  });
  ctx.buf.line('})();');
  ctx.buf.blank();
};

interface Mapping {
  target: string;
  expr: string;
}

/** transform: field mapping / restructuring (SRS §6.3.2). */
export const transformGenerator: NodeGenerator = (node, ctx) => {
  const src = str(node, 'sourceVar');
  const listMode = bool(node, 'listMode', true);
  const mappings = arr<Mapping>(node, 'mappings');
  const v = outVar(node, 'transformed');
  ctx.declareVar(v);

  const fields = mappings.map((m) => `${m.target}: ${m.expr}`);
  ctx.buf.comment(`字段映射 ${nodeLabel(node)}`);
  if (listMode) {
    ctx.buf.line(`var ${v} = ${src}.map(item => {`);
    ctx.buf.block(() => {
      ctx.buf.line('return {');
      ctx.buf.block(() => fields.forEach((f, i) => ctx.buf.line(f + (i < fields.length - 1 ? ',' : ''))));
      ctx.buf.line('};');
    });
    ctx.buf.line('});');
  } else {
    ctx.buf.line(`var ${v} = {`);
    ctx.buf.block(() => fields.forEach((f, i) => ctx.buf.line(f + (i < fields.length - 1 ? ',' : ''))));
    ctx.buf.line('};');
  }
  ctx.buf.blank();
};

/** filter: list filtering by predicate (SRS §6.3.3). */
export const filterGenerator: NodeGenerator = (node, ctx) => {
  const src = str(node, 'sourceVar');
  const predicate = str(node, 'predicate', 'true');
  const v = outVar(node, 'filtered');
  ctx.declareVar(v);
  ctx.buf.comment(`过滤 ${nodeLabel(node)}`);
  ctx.buf.line(`var ${v} = ${src}.filter(item => ${predicate});`);
  ctx.buf.blank();
};

interface AggSpec {
  field: string;
  op: string;
  as: string;
}

/** aggregate: group-by aggregation. Emits a platform aggregate() helper call
 *  (SRS §6.3.4 advises pushing aggregation down to SQL/ES where possible). */
export const aggregateGenerator: NodeGenerator = (node, ctx) => {
  const src = str(node, 'sourceVar');
  const groupBy = arr<string>(node, 'groupBy');
  const aggs = arr<AggSpec>(node, 'aggregations');
  const v = outVar(node, 'aggregated');
  ctx.declareVar(v);
  const gb = `[${groupBy.map((g) => JSON.stringify(g)).join(', ')}]`;
  const aggList = aggs
    .map((a) => `{ field: ${JSON.stringify(a.field)}, op: ${JSON.stringify(a.op)}, as: ${JSON.stringify(a.as)} }`)
    .join(', ');
  ctx.buf.comment(`聚合 ${nodeLabel(node)}（建议尽量下推到 SQL/ES）`);
  ctx.buf.line(`var ${v} = aggregate(${src}, { groupBy: ${gb}, aggregations: [${aggList}] });`);
  ctx.buf.blank();
};

interface SortSpec {
  field: string;
  order: 'asc' | 'desc';
}

/** sort: multi-key sort (SRS §6.3.5). */
export const sortGenerator: NodeGenerator = (node, ctx) => {
  const src = str(node, 'sourceVar');
  const sortBy = arr<SortSpec>(node, 'sortBy');
  const v = outVar(node, 'sorted');
  ctx.declareVar(v);
  ctx.buf.comment(`排序 ${nodeLabel(node)}`);
  ctx.buf.line(`var ${v} = ${src}.sort((a, b) => {`);
  ctx.buf.block(() => {
    for (const s of sortBy) {
      const dir = s.order === 'desc' ? -1 : 1;
      ctx.buf.line(
        `if (a.${s.field} != b.${s.field}) return (a.${s.field} > b.${s.field} ? 1 : -1) * ${dir};`,
      );
    }
    ctx.buf.line('return 0;');
  });
  ctx.buf.line('});');
  ctx.buf.blank();
};

/** paginate: in-memory pagination (SRS §6.3.5). */
export const paginateGenerator: NodeGenerator = (node, ctx) => {
  const src = str(node, 'sourceVar');
  const page = str(node, 'page', '1');
  const size = str(node, 'size', '10');
  const v = outVar(node, 'paged');
  ctx.declareVar(v);
  ctx.buf.comment(`分页 ${nodeLabel(node)}（建议优先在 SQL/ES 下推）`);
  ctx.buf.line(`var __page = ${page};`);
  ctx.buf.line(`var __size = ${size};`);
  ctx.buf.line(
    `var ${v} = { total: ${src}.size(), list: ${src}.skip((__page - 1) * __size).limit(__size) };`,
  );
  ctx.buf.blank();
};

/** join: relate two lists by key (inner/left) (SRS §6.3.5). */
export const joinGenerator: NodeGenerator = (node, ctx) => {
  const left = str(node, 'leftVar');
  const right = str(node, 'rightVar');
  const leftKey = str(node, 'leftKey', 'id');
  const rightKey = str(node, 'rightKey', 'id');
  const type = str(node, 'type', 'left');
  const v = outVar(node, 'joined');
  ctx.declareVar(v);
  ctx.buf.comment(`合并 Join (${type}) ${nodeLabel(node)}`);
  ctx.buf.line(`var ${v} = (() => {`);
  ctx.buf.block(() => {
    ctx.buf.line('var __right = {};');
    ctx.buf.line(`for (var __r in ${right}) { __right[__r.${rightKey}] = __r; }`);
    ctx.buf.line('var __res = [];');
    ctx.buf.line(`for (var __l in ${left}) {`);
    ctx.buf.block(() => {
      ctx.buf.line(`var __m = __right[__l.${leftKey}];`);
      ctx.buf.line(`if (__m != null || '${type}' == 'left') {`);
      ctx.buf.block(() => {
        ctx.buf.line('var __row = {};');
        ctx.buf.line('for (var __k in __l) { __row[__k] = __l[__k]; }');
        ctx.buf.line('if (__m != null) { for (var __k in __m) { __row[__k] = __m[__k]; } }');
        ctx.buf.line('__res.add(__row);');
      });
      ctx.buf.line('}');
    });
    ctx.buf.line('}');
    ctx.buf.line('return __res;');
  });
  ctx.buf.line('})();');
  ctx.buf.blank();
};

