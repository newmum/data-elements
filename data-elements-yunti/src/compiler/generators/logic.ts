import type { NodeGenerator } from './types';
import { str, num, arr, bool, nodeLabel } from './types';

function literal(value: unknown): string {
  if (value == null) return 'null';
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  const s = String(value);
  if (/^-?\d+(\.\d+)?$/.test(s) || s === 'true' || s === 'false' || s === 'null') return s;
  return JSON.stringify(s);
}

/** condition: if / else over true/false ports (SRS §6.4.1). */
export const conditionGenerator: NodeGenerator = (node, ctx) => {
  const expr = str(node, 'expr', 'true');
  ctx.buf.comment(`条件分支 ${nodeLabel(node)}`);
  ctx.buf.line(`if (${expr}) {`);
  ctx.buf.block(() => ctx.emitBranch(node.id, 'true'));
  ctx.buf.line('} else {');
  ctx.buf.block(() => ctx.emitBranch(node.id, 'false'));
  ctx.buf.line('}');
  ctx.buf.blank();
};

interface SwitchCase {
  value: string;
  port: string;
}

/** switch: chained if/else if/else over case ports (SRS §6.4.2). */
export const switchGenerator: NodeGenerator = (node, ctx) => {
  const expr = str(node, 'expr');
  const cases = arr<SwitchCase>(node, 'cases');
  ctx.buf.comment(`多路分支 ${nodeLabel(node)}`);
  cases.forEach((c, i) => {
    const head = i === 0 ? 'if' : '} else if';
    ctx.buf.line(`${head} (${expr} == ${literal(c.value)}) {`);
    ctx.buf.block(() => ctx.emitBranch(node.id, c.port));
  });
  if (cases.length > 0) {
    ctx.buf.line('} else {');
    ctx.buf.block(() => ctx.emitBranch(node.id, 'default'));
    ctx.buf.line('}');
  } else {
    ctx.emitBranch(node.id, 'default');
  }
  ctx.buf.blank();
};

/** foreach: loop over an array, emitting the body subgraph (SRS §6.4.3). */
export const foreachGenerator: NodeGenerator = (node, ctx) => {
  const list = str(node, 'listVar');
  const item = str(node, 'itemVar', 'item');
  const collect = str(node, 'collectVar', 'collected');
  ctx.declareVar(collect);
  ctx.declareVar(item);
  ctx.buf.comment(`循环 ${nodeLabel(node)}`);
  ctx.buf.line(`var ${collect} = [];`);
  ctx.buf.line(`for (var ${item} in ${list}) {`);
  ctx.buf.block(() => {
    ctx.emitBranch(node.id, 'body');
  });
  ctx.buf.line('}');
  ctx.buf.blank();
};

/** parallel: run each out-successor branch concurrently then await (SRS §6.4.4). */
export const parallelGenerator: NodeGenerator = (node, ctx) => {
  const successors = ctx.portSuccessors(node.id, 'out');
  ctx.buf.comment(`并行 ${nodeLabel(node)}`);
  const handles: string[] = [];
  successors.forEach((succ, i) => {
    const handle = `__p${i}`;
    handles.push(handle);
    ctx.buf.line(`var ${handle} = async(() => {`);
    ctx.buf.block(() => {
      ctx.emitSequence(ctx.exclusiveSuccessorNodes(succ, successors));
    });
    ctx.buf.line('});');
  });
  if (handles.length) {
    ctx.buf.comment('汇合');
    ctx.buf.line(handles.map((h) => `await ${h};`).join(' '));
  }
  ctx.buf.blank();
};

/** try-catch: protected (out) + catch (error) branches (SRS §6.4.5). */
export const tryCatchGenerator: NodeGenerator = (node, ctx) => {
  const errorVar = str(node, 'errorVar', 'error');
  const swallow = bool(node, 'swallow');
  ctx.declareVar(errorVar);
  ctx.buf.comment(`异常捕获 ${nodeLabel(node)}`);
  ctx.buf.line('try {');
  ctx.buf.block(() => ctx.emitBranch(node.id, 'out'));
  ctx.buf.line(`} catch (${errorVar}) {`);
  ctx.buf.block(() => {
    ctx.emitBranch(node.id, 'error');
    if (swallow) ctx.buf.comment('（吞掉异常）');
  });
  ctx.buf.line('}');
  ctx.buf.blank();
};

