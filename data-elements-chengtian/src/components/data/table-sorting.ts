/** List sorting is part of the read request. Never sort a paginated page locally. */
export type TableSort = { sortField?: string; sortDir?: 'asc' | 'desc' };
export const isRequiredSortColumn = (column: { label?: string; prop?: string; type?: string }, first: boolean) => {
  const label = String(column.label ?? '');
  if (!column.prop || /操作|序号/.test(label) || ['selection', 'index', 'seq', 'checkbox', 'buttons'].includes(column.type ?? '')) return false;
  return (first && /名称|姓名|账号|数据表|数据源|字段|目录|任务|流程|服务/.test(label))
    || column.type === 'date' || /时间|日期|最近登录|最近更新/.test(label)
    || column.type === 'num' || /数量|总数|次数|条数|字段数|字典（项）|规则数|质量得分|一致率|检查数|异常数|记录数|用户数|权限数|表数|数据表（|字节数|文件数|线程数|大小|容量|占比|率|耗时|延迟|延时|阈值|长度/.test(label);
};
