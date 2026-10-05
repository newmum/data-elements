// 数据源登记第二步与登记目录抽屉共用的分类规则。
export const tableBusinessTypeOptions = [
  { label: "业务表", value: "业务表" },
  { label: "日志表", value: "日志表" },
  { label: "字典表", value: "字典表" },
  { label: "过程表", value: "过程表" },
  { label: "备份表", value: "备份表" },
  { label: "暂不处理", value: "不确定" },
];
export const inferBusinessType = (row) => {
  const tableName = String(row.tableName || row.name || "").toLowerCase();
  const tableComment = String(row.tableComment || row.tableNameCn || "").toLowerCase();
  const normalizedName = tableName.replace(/[^a-z0-9]+/g, "_");
  const text = `${tableName} ${tableComment}`;
  const hasToken = (...tokens) =>
    tokens.some((token) => new RegExp(`(^|_)${token}(_|$)`).test(normalizedName));
  const hasText = (...words) => words.some((word) => text.includes(word));
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
export const resolveBusinessType = (row) => {
  const validTypes = tableBusinessTypeOptions.map((item) => item.value);
  const inferred = inferBusinessType(row);
  const backendType = validTypes.includes(row.businessType) ? row.businessType : "";
  if (backendType && String(row.businessTypeReason || "").includes("人工调整")) {
    return {
      type: backendType,
      reason: row.businessTypeReason,
    };
  }
  if (inferred.type !== "业务表") return inferred;
  if (backendType) {
    return {
      type: backendType,
      reason: row.businessTypeReason || inferred.reason,
    };
  }
  return inferred;
};

export const isTableAnnotated = (row) => {
  const value = row?.annotated;
  return value === true || value === 1 || value === "1" || String(value).trim().toLowerCase() === "true";
};

// 已标注和明确“暂不处理”的人工决定优先；未标注快照复用第二步的自动推断。
export const resolveCollectedBusinessType = (row, savedRow = row) => {
  const decision = resolveBusinessType({
    ...row,
    businessType: savedRow?.businessType ?? row?.businessType,
    businessTypeReason: savedRow?.businessTypeReason ?? row?.businessTypeReason,
  });
  const keepSaved = isTableAnnotated(savedRow) ||
    ["不确定", "暂不处理"].includes(savedRow?.businessType);
  return {
    type: (keepSaved ? savedRow?.businessType : decision.type) || decision.type,
    reason: (keepSaved ? savedRow?.businessTypeReason : decision.reason) || decision.reason,
  };
};
