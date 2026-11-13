/** 仅虚构行业审计事实；不改菜单/机构，不采集真实请求，不写真实 Magic。 */
export const logKinds = ["auth", "operations", "requests", "security"];
export const captureStatuses = ["EMPTY", "CAPTURED", "PARTIAL", "OMITTED", "FAILED", "LEGACY"];
export const logResults = ["SUCCESS", "FAILED", "DENIED", "PARTIAL", "ACCEPTED", "CANCELLED", "TIMEOUT", "UNKNOWN"];
const day = 86400000;

export function createLogScenario(database, timestamp = Date.now(), perKind = 1440) {
  const { code, tenantId, applications, scene } = database;
  // 同秒多条记录用于稳定排序回归；96 天包含默认窗口、历史及边界样本。
  const anchor = Math.floor(timestamp / 1000) * 1000 - 5000;
  return logKinds.flatMap((kind, kindIndex) => Array.from({ length: perKind }, (_, i) => {
    const actor = i % 3;
    const org = applications[Math.floor(i / 3) % applications.length];
    const status = captureStatuses[(Math.floor(i / 3) + Math.floor(i / 18)) % captureStatuses.length];
    const result = logResults[Math.floor(i / 9) % logResults.length];
    const dryRun = Math.floor(i / 6) % 2 === 0;
    const executed = !dryRun && ["SUCCESS", "PARTIAL"].includes(result);
    const tid = `${code}-${kind}-${String(i + 1).padStart(5, "0")}`;
    const targetId = `${code}-validation-task-${i % 100}`;
    const targetName = `${scene.name}虚构任务 ${i % 100} 字面%_`;
    const items = Array.from({ length: 2 }, (_, item) => ({
      item_no: item + 1, target_type: "TASK", target_id: `${targetId}-${item}`,
      target_name: `${targetName}-${item}`, action_code: dryRun ? "TASK_DELETE_PRECHECK" : "TASK_DELETE",
      result: result === "PARTIAL" ? item ? "FAILED" : "SUCCESS" : result,
      reason_code: result === "FAILED" || result === "PARTIAL" && item ? "VALIDATION_BUSINESS_FAILED" : null,
    }));
    const deleted = executed ? items.filter((item) => item.result === "SUCCESS") : [];
    const eventTime = new Date(anchor - Math.floor(i / 15) * day - Math.floor(i % 15 / 3) * 1000).toISOString();
    const parameterKind = ["operations", "requests"].includes(kind);
    const captured = parameterKind && ["CAPTURED", "PARTIAL"].includes(status);
    const parameters = parameterKind && (captured || status === "LEGACY") ? {
      taskId: items.map((item) => item.target_id), taskName: items.map((item) => item.target_name), dryRun: String(dryRun),
      ...(executed ? { deletedTaskId: deleted.map((item) => item.target_id), deletedTaskName: deleted.map((item) => item.target_name) } : {}),
    } : {};
    // 已执行标记只来自虚构执行结果，绝不从请求的 dryRun 或待执行 ID 推断。
    const bodies = [
      { "中文嵌套": { "说明": "单完整字面 A B %_ !", "混合数组": [false, 0, null, { "名称": targetName }] }, password: "[REDACTED]", accessToken: "[REDACTED]" },
      ["中文数组", false, 0, null, { "深层": { "标签": "Literal%_Case" } }],
      false, 0, null, "独立中文正文 Literal%_Case",
    ];
    const parameterSnapshot = {
      version: 1, status: parameterKind ? status : "OMITTED",
      path: captured ? { taskId: targetId } : {},
      query: captured ? { dryRun, "检索": "Literal%_Case" } : {},
      body: captured ? bodies[Math.floor(i / 18) % bodies.length] : null,
      verified: captured && executed ? { deletedTaskId: deleted.map((item) => item.target_id), deletedTaskName: deleted.map((item) => item.target_name) } : {},
      notices: status === "PARTIAL" ? ["部分敏感字段已脱敏；超限内容未保存"]
        : status === "FAILED" ? ["采集失败，不代表业务执行失败"]
          : status === "OMITTED" || !parameterKind ? ["按采集策略省略"]
            : status === "LEGACY" ? ["历史未采集结构化参数；仅保留安全白名单"] : [],
    };
    return {
      tid, tenant_id: tenantId, kind,
      event_time: status === "LEGACY" ? null : eventTime, created_time: eventTime, received_time: eventTime,
      category: { auth: "AUTH", operations: i % 5 ? "OPERATION" : "TASK", requests: "REQUEST", security: "SECURITY" }[kind],
      actor_kind: "TENANT_USER", actor_id: `${code}-user-${actor}`,
      actor_account: actor === 2 ? "=SUM(1,2)" : `demo-${code}-${actor}`,
      actor_name: ["虚构数据管理员", "虚构部门登记员", "虚构资源维护员"][actor],
      actor_org_id: org?.orgId || "", actor_org_name: org?.orgName || "",
      // 历史路径是事件快照，不据此注册或补齐任何可见菜单。
      menu_id: null, menu_path: "历史虚构业务 / 任务管理", page_route: "/validation/tasks",
      operation_name: kind === "auth" ? ["登录", "密码验证", "退出"][i % 3] : kind === "security" ? "访问范围校验" : dryRun ? "删除任务预检" : "删除任务执行",
      function_code: `validation-${kind}`, action_code: kind === "auth" ? ["LOGIN", "PASSWORD_VERIFY", "LOGOUT"][i % 3] : kind === "security" ? "ACCESS_CHECK" : "TASK_DELETE",
      target_type: "TASK", target_id: targetId, target_name: targetName, result,
      reason_code: result === "FAILED" ? "VALIDATION_BUSINESS_FAILED" : result === "DENIED" ? "ACCESS_DENIED" : null,
      ip: `192.0.2.${actor + 1}`, user_agent: status === "LEGACY" ? null : "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/130.0.0.0",
      duration_ms: kind === "requests" && status !== "LEGACY" ? i % 500 : null,
      trace_id: `${code}-trace-${kindIndex}-${i}`, request_id: `${code}-request-${kindIndex}-${i}`,
      session_ref: null, http_status: kind === "requests" ? 200 : null,
      business_code: result === "FAILED" ? "VALIDATION_FAILED" : "0",
      total_count: items.length, success_count: items.filter((item) => item.result === "SUCCESS").length,
      failure_count: items.filter((item) => item.result === "FAILED").length,
      source_app: "chengtian", batch_id: tid, task_id: targetId,
      route_path: `/validation/${kind}`, legacy: status === "LEGACY",
      parameter_capture_status: parameterSnapshot.status,
      parameter_snapshot: parameterSnapshot, request_parameters: parameters,
      details: { phase: dryRun ? "PRECHECK" : "EXECUTION", dryRun, executed, simulated: true },
      items,
    };
  }));
}
