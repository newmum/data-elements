import { defineNode, IN_OUT, type NodeDef } from '../node-def';

export const processingNodeDefs: NodeDef[] = [
  defineNode({
    type: 'script',
    category: 'processing',
    label: '脚本',
    description: '编写脚本处理数据，输出结果可供后续节点引用',
    icon: 'CodeOutlined',
    ports: IN_OUT,
    defaultData: {
      code: '// 在此编写高级脚本\nreturn null;',
      outputVar: 'scriptResult',
    },
  }),
  defineNode({
    type: 'transform',
    category: 'processing',
    label: '字段映射 / 转换',
    description: '把源结构映射/重命名/类型转换为目标结构（报文转换）',
    icon: 'SwapOutlined',
    ports: IN_OUT,
    defaultData: {
      sourceVar: '',
      listMode: true, // map over array items
      // each: { target: string; expr: string }
      mappings: [],
      outputVar: 'transformed',
    },
  }),
  defineNode({
    type: 'filter',
    category: 'processing',
    label: '过滤',
    description: '按条件过滤列表',
    icon: 'FilterOutlined',
    ports: IN_OUT,
    defaultData: {
      sourceVar: '',
      predicate: 'item.value != null',
      outputVar: 'filtered',
    },
  }),
  defineNode({
    type: 'aggregate',
    category: 'processing',
    label: '聚合',
    description: '分组聚合（count/sum/avg/min/max/group by）',
    icon: 'FunctionOutlined',
    ports: IN_OUT,
    defaultData: {
      sourceVar: '',
      groupBy: [],
      // each: { field: string; op: 'count'|'sum'|'avg'|'min'|'max'; as: string }
      aggregations: [],
      outputVar: 'aggregated',
    },
  }),
  defineNode({
    type: 'sort',
    category: 'processing',
    label: '排序',
    description: '按字段升/降序排序列表',
    icon: 'SortAscendingOutlined',
    ports: IN_OUT,
    defaultData: {
      sourceVar: '',
      // each: { field: string; order: 'asc'|'desc' }
      sortBy: [],
      outputVar: 'sorted',
    },
  }),
  defineNode({
    type: 'paginate',
    category: 'processing',
    label: '分页',
    description: '对内存列表分页（建议优先在 SQL/ES 下推）',
    icon: 'ContainerOutlined',
    ports: IN_OUT,
    defaultData: {
      sourceVar: '',
      page: '${page}',
      size: '${size}',
      outputVar: 'paged',
    },
  }),
  defineNode({
    type: 'join',
    category: 'processing',
    label: '合并 / Join',
    description: '两个列表按 key 关联（inner/left），用于多源数据合并',
    icon: 'MergeCellsOutlined',
    ports: IN_OUT,
    defaultData: {
      leftVar: '',
      rightVar: '',
      leftKey: 'id',
      rightKey: 'id',
      type: 'left', // inner | left
      outputVar: 'joined',
    },
  }),
];
