import { defineNode, IN_OUT, type NodeDef } from '../node-def';

export const toolsNodeDefs: NodeDef[] = [
  defineNode({
    type: 'validate',
    category: 'tools',
    label: '参数校验',
    description: '集中校验参数（非空、范围、正则、枚举、自定义）',
    icon: 'CheckCircleOutlined',
    ports: IN_OUT,
    defaultData: {
      // each: { field: string; rule: 'required'|'range'|'regex'|'enum'; arg?: string; message?: string }
      rules: [],
      errorCode: 400,
      errorMessage: '参数校验失败',
    },
  }),
];
