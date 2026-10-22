import { defineNode, OUT_PORT, IN_PORT, type NodeDef } from '../node-def';
import { TriggerDataSchema } from '../flow-dsl';

export const ioNodeDefs: NodeDef[] = [
  defineNode({
    type: 'trigger',
    category: 'io',
    label: 'API 触发器',
    description: '定义对外 HTTP 接口的方法、路径与入参契约（每编排唯一）',
    icon: 'ApiOutlined',
    singleton: true,
    ports: [OUT_PORT],
    defaultData: TriggerDataSchema.parse({}) as unknown as Record<string, unknown>,
  }),
  defineNode({
    type: 'response',
    category: 'io',
    label: '响应',
    description: '定义最终返回结构与状态，终止编排',
    icon: 'SendOutlined',
    terminal: true,
    ports: [IN_PORT],
    defaultData: {
      mode: 'wrap', // wrap | var | template | script
      source: '', // variable name when mode=var
      template: '', // JSON template when mode=template
      code: '', // script when mode=script
      wrap: true, // {code,message,data} envelope
      statusCode: 200,
      headers: [],
    },
  }),
];
