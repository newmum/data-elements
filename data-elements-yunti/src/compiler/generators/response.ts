import type { NodeGenerator } from './types';
import { str, bool, num, nodeLabel } from './types';

/** Emit the final `return` for a response node (supports several modes). */
export const responseGenerator: NodeGenerator = (node, ctx) => {
  const mode = str(node, 'mode', 'wrap');
  const source = str(node, 'source');
  const template = str(node, 'template');
  const code = str(node, 'code');
  const wrap = bool(node, 'wrap', true);
  const status = num(node, 'statusCode', 200);

  ctx.buf.comment(`响应 ${nodeLabel(node)}`);

  let value: string;
  switch (mode) {
    case 'var':
      value = source || 'null';
      break;
    case 'template':
      value = template.trim() || 'null';
      break;
    case 'script':
      // The script is expected to produce the return value.
      ctx.buf.line(code.trim() || 'return null;');
      return;
    case 'wrap':
    default:
      value = wrap
        ? `{ code: 0, message: 'success', data: ${source || 'null'} }`
        : source || 'null';
      break;
  }

  if (status !== 200) {
    ctx.buf.line(`return response.json(${status}, ${value});`);
  } else {
    ctx.buf.line(`return ${value};`);
  }
};
