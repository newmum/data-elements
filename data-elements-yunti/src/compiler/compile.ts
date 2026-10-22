import type { Flow } from '@/domain/flow-dsl';
import { validateFlow, hasErrors, type Problem } from '@/domain/validators';
import { CompileContext } from './context';
import { triggerCodeGenerator } from './generators';

export interface CompileResult {
  ok: boolean;
  script: string;
  /** All problems (errors block compilation; warnings are advisory). */
  problems: Problem[];
}

/**
 * Compile a Flow DSL into 服务脚本.
 * Returns `ok: false` with problems (and no script) when there are errors.
 */
export function compile(flow: Flow): CompileResult {
  const problems = validateFlow(flow);
  if (hasErrors(problems)) {
    return { ok: false, script: '', problems };
  }

  const ctx = new CompileContext(flow);

  // Header
  ctx.buf.comment(
    `=== FlowServe 自动生成，请勿手改；flowId: ${flow.flowId}, version: ${flow.version} ===`,
  );
  ctx.buf.comment(`编排: ${flow.name}`);
  ctx.buf.blank();

  // Trigger: emit parameter docs (trigger is skipped by the normal walk).
  const trigger = flow.nodes.find((n) => n.type === 'trigger');
  if (trigger) {
    triggerCodeGenerator(trigger, ctx);
    ctx.emitted.add(trigger.id);
  }

  // Walk every node in topological order; control-flow nodes recurse into
  // their branches and mark consumed nodes as emitted.
  ctx.emitSequence(ctx.order);

  return { ok: true, script: ctx.toString().replace(/\n{3,}/g, '\n\n').trimEnd() + '\n', problems };
}

