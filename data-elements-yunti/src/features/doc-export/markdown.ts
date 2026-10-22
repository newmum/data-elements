import type { Flow, RequestParam, TriggerData } from '@/domain/flow-dsl';

function triggerOf(flow: Flow): TriggerData {
  return flow.nodes.find((node) => node.type === 'trigger')?.data as TriggerData;
}

function table(params: RequestParam[]) {
  if (params.length === 0) return '无\n';
  return [
    '| 名称 | 类型 | 必填 | 说明 | 示例 |',
    '|---|---|---:|---|---|',
    ...params.map((p) => `| ${p.name} | ${p.type} | ${p.required ? '是' : '否'} | ${p.desc || '-'} | ${String(p.example ?? '-')} |`),
  ].join('\n');
}

export function generateMarkdown(flow: Flow): string {
  const trigger = triggerOf(flow);
  return `# ${flow.name}

${flow.description || ''}

## 基本信息

- Method: \`${trigger.method}\`
- Path: \`${trigger.path}\`
- Group: ${trigger.group || flow.group}

## Query 参数

${table(trigger.request.query)}

## Path 参数

${table(trigger.request.path)}

## Header 参数

${table(trigger.request.header)}

## cURL 示例

\`\`\`bash
curl -X ${trigger.method} '${trigger.path}'
\`\`\`

## 响应示例

\`\`\`json
{
  "code": 0,
  "message": "success",
  "data": {}
}
\`\`\`
`;
}