interface InputMap {
  param: string;
  expr: string;
}

/** subflow: call another flow as a sub-process (SRS §6.4.6). */
export const subflowGenerator: NodeGenerator = (node, ctx) => {
  const flowId = str(node, 'flowId');
  const out = str(node, 'outputVar', 'subResult');
  ctx.declareVar(out);
  const inputs = arr<InputMap>(node, 'inputMap');
  const inputObj = `{ ${inputs.map((i) => `${i.param}: ${i.expr}`).join(', ')} }`;
  ctx.buf.comment(`子编排引用 ${nodeLabel(node)}`);
  ctx.buf.line(`var ${out} = subflow('${flowId}', ${inputObj});`);
  ctx.buf.blank();
};

/** delay: sleep for ms (SRS §6.4.7). */
export const delayGenerator: NodeGenerator = (node, ctx) => {
  ctx.buf.comment(`延迟 ${nodeLabel(node)}`);
  ctx.buf.line(`sleep(${num(node, 'ms', 1000)});`);
  ctx.buf.blank();
};

/** set-var: assign an expression to a context variable (SRS §6.4.7). */
export const setVarGenerator: NodeGenerator = (node, ctx) => {
  const name = str(node, 'name', 'myVar');
  const expr = str(node, 'expr', 'null');
  ctx.declareVar(name);
  ctx.buf.line(`var ${name} = ${expr || 'null'};`);
};

/** const: define a constant value (SRS §6.4.7). */
export const constGenerator: NodeGenerator = (node, ctx) => {
  const name = str(node, 'name', 'myConst');
  const raw = (node.data as Record<string, unknown>).value;
  ctx.declareVar(name);
  ctx.buf.line(`var ${name} = ${literal(raw)};`);
};

/** log: emit a runtime log line (SRS §6.4.7). */
export const logGenerator: NodeGenerator = (node, ctx) => {
  const level = str(node, 'level', 'info');
  const message = str(node, 'message');
  ctx.buf.line(`log.${level}(${JSON.stringify(message)});`);
};

interface ValidateRule {
  field: string;
  rule: string;
  arg?: string;
  message?: string;
}

/** validate: parameter guards that early-return an error envelope (SRS §6.5). */
export const validateGenerator: NodeGenerator = (node, ctx) => {
  const rules = arr<ValidateRule>(node, 'rules');
  const code = num(node, 'errorCode', 400);
  const defaultMsg = str(node, 'errorMessage', '参数校验失败');
  ctx.buf.comment(`参数校验 ${nodeLabel(node)}`);
  for (const r of rules) {
    const msg = JSON.stringify(r.message || `${r.field} ${defaultMsg}`);
    let cond: string;
    switch (r.rule) {
      case 'required':
        cond = `${r.field} == null || ${r.field} == ''`;
        break;
      case 'regex':
        cond = `${r.field} == null || !regex(${JSON.stringify(r.arg ?? '')}, ${r.field})`;
        break;
      case 'range': {
        const [min, max] = (r.arg ?? '').split(',');
        cond = `${r.field} == null || ${r.field} < ${min || 0} || ${r.field} > ${max || 0}`;
        break;
      }
      case 'enum': {
        const values = (r.arg ?? '')
          .split(',')
          .map((s) => JSON.stringify(s.trim()))
          .join(', ');
        cond = `![${values}].contains(${r.field})`;
        break;
      }
      default:
        cond = 'false';
    }
    ctx.buf.line(`if (${cond}) {`);
    ctx.buf.block(() => ctx.buf.line(`return response.json(${code}, { code: ${code}, message: ${msg} });`));
    ctx.buf.line('}');
  }
  ctx.buf.blank();
};
