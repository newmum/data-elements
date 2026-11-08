import { tenantConfiguration } from "./configuration.mjs";

/** 公开行业配置沿用当前数据库，业务记录仍使用可重复的虚构种子。 */
const businessScenarios = {
  police: {
    name: "公安",
    systems: [
      "警情管理",
      "预警研判",
      "案件管理",
      "勤务指挥",
      "基础信息",
      "综合统计",
    ],
    tables: ["警情记录", "预警事件", "业务档案", "处置记录", "代码字典"],
  },
  broadcasting: {
    name: "广电",
    systems: [
      "节目编排",
      "媒资管理",
      "播出监测",
      "终端管理",
      "收视分析",
      "运维保障",
    ],
    tables: ["节目档案", "媒资目录", "播出记录", "终端状态", "频道字典"],
  },
  "sea-fishery": {
    name: "海渔",
    systems: [
      "渔船管理",
      "港口管理",
      "航次管理",
      "养殖管理",
      "环境监测",
      "生产统计",
    ],
    tables: ["渔船档案", "港口信息", "航次记录", "监测数据", "品种字典"],
  },
  chengtian: {
    name: "澄天",
    systems: [
      "园区管理",
      "企业服务",
      "项目管理",
      "设备管理",
      "能源管理",
      "经营分析",
    ],
    tables: ["企业档案", "项目记录", "设备信息", "能耗记录", "行业字典"],
  },
};

export const scenarios = Object.fromEntries(
  Object.entries(businessScenarios).map(([code, scene]) => {
    const { settings } = tenantConfiguration(code);
    return [code, { ...scene, title: settings.systemName }];
  }),
);

export function createScenario(code = "police", size = 120) {
  const scene = scenarios[code];
  if (!scene) throw new Error(`未知行业场景：${code}`);
  if (!Number.isInteger(size) || size < 1 || size > 1000)
    throw new Error("场景规模必须为 1–1000");
  const tenantId = tenantConfiguration(code).tenant.tid;
  const organizations = (tenantConfiguration(code).organizations || [])
    .filter((row) => row.id !== "ROOT")
    .map((row) => ({
      ...structuredClone(row),
      tid: row.id,
      orgName: row.name,
      label: row.name,
      value: row.id,
    }));
  const parentIds = new Set(organizations.map((row) => row.parentId));
  const assignedOrganizations = organizations
    .filter((row) => !parentIds.has(row.id))
    .slice(0, 3);
  const applications = scene.systems.map((name, i) => ({
    tid: `${code}-app-${i}`,
    appId: `${code}-app-${i}`,
    appName: `${name}系统`,
    orgId: assignedOrganizations[i % 3]?.tid || "",
    orgName: assignedOrganizations[i % 3]?.name || "",
    tenantId,
  }));
  const sources = Array.from({ length: size }, (_, i) => {
    const app = applications[i % applications.length];
    const dbType = ["mysql", "postgresql", "oracle", "kingbase8", "dameng"][
      i % 5
    ];
    const tid = `${code}-source-${String(i + 1).padStart(3, "0")}`;
    const createdBy = `${code}-user-${(Math.floor(i / 3) + i) % 3}`;
    return {
      tid,
      id: tid,
      tenantId,
      assetType: "db",
      dbName: `${scene.systems[i % 6]}${["业务库", "交换库", "历史库", "主题库"][Math.floor(i / 6) % 4]} ${String(i + 1).padStart(3, "0")}`,
      dbType,
      dbTypeName: {
        mysql: "MySQL",
        postgresql: "PostgreSQL",
        oracle: "Oracle",
        kingbase8: "Kingbase",
        dameng: "Dameng",
      }[dbType],
      database: `demo_${code.replaceAll("-", "_")}_${i + 1}`,
      host: `192.0.2.${(i % 200) + 1}`,
      port: dbType === "mysql" ? "3306" : "5432",
      showConnect: 1,
      accessMode: "jdbc",
      orgId: app.orgId,
      orgName: app.orgName,
      appId: app.tid,
      appName: app.appName,
      sourceOrgId: app.orgId,
      sourceOrgName: app.orgName,
      createdBy,
      createdByName: ["数据管理员", "部门登记员", "资源维护员"][
        (Math.floor(i / 3) + i) % 3
      ],
      createdTime: "2026-10-01 09:00:00",
      updatedTime: `2026-11-${String((i % 7) + 1).padStart(2, "0")} 10:00:00`,
      assetDesc: `${scene.name}行业${scene.systems[i % 6]}场景的虚构调试数据，可编辑并保存在当前浏览器。`,
      storageDomain: ["互联网", "政务网", "业务专网"][i % 3],
      assetStatus: [0, 1, 2][i % 3],
      displayStatus: [0, 1, 2][i % 3],
      flowStatus: 2,
      connectionStatus: i % 11 === 0 ? "failed" : "success",
      tableNum: 6,
      exploredTableNum: 6,
      collectedTableCount: 6,
      fieldCount: 30,
      registeredTableNum: i % 3 === 2 ? 6 : 3,
      annotatedTableNum: i % 3 !== 2 && i % 4 === 0 ? 3 : 5,
      unannotatedTableNum: i % 3 !== 2 && i % 4 === 0 ? 3 : 1,
      unregisteredTableNum: i % 3 === 2 ? 0 : 3,
      businessTableNum: 4,
      dictionaryTableNum: 1,
      logTableNum: 1,
      coreTableNum: 4,
      registeredCoreTableNum: 3,
      deferredTableNum: 0,
      changedTableNum: i % 4 === 0 ? 1 : 0,
      addedTableNum: i % 4 === 0 ? 1 : 0,
      modifiedTableNum: 0,
      deletedTableNum: 0,
      versionNo: 1,
      isDel: 0,
    };
  });
  const tables = sources.flatMap((source) =>
    [...scene.tables, "有效业务记录视图"].map((name, i) => ({
      tid: `${source.tid}-table-${i}`,
      id: `${source.tid}-table-${i}`,
      datasourceId: source.tid,
      dbId: source.tid,
      tenantId,
      tableName:
        i === 5
          ? "view_active_records"
          : `demo_${["record", "event", "archive", "log", "code"][i]}`,
      objectType: i === 5 ? "VIEW" : "TABLE",
      viewOf: i === 5 ? `${source.tid}-table-0` : null,
      baseTableComment: i === 5 ? scene.tables[0] : null,
      tableComment: name,
      tableRemarks: name,
      columnNum: 5,
      fieldCount: 5,
      recordCount: i === 5 ? 80 : 120 + i * 37,
      annotated: i < source.annotatedTableNum ? 1 : 0,
      assetStatus: i < source.registeredTableNum ? 2 : 0,
      tableType: i === 5 ? "VIEW" : "TABLE",
      businessType: i === 4 ? "字典表" : i === 3 ? "日志表" : "业务表",
      dbName: source.dbName,
      orgId: source.orgId,
      createdBy: source.createdBy,
      updatedTime: source.updatedTime,
    })),
  );
  return {
    version: 3,
    code,
    tenantId,
    scene,
    organizations,
    applications,
    sources,
    tables,
  };
}
