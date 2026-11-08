import configuration from "./configuration.json" with { type: "json" };

/** 当前数据库的公开配置镜像；运行时只读本地文件。 */
export { configuration };

export function tenantConfiguration(scenario) {
  const result = configuration.tenants.find((item) => item.scenario === scenario);
  if (!result) throw new Error(`未知行业配置：${scenario}`);
  return result;
}

export function scenarioForTenantId(tenantId) {
  return configuration.tenants.find((item) => item.tenant.tid === tenantId)?.scenario;
}
