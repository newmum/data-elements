import type { NodeGenerator } from './types';
import { str, bool, outVar, nodeLabel } from './types';

/** Emit a SQL string literal — multi-line uses triple-quoted 服务脚本 strings. */
export function sqlLiteral(sql: string): string {
  const trimmed = sql.trim();
  if (trimmed.includes('\n')) {
    return `"""\n${trimmed}\n"""`;
  }
  return JSON.stringify(trimmed);
}

function dbHandle(datasource: string): string {
  return datasource ? `db.datasource(flowDataSource.ensureRuntime('${datasource}'))` : 'db';
}

/** sql-query: SELECT → select / page / selectOne (SRS §6.2.1). */
export const sqlQueryGenerator: NodeGenerator = (node, ctx) => {
  const datasource = str(node, 'datasource');
  const sql = str(node, 'sql');
  const paging = bool(node, 'paging');
  const single = bool(node, 'single');
  const v = outVar(node, 'result');
  ctx.declareVar(v);

  const method = single ? 'selectOne' : paging ? 'page' : 'select';
  ctx.buf.comment(`SQL 查询 ${nodeLabel(node)}`);
  ctx.buf.line(`var ${v} = ${dbHandle(datasource)}`);
  ctx.buf.block(() => {
    ctx.buf.line(`.${method}(${sqlLiteral(sql)});`);
  });
  ctx.buf.blank();
};

/** sql-exec: INSERT/UPDATE/DELETE → update / insert (SRS §6.2.2). */
export const sqlExecGenerator: NodeGenerator = (node, ctx) => {
  const datasource = str(node, 'datasource');
  const sql = str(node, 'sql');
  const returnKey = bool(node, 'returnGeneratedKey');
  const v = outVar(node, 'affected');
  ctx.declareVar(v);

  const method = returnKey ? 'insert' : 'update';
  ctx.buf.comment(`SQL 执行 ${nodeLabel(node)}`);
  ctx.buf.line(`var ${v} = ${dbHandle(datasource)}.${method}(${sqlLiteral(sql)});`);
  ctx.buf.blank();
};

