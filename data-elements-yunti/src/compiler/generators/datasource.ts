import type { NodeGenerator } from './types';
import { str, num, bool, arr, outVar, nodeLabel } from './types';

function objLiteral(pairs: [string, string][]): string {
  if (pairs.length === 0) return '{}';
  return `{ ${pairs.map(([key, value]) => `${key}: ${value}`).join(', ')} }`;
}

export const kafkaConsumeGenerator: NodeGenerator = (node, ctx) => {
  const datasource = str(node, 'datasource');
  const output = outVar(node, 'messages');
  ctx.declareVar(output);
  const options: [string, string][] = [
    ['topic', JSON.stringify(str(node, 'topic'))],
    ['maxRecords', String(num(node, 'maxRecords', 100))],
    ['pollTimeoutMs', String(num(node, 'pollTimeoutMs', 2000))],
    ['offsetReset', JSON.stringify(str(node, 'offsetReset', 'latest'))],
    ['valueFormat', JSON.stringify(str(node, 'valueFormat', 'json'))],
    ['commit', String(bool(node, 'commit'))],
  ];
  const group = str(node, 'consumerGroup');
  if (group) options.push(['consumerGroup', JSON.stringify(group)]);
  ctx.buf.comment(`Kafka 消费（拉取式） ${nodeLabel(node)}`);
  ctx.buf.line(`var ${output} = kafka.consume('${datasource}', ${objLiteral(options)});`);
  ctx.buf.blank();
};

export const kafkaProduceGenerator: NodeGenerator = (node, ctx) => {
  const datasource = str(node, 'datasource');
  const output = outVar(node, 'sendResult');
  ctx.declareVar(output);
  const topic = JSON.stringify(str(node, 'topic'));
  const key = str(node, 'keyExpr') || 'null';
  const value = str(node, 'value') || 'null';
  const options = objLiteral([['waitAck', String(bool(node, 'waitAck', true))]]);
  ctx.buf.comment(`Kafka 生产 ${nodeLabel(node)}`);
  ctx.buf.line(`var ${output} = kafka.send('${datasource}', ${topic}, ${key}, ${value}, ${options});`);
  ctx.buf.blank();
};

interface EsCondition {
  field: string;
  op: string;
  value: string;
}

function buildEsDsl(node: Parameters<NodeGenerator>[0]): string {
  const size = num(node, 'size', 50);
  const from = num(node, 'from', 0);
  if (str(node, 'mode', 'dsl') === 'builder') {
    const builder = (node.data as Record<string, unknown>).builder as
      | { conditions?: EsCondition[] }
      | undefined;
    const conditions = builder?.conditions ?? [];
    const must = conditions.map((condition) => {
      switch (condition.op) {
        case 'eq':
          return `{ term: { ${condition.field}: ${condition.value} } }`;
        case 'match':
          return `{ match: { ${condition.field}: ${JSON.stringify(condition.value)} } }`;
        case 'gt':
        case 'gte':
        case 'lt':
        case 'lte':
          return `{ range: { ${condition.field}: { ${condition.op}: ${condition.value} } } }`;
        default:
          return `{ match: { ${condition.field}: ${JSON.stringify(condition.value)} } }`;
      }
    });
    return `{ query: { bool: { must: [${must.join(', ')}] } }, size: ${size}, from: ${from} }`;
  }
  return str(node, 'dsl', '{ "query": { "match_all": {} } }').trim();
}

export const esQueryGenerator: NodeGenerator = (node, ctx) => {
  const datasource = str(node, 'datasource');
  const index = str(node, 'index');
  const output = outVar(node, 'esResult');
  ctx.declareVar(output);
  ctx.buf.comment(`ES 查询 ${nodeLabel(node)}`);
  ctx.buf.line(`var ${output} = flowEs.search('${datasource}', '${index}', ${buildEsDsl(node)});`);
  ctx.buf.blank();
};

export const httpCallGenerator: NodeGenerator = (node, ctx) => {
  const output = outVar(node, 'httpResult');
  ctx.declareVar(output);
  const url = str(node, 'url');
  const method = str(node, 'method', 'GET').toLowerCase();
  const headers = arr<{ name?: string; key?: string; value: string }>(node, 'headers');
  const body = str(node, 'body');
  const extract = str(node, 'extract');
  const datasource = str(node, 'datasource');

  ctx.buf.comment(`HTTP 调用 ${nodeLabel(node)}`);
  if (datasource) {
    const headerObject = objLiteral(
      headers
        .map((header) => [
          header.name ?? header.key ?? '',
          JSON.stringify(header.value),
        ] as [string, string])
        .filter(([name]) => !!name),
    );
    const options: [string, string][] = [
      ['datasource', JSON.stringify(datasource)],
      ['path', JSON.stringify(url)],
      ['method', JSON.stringify(method.toUpperCase())],
      ['headers', headerObject],
    ];
    if (body) options.push(['body', body]);
    ctx.buf.line(`var ${output} = httpx.request(${objLiteral(options)});`);
    ctx.buf.blank();
    return;
  }

  ctx.buf.line(`var ${output} = http.connect(${JSON.stringify(url)})`);
  ctx.buf.block(() => {
    for (const header of headers) {
      const name = header.name ?? header.key ?? '';
      if (name) {
        ctx.buf.line(`.header(${JSON.stringify(name)}, ${JSON.stringify(header.value)})`);
      }
    }
    if (body && (method === 'post' || method === 'put' || method === 'patch')) {
      ctx.buf.line(`.data(${body})`);
    }
    ctx.buf.line(`.${method}()`);
    ctx.buf.line('.getBody();');
  });
  if (extract) ctx.buf.comment(`提取：${extract}`);
  ctx.buf.blank();
};

export const redisGenerator: NodeGenerator = (node, ctx) => {
  const output = outVar(node, 'redisResult');
  ctx.declareVar(output);
  const datasource = str(node, 'datasource');
  const operation = str(node, 'op', 'get');
  const key = JSON.stringify(str(node, 'key'));
  const value = str(node, 'value');
  const ttl = num(node, 'ttl', 0);
  const options = objLiteral([
    ['ttl', String(ttl)],
    ['field', JSON.stringify(str(node, 'field'))],
  ]);
  ctx.buf.comment(`Redis ${operation} ${nodeLabel(node)}`);
  ctx.buf.line(
    `var ${output} = flowRedis.op(${JSON.stringify(datasource)}, ${JSON.stringify(operation)}, ${key}, ${value || 'null'}, ${options});`,
  );
  ctx.buf.blank();
};
