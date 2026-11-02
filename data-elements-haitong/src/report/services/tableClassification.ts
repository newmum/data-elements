// Keep this pure classifier aligned with datasource registration; parity is checked in report tests.
export const inferBusinessType = (row: {tableName?:string;name?:string;tableComment?:string;tableNameCn?:string}) => {
  const tableName = String(row.tableName || row.name || "").toLowerCase();
  const tableComment = String(row.tableComment || row.tableNameCn || "").toLowerCase();
  const normalizedName = tableName.replace(/[^a-z0-9]+/g, "_");
  const text = `${tableName} ${tableComment}`;
  const hasToken = (...tokens:string[]) =>
    tokens.some((token) => new RegExp(`(^|_)${token}(_|$)`).test(normalizedName));
  const hasText = (...words:string[]) => words.some((word) => text.includes(word));
  if (
    hasToken("bak", "backup", "bk", "old", "copy", "his", "history", "archive", "arch", "dump") ||
    /(^|_)(19|20)\d{6,12}($|_)/.test(normalizedName) ||
    hasText("备份", "历史", "归档", "副本")
  ) {
    return { type: "备份表", reason: "表名或注释命中备份、历史、归档或版本特征" };
  }
  if (
    hasToken("log", "logs", "audit", "trace", "access", "operate", "operation", "login") ||
    hasText("日志", "审计", "轨迹", "访问记录", "操作记录", "登录记录")
  ) {
    return { type: "日志表", reason: "表名或注释命中日志、审计、访问或操作记录特征" };
  }
  if (
    hasToken(
      "dict",
      "dictionary",
      "code",
      "enum",
      "lookup",
      "type",
      "param",
      "params",
      "config",
      "constant"
    ) ||
    hasText("字典", "码表", "代码表", "枚举", "参数", "配置", "常量", "类型")
  ) {
    return { type: "字典表", reason: "表名或注释命中字典、编码、参数或配置特征" };
  }
  if (
    hasToken(
      "flow",
      "workflow",
      "process",
      "proc",
      "task",
      "approval",
      "approve",
      "node",
      "todo",
      "bpm"
    ) ||
    hasText("流程", "过程", "审批", "工单", "任务", "节点", "待办")
  ) {
    return { type: "过程表", reason: "表名或注释命中流程、任务、审批或节点特征" };
  }
  return {
    type: "业务表",
    reason:
      "表名和注释未呈现日志、字典、流程或备份特征，更符合承载核心业务对象与业务过程数据的业务表特征",
  };
};
