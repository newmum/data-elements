import { DeleteOutlined, PlusOutlined } from "@ant-design/icons";
import { Button, Input, Select, Switch, Tabs } from "antd";
import { JsonField } from "./CodeField";

import { parameterColumns, type Column } from "./recordSchema";

function records(value: unknown): Record<string, unknown>[] {
  return Array.isArray(value)
    ? value.filter(
        (item): item is Record<string, unknown> =>
          Boolean(item) && typeof item === "object" && !Array.isArray(item),
      )
    : [];
}
export function RecordsField({
  value,
  onChange,
  columns,
  label,
  defaults = {},
}: {
  value?: unknown;
  onChange?: (next: unknown) => void;
  columns: Column[];
  label: string;
  defaults?: Record<string, unknown>;
}) {
  const rows = records(value);
  // Preserve imported extension fields; opening a visual editor never rewrites the saved structure.
  if (Array.isArray(value) && rows.length !== value.length)
    return (
      <JsonField
        label={label}
        value={value}
        onChange={onChange}
        shape="array"
      />
    );
  const update = (index: number, key: string, next: unknown) =>
    onChange?.(
      rows.map((row, i) => (i === index ? { ...row, [key]: next } : row)),
    );
  return (
    <div className="fs-records">
      {!rows.length && (
        <div className="fs-records__empty">暂无{label}，按需添加</div>
      )}
      {rows.map((row, index) => (
        <div className="fs-records__row" key={index}>
          <div className="fs-records__head">
            <span>
              {label} {index + 1}
            </span>
            <Button
              type="text"
              size="small"
              danger
              icon={<DeleteOutlined />}
              aria-label={`删除${label}${index + 1}`}
              disabled={label === "分支规则" && row.port === "default"}
              onClick={() => onChange?.(rows.filter((_, i) => i !== index))}
            />
          </div>
          <div className="fs-records__fields">
            {columns.map((column) => (
              <label
                className={column.boolean ? "fs-records__boolean" : ""}
                key={column.key}
              >
                <span>{column.label}</span>
                {column.boolean ? (
                  <Switch
                    size="small"
                    checked={Boolean(row[column.key])}
                    onChange={(next) => update(index, column.key, next)}
                  />
                ) : column.options ? (
                  <Select
                    size="small"
                    aria-label={`${column.label}${index + 1}`}
                    value={
                      typeof row[column.key] === "string"
                        ? row[column.key]
                        : undefined
                    }
                    options={column.options}
                    onChange={(next) => update(index, column.key, next)}
                  />
                ) : (
                  <Input
                    size="small"
                    aria-label={`${column.label}${index + 1}`}
                    placeholder={column.label}
                    value={String(row[column.key] ?? "")}
                    disabled={
                      label === "分支规则" &&
                      row.port === "default" &&
                      column.key === "port"
                    }
                    onChange={(e) => update(index, column.key, e.target.value)}
                  />
                )}
              </label>
            ))}
          </div>
        </div>
      ))}
      <Button
        block
        size="small"
        type="dashed"
        icon={<PlusOutlined />}
        onClick={() =>
          onChange?.([
            ...rows,
            {
              ...Object.fromEntries(
                columns.map((c) => [
                  c.key,
                  c.boolean ? false : (c.options?.[0]?.value ?? ""),
                ]),
              ),
              ...defaults,
            },
          ])
        }
      >
        添加{label}
      </Button>
    </div>
  );
}
export function RequestField({
  value,
  onChange,
}: {
  value?: Record<string, unknown>;
  onChange?: (next: Record<string, unknown>) => void;
}) {
  const request = value ?? {};
  return (
    <Tabs
      size="small"
      className="fs-request-tabs"
      items={[
        ...[
          { key: "query", label: "查询" },
          { key: "path", label: "路径" },
          { key: "header", label: "请求头" },
        ].map((item) => {
          const entries = request[item.key];
          return {
            key: item.key,
            label: `${item.label} · ${Array.isArray(entries) ? entries.length : 0}`,
            children: (
              <RecordsField
                label={`${item.label}参数`}
                columns={parameterColumns}
                value={entries}
                onChange={(next) =>
                  onChange?.({ ...request, [item.key]: next })
                }
              />
            ),
          };
        }),
        {
          key: "body",
          label: "请求体",
          children: (
            <div className="fs-request-body">
              <span>JSON Schema</span>
              <JsonField
                shape="object"
                label="请求体结构"
                value={request.body}
                onChange={(next) => onChange?.({ ...request, body: next })}
              />
              {request.body !== null && request.body !== undefined && (
                <Button
                  size="small"
                  onClick={() => onChange?.({ ...request, body: null })}
                >
                  清除请求体定义
                </Button>
              )}
            </div>
          ),
        },
      ]}
    />
  );
}
