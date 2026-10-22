import type { NodeGenerator } from './types';
import type { TriggerData } from '@/domain/flow-dsl';

/**
 * The trigger doesn't emit executable code — 运行服务 injects request
 * parameters as same-named variables. We emit doc comments and register the
 * parameter names as available context variables.
 */
export const triggerGenerator: NodeGenerator = (node, ctx) => {
  const data = node.data as unknown as TriggerData;
  const params = [
    ...(data.request?.query ?? []).map((p) => ({ ...p, in: 'query' })),
    ...(data.request?.path ?? []).map((p) => ({ ...p, in: 'path' })),
    ...(data.request?.header ?? []).map((p) => ({ ...p, in: 'header' })),
  ];
  ctx.buf.comment('请求参数（运行服务 自动注入同名变量）');
  if (params.length === 0) {
    ctx.buf.comment('（无）');
  }
  for (const p of params) {
    ctx.declareVar(p.name);
    ctx.buf.comment(
      `${p.name}: ${p.desc || ''} (${p.type}${p.required ? ', required' : ''}, in=${p.in})`,
    );
  }
  if (data.request?.body) {
    ctx.declareVar('body');
    ctx.buf.comment('body: 请求体 (object)');
  }
  ctx.buf.blank();
};

