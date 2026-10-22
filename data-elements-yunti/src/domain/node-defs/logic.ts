import { defineNode, IN_PORT, OUT_PORT, IN_OUT, type NodeDef, type PortDef } from '../node-def';

const truePort: PortDef = { id: 'true', group: 'out', label: 'true' };
const falsePort: PortDef = { id: 'false', group: 'out', label: 'false' };
const defaultPort: PortDef = { id: 'default', group: 'out', label: 'default' };
const bodyPort: PortDef = { id: 'body', group: 'out', label: '循环体' };
const errorPort: PortDef = { id: 'error', group: 'out', label: '异常' };

export const logicNodeDefs: NodeDef[] = [
  defineNode({
    type: 'condition',
    category: 'logic',
    label: '条件分支',
    description: 'if / else：按条件表达式分流到 true / false 分支',
    icon: 'BranchesOutlined',
    ports: [IN_PORT, truePort, falsePort],
    defaultData: { field: '', operator: 'eq', value: '', expr: '' },
  }),
  defineNode({
    type: 'switch',
    category: 'logic',
    label: '多路分支',
    description: 'switch：表达式匹配多个 case 值，含 default',
    icon: 'NodeIndexOutlined',
    ports: [IN_PORT, defaultPort],
    defaultData: {
      field: '',
      expr: '',
      // each: { value: string; port: string }
      cases: [{ value: '', port: 'default', label: '默认分支' }],
    },
  }),
  defineNode({
    type: 'foreach',
    category: 'logic',
    label: '循环',
    description: 'foreach：对数组逐项执行循环体并收集结果',
    icon: 'RetweetOutlined',
    ports: [IN_PORT, bodyPort, OUT_PORT],
    defaultData: {
      listVar: '',
      itemVar: 'item',
      indexVar: 'index',
      collectVar: 'collected',
    },
  }),
  defineNode({
    type: 'parallel',
    category: 'logic',
    label: '并行',
    description: '多个分支并发执行后汇合（多源同时取数提速）',
    icon: 'PartitionOutlined',
    ports: IN_OUT,
    defaultData: {
      joinStrategy: 'all', // all | any
      timeout: 10000,
    },
  }),
  defineNode({
    type: 'try-catch',
    category: 'logic',
    label: '异常捕获',
    description: 'try / catch：保护子图，异常走 error 分支',
    icon: 'WarningOutlined',
    ports: [IN_PORT, OUT_PORT, errorPort],
    defaultData: {
      errorVar: 'error',
      swallow: false,
    },
  }),
  defineNode({
    type: 'subflow',
    category: 'logic',
    label: '子编排引用',
    description: '引用另一个编排作为子流程（复用、流程组合）',
    icon: 'DeploymentUnitOutlined',
    ports: IN_OUT,
    defaultData: {
      flowId: '',
      // each: { param: string; expr: string }
      inputMap: [],
      outputVar: 'subResult',
    },
  }),
  defineNode({
    type: 'delay',
    category: 'logic',
    label: '延迟',
    description: '等待指定毫秒数',
    icon: 'ClockCircleOutlined',
    ports: IN_OUT,
    defaultData: { ms: 1000 },
  }),
  defineNode({
    type: 'set-var',
    category: 'logic',
    label: '变量赋值',
    description: '将表达式结果赋值给上下文变量',
    icon: 'TagOutlined',
    ports: IN_OUT,
    defaultData: { name: 'myVar', expr: '' },
  }),
  defineNode({
    type: 'const',
    category: 'logic',
    label: '常量',
    description: '定义一个常量值到上下文',
    icon: 'NumberOutlined',
    ports: IN_OUT,
    defaultData: { name: 'myConst', value: '' },
  }),
  defineNode({
    type: 'log',
    category: 'logic',
    label: '日志',
    description: '输出一条运行日志',
    icon: 'ProfileOutlined',
    ports: IN_OUT,
    defaultData: { level: 'info', message: '' },
  }),
  defineNode({
    type: 'note',
    category: 'logic',
    label: '注释',
    description: '画布注解，不参与编译',
    icon: 'MessageOutlined',
    nonExecutable: true,
    ports: [],
    defaultData: { text: '注释内容…' },
  }),
];
