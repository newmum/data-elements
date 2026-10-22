import {
  CloseOutlined,
  InfoCircleOutlined,
  SettingOutlined,
} from "@ant-design/icons";
import {
  Alert,
  AutoComplete,
  Button,
  Form,
  Input,
  InputNumber,
  Select,
  Segmented,
  Switch,
  Tabs,
  Tooltip,
} from "antd";
import { useEffect, useMemo, useState } from "react";
import { requireNodeDef } from "@/domain/node-registry";
import {
  usePlatformDataSources,
  type PlatformDataSource,
} from "@/services/platformDataSources";
import { useEditorStore } from "@/store/editorStore";
import { useUiStore } from "@/store/uiStore";
import { resolveIcon } from "../canvas/iconMap";
import { CodeField, JsonField } from "./CodeField";
import { recordColumns } from "./recordSchema";
import { RecordsField, RequestField } from "./RecordsField";
import {
  buildConditionExpr,
  fieldGroups,
  fieldMeta,
  variableOptions,
  initialLogicMode,
  type LogicMode,
} from "./presentation";
import "./ConfigPanel.css";

interface FormValues {
  name?: string;
  onError?: "fail" | "ignore" | "branch" | "retry";
  data: Record<string, unknown>;
}
const nonJdbcTypes = new Set([
  "api",
  "elasticsearch",
  "es",
  "ftp",
  "http",
  "kafka",
  "minio",
  "redis",
  "sftp",
]);
function supportsNode(source: PlatformDataSource, type: string) {
  if (type.startsWith("kafka-")) return source.type === "kafka";
  if (type === "es-query") return ["es", "elasticsearch"].includes(source.type);
  if (type === "http-call") return ["api", "http"].includes(source.type);
  if (type === "redis") return source.type === "redis";
  return !nonJdbcTypes.has(source.type);
}
const operators = [
  { value: "eq", label: "等于" },
  { value: "ne", label: "不等于" },
  { value: "gt", label: "大于" },
  { value: "gte", label: "大于等于" },
  { value: "lt", label: "小于" },
  { value: "lte", label: "小于等于" },
  { value: "contains", label: "包含" },
  { value: "empty", label: "为空" },
  { value: "notEmpty", label: "不为空" },
];
const options: Record<string, { value: string; label: string }[]> = {
  method: ["GET", "POST", "PUT", "DELETE", "PATCH"].map((value) => ({
    value,
    label: value,
  })),
  offsetReset: [
    { value: "latest", label: "最新消息" },
    { value: "earliest", label: "最早消息" },
  ],
  parse: [
    { value: "json", label: "JSON 对象" },
    { value: "text", label: "纯文本" },
  ],
  valueFormat: [
    { value: "json", label: "JSON 对象" },
    { value: "string", label: "字符串" },
  ],
  type: [
    { value: "left", label: "左关联" },
    { value: "inner", label: "内关联" },
  ],
  level: ["debug", "info", "warn", "error"].map((value) => ({
    value,
    label: value.toUpperCase(),
  })),
  op: [
    { value: "get", label: "读取值" },
    { value: "set", label: "写入值" },
    { value: "del", label: "删除键" },
    { value: "hget", label: "读取哈希字段" },
    { value: "hset", label: "写入哈希字段" },
    { value: "incr", label: "递增" },
    { value: "expire", label: "设置有效期" },
  ],
  joinStrategy: [
    { value: "all", label: "等待所有分支" },
    { value: "any", label: "任一分支完成" },
  ],
  operator: operators,
};
function fieldLabel(key: string, type: string) {
  const base = fieldMeta[key] ?? { label: key };
  const meta =
    type === "response" && key === "headers"
      ? { ...base, label: "响应头" }
      : type === "response" && key === "mode"
        ? { ...base, label: "返回方式" }
        : base;
  return (
    <span className="fs-config__label">
      {key === "value" && type === "condition" ? "比较值" : meta.label}
      {meta.help && (
        <Tooltip title={meta.help}>
          <InfoCircleOutlined tabIndex={0} aria-label={`${meta.label}说明`} />
        </Tooltip>
      )}
    </span>
  );
}
function NodeFields({
  nodeType,
  keys,
  logicMode,
  onLogicModeChange,
}: {
  nodeType: string;
  keys: string[];
  logicMode: LogicMode;
  onLogicModeChange: (mode: LogicMode) => void;
}) {
  const form = Form.useFormInstance<FormValues>();
  const data = Form.useWatch("data", form) as
    | Record<string, unknown>
    | undefined;
  const flow = useEditorStore((s) => s.flow);
  const selectedId = useEditorStore((s) => s.selectedNodeIds[0]);
  const {
    data: sources = [],
    isLoading,
    isError,
    refetch,
  } = usePlatformDataSources(keys.includes("datasource"));
  const def = requireNodeDef(nodeType);
  const variables = useMemo(
    () => variableOptions(flow, selectedId),
    [flow, selectedId],
  );
  return (
    <>
      {["condition", "switch"].includes(nodeType) && keys.includes("field") && (
        <div className="fs-config__logic-mode">
          <Segmented
            block
            size="small"
            value={logicMode}
            options={[
              { value: "builder", label: "条件构建" },
              { value: "expression", label: "表达式编辑" },
            ]}
            onChange={onLogicModeChange}
          />
          <p>
            {logicMode === "builder"
              ? "配置字段和判断方式，自动生成表达式。"
              : "直接编辑已有表达式，保留复杂判断逻辑。"}
          </p>
        </div>
      )}
      {keys.map((key) => {
        if (key === "auth") return null;
        if (
          ["condition", "switch"].includes(nodeType) &&
          logicMode === "expression" &&
          ["field", "operator", "value"].includes(key)
        )
          return null;
        if (
          nodeType === "response" &&
          ((key === "template" && data?.mode !== "template") ||
            (key === "code" && data?.mode !== "script") ||
            (key === "source" &&
              !["wrap", "var"].includes(String(data?.mode ?? "wrap"))))
        )
          return null;
        if (
          nodeType === "es-query" &&
          ((key === "dsl" && data?.mode === "builder") ||
            (key === "builder" && data?.mode !== "builder"))
        )
          return null;
        if (
          nodeType === "condition" &&
          key === "value" &&
          ["empty", "notEmpty"].includes(String(data?.operator))
        )
          return null;
        const sample = def.defaultData[key];
        const path = ["data", key];
        const label = fieldLabel(key, nodeType);
        const meta = fieldMeta[key];
        const block = Boolean(
          meta?.language ||
          recordColumns[key] ||
          key === "request" ||
          (typeof sample === "object" &&
            !["groupBy", "dynamicWhitelist"].includes(key)),
        );
        const itemProps = {
          key,
          label,
          name: path,
          className: block ? "fs-config__block-field" : undefined,
        };
        if (key === "request")
          return (
            <Form.Item {...itemProps}>
              <RequestField />
            </Form.Item>
          );
        if (recordColumns[key])
          return (
            <Form.Item {...itemProps}>
              <RecordsField
                columns={recordColumns[key]}
                label={meta?.label ?? key}
                defaults={
                  key === "cases"
                    ? {
                        port: `case_${Array.isArray(data?.cases) ? data.cases.length : 0}`,
                      }
                    : undefined
                }
              />
            </Form.Item>
          );
        if (["groupBy", "dynamicWhitelist"].includes(key))
          return (
            <Form.Item {...itemProps}>
              <Select
                mode="tags"
                tokenSeparators={[","]}
                placeholder="输入名称后按回车"
                open={false}
              />
            </Form.Item>
          );
        if (typeof sample === "boolean")
          return (
            <Form.Item {...itemProps} valuePropName="checked">
              <Switch size="small" />
            </Form.Item>
          );
        if (typeof sample === "number")
          return (
            <Form.Item {...itemProps}>
              <InputNumber style={{ width: "100%" }} />
            </Form.Item>
          );
        if (key === "datasource")
          return (
            <div key={key}>
              <Form.Item {...itemProps}>
                <Select
                  showSearch
                  allowClear
                  optionFilterProp="label"
                  loading={isLoading}
                  placeholder={
                    isLoading ? "正在读取数据源" : "选择已登记的数据源"
                  }
                  options={sources
                    .filter((s) => supportsNode(s, nodeType))
                    .map((s) => ({
                      value: s.id,
                      label: `${s.name} · ${s.type.toUpperCase()}`,
                      disabled: ["failed", "unprovided"].includes(
                        s.connectionStatus,
                      ),
                    }))}
                  notFoundContent="暂无匹配的数据源"
                />
              </Form.Item>
              {isError && (
                <div className="fs-field-error" role="alert">
                  数据源读取失败{" "}
                  <Button
                    size="small"
                    type="link"
                    onClick={() => void refetch()}
                  >
                    重新加载
                  </Button>
                </div>
              )}
            </div>
          );
        if (key === "mode")
          return (
            <Form.Item {...itemProps}>
              <Select
                options={
                  nodeType === "response"
                    ? [
                        { value: "wrap", label: "标准响应" },
                        { value: "var", label: "返回变量" },
                        { value: "template", label: "模板生成" },
                        { value: "script", label: "脚本生成" },
                      ]
                    : [
                        { value: "dsl", label: "DSL 查询" },
                        { value: "builder", label: "条件构建" },
                      ]
                }
              />
            </Form.Item>
          );
        if (options[key])
          return (
            <Form.Item {...itemProps}>
              <Select options={options[key]} />
            </Form.Item>
          );
        if (Array.isArray(sample) || (sample && typeof sample === "object"))
          return (
            <Form.Item {...itemProps}>
              <JsonField
                label={
                  nodeType === "response" && key === "headers"
                    ? "响应头"
                    : (meta?.label ?? key)
                }
                shape={Array.isArray(sample) ? "array" : "object"}
              />
            </Form.Item>
          );
        if (meta?.language)
          return (
            <Form.Item {...itemProps}>
              <CodeField
                label={meta.label}
                language={meta.language}
                compact={key === "expr" || key === "predicate"}
                readOnly={
                  key === "expr" &&
                  ["condition", "switch"].includes(nodeType) &&
                  logicMode === "builder" &&
                  Boolean(data?.field)
                }
              />
            </Form.Item>
          );
        if (
          [
            "sourceVar",
            "source",
            "listVar",
            "leftVar",
            "rightVar",
            "field",
          ].includes(key)
        )
          return (
            <Form.Item {...itemProps}>
              <AutoComplete
                options={variables}
                placeholder="输入变量或字段表达式"
                filterOption={(input, option) =>
                  String(option?.value)
                    .toLowerCase()
                    .includes(input.toLowerCase())
                }
              />
            </Form.Item>
          );
        if (key === "text" || key === "message")
          return (
            <Form.Item {...itemProps}>
              <Input.TextArea autoSize={{ minRows: 2, maxRows: 5 }} />
            </Form.Item>
          );
        return (
          <Form.Item {...itemProps}>
            <Input spellCheck={false} />
          </Form.Item>
        );
      })}
    </>
  );
}
export function ConfigPanel() {
  const selectedId = useEditorStore((s) => s.selectedNodeIds[0]);
  return <SelectedConfigPanel key={selectedId} selectedId={selectedId} />;
}
function SelectedConfigPanel({ selectedId }: { selectedId: string }) {
  const [form] = Form.useForm<FormValues>();
  const node = useEditorStore((s) =>
    s.flow?.nodes.find((n) => n.id === selectedId),
  );
  const [logicMode, setLogicMode] = useState<LogicMode>(() =>
    node ? initialLogicMode(node.type, node.data) : "builder",
  );
  const allProblems = useEditorStore((s) => s.problems);
  const problems = allProblems.filter((p) => p.nodeId === selectedId);
  useEffect(() => {
    if (node)
      form.setFieldsValue({
        name: node.name,
        onError: node.errorHandling?.onError ?? "fail",
        data: node.data,
      });
  }, [form, node]);
  if (!node) return null;
  const def = requireNodeDef(node.type);
  const Icon = resolveIcon(def.icon);
  const groups = fieldGroups(Object.keys(def.defaultData));
  return (
    <aside className="fs-config" aria-label="组件配置">
      <header className="fs-config__head">
        <span
          className="fs-config__icon"
          style={{ color: def.color, background: `${def.color}12` }}
        >
          <Icon />
        </span>
        <div>
          <strong>{def.label}</strong>
          <span>组件配置</span>
        </div>
        <Tooltip title="收起配置">
          <Button
            type="text"
            size="small"
            icon={<CloseOutlined />}
            aria-label="收起组件配置"
            onClick={() => useUiStore.getState().setRightCollapsed(true)}
          />
        </Tooltip>
      </header>
      <div className="fs-config__description">{def.description}</div>
      {problems.length > 0 && (
        <Alert
          className="fs-config__alert"
          type={
            problems.some((p) => p.severity === "error") ? "error" : "warning"
          }
          showIcon
          title={problems[0].message}
        />
      )}
      <Form
        form={form}
        className="fs-config__form"
        layout="horizontal"
        labelCol={{ flex: "92px" }}
        wrapperCol={{ flex: "auto" }}
        labelAlign="left"
        colon={false}
        size="small"
        onValuesChange={(changed: Partial<FormValues>, values: FormValues) => {
          let next = values.data ?? {};
          const businessChanged =
            changed.data &&
            ["field", "operator", "value"].some(
              (key) => key in (changed.data ?? {}),
            );
          if (
            node.type === "condition" &&
            next.field &&
            logicMode === "builder" &&
            businessChanged
          )
            next = {
              ...next,
              expr: buildConditionExpr(next.field, next.operator, next.value),
            };
          if (
            node.type === "switch" &&
            next.field &&
            logicMode === "builder" &&
            businessChanged
          )
            next = { ...next, expr: next.field };
          useEditorStore.getState().updateNode(node.id, {
            name: values.name,
            errorHandling: {
              ...node.errorHandling,
              onError: values.onError ?? "fail",
            },
            data: { ...node.data, ...next },
          });
        }}
      >
        <Tabs
          size="small"
          defaultActiveKey="config"
          items={[
            {
              key: "config",
              label: "常用配置",
              forceRender: true,
              children: (
                <>
                  <div className="fs-config__section">
                    <Form.Item label="节点名称" name="name">
                      <Input placeholder="为当前节点命名" />
                    </Form.Item>
                  </div>
                  {groups
                    .filter((g) => g.key !== "advanced")
                    .map((group) => (
                      <section className="fs-config__section" key={group.key}>
                        {(group.key !== "business" ||
                          group.keys.length > 1) && <h3>{group.label}</h3>}
                        <NodeFields
                          nodeType={node.type}
                          keys={group.keys}
                          logicMode={logicMode}
                          onLogicModeChange={setLogicMode}
                        />
                      </section>
                    ))}
                </>
              ),
            },
            {
              key: "advanced",
              label: (
                <span>
                  <SettingOutlined /> 运行与高级
                </span>
              ),
              forceRender: true,
              children: (
                <section className="fs-config__section">
                  <h3>异常处理</h3>
                  <Form.Item label="错误策略" name="onError">
                    <Select
                      options={[
                        { value: "fail", label: "中断流程" },
                        { value: "ignore", label: "忽略并继续" },
                        { value: "branch", label: "进入异常分支" },
                        { value: "retry", label: "重试执行" },
                      ]}
                    />
                  </Form.Item>
                  <p className="fs-config__note">
                    配置会应用到当前草稿，保存服务后持久化。
                  </p>
                  {groups
                    .filter((g) => g.key === "advanced")
                    .map((group) => (
                      <div key={group.key}>
                        <h3>{group.label}</h3>
                        <NodeFields
                          nodeType={node.type}
                          keys={group.keys}
                          logicMode={logicMode}
                          onLogicModeChange={setLogicMode}
                        />
                      </div>
                    ))}
                </section>
              ),
            },
          ]}
        />
      </Form>
      <footer className="fs-config__footer">
        <span className="fs-config__draft-dot" />
        修改即时应用到当前草稿
      </footer>
    </aside>
  );
}
