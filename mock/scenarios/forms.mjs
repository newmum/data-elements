import { tenantConfiguration } from "./configuration.mjs";

/** 本地场景表单定义；沿用真实 /sym/form 返回格式和现有 JsonForm 渲染器。 */
export function detailForms(database) {
  const field = (title, name, type = "input", extra = {}) => ({
    type,
    field: name,
    title,
    col: { span: 12 },
    model: "1",
    ...extra,
  });
  const title = {
    type: "u-title",
    props: {
      name: "基本信息",
      actions: [
        {
          key: "edit",
          label: "编辑",
          icon: "EditPen",
          visibleWhen: "view",
          requiresEditable: true,
        },
        { key: "cancel", label: "取消", visibleWhen: "editing" },
        { key: "save", label: "保存", visibleWhen: "editing", type: "primary" },
      ],
    },
    col: { span: 24 },
  };
  const rules = [
    title,
    field("数据源名称", "dbName", "input", {
      validate: [
        { required: true, message: "请输入数据源名称", trigger: "blur" },
      ],
    }),
    field("数据库类型", "dbType", "select", {
      options: ["mysql", "postgresql", "oracle", "kingbase8", "dameng"].map(
        (value) => ({ label: value, value }),
      ),
    }),
    field("业务系统", "appName", "input", { props: { disabled: true } }),
    field("所属部门", "orgName", "input", { props: { disabled: true } }),
    field("数据库名", "database"),
    field("来源地址", "host"),
    field("端口", "port"),
    field("所属网域", "storageDomain"),
    field("数据源说明", "assetDesc", "input", {
      props: { type: "textarea", rows: 3 },
      col: { span: 24 },
    }),
  ];
  return [
    {
      tid: `${database.code}-form-db-detail`,
      formName: "库详情",
      formJson: JSON.stringify(rules),
      formConfig: null,
      env: tenantConfiguration(database.code).settings.systemCode,
      isDel: 0,
      createdTime: "2026-10-01 09:00:00",
      updatedTime: "2026-10-01 09:00:00",
    },
  ];
}
