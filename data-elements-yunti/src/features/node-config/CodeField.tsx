import { CopyOutlined, ExpandOutlined } from "@ant-design/icons";
import { App, Button, Input, Modal, Tooltip } from "antd";
import { useState } from "react";
import "./CodeField.css";

interface CodeFieldProps {
  id?: string;
  value?: string | null;
  onChange?: (value: string) => void;
  label?: string;
  language?: string;
  readOnly?: boolean;
  compact?: boolean;
  height?: string;
}

/** A local editor remains usable without a CDN, including in isolated deployments. */
export function CodeField({
  id,
  value: rawValue = "",
  onChange,
  label = "代码",
  language = "text",
  readOnly = false,
  compact = false,
  height,
}: CodeFieldProps) {
  const [open, setOpen] = useState(false);
  const value =
    typeof rawValue === "string" ? rawValue : String(rawValue ?? "");
  const { message } = App.useApp();
  return (
    <>
      <div className="fs-code-field">
        <div className="fs-code-field__bar">
          <span>
            {language.toUpperCase()}
            <span className="fs-code-field__lines">
              {value.split("\n").length} 行
            </span>
          </span>
          <span>
            <Tooltip title="复制内容">
              <Button
                type="text"
                size="small"
                aria-label={`复制${label}`}
                icon={<CopyOutlined />}
                onClick={() => {
                  if (!navigator.clipboard) {
                    void message.error(
                      "当前浏览器不支持自动复制，请选中文本后复制",
                    );
                    return;
                  }
                  void navigator.clipboard.writeText(value).then(
                    () => message.success("已复制"),
                    () => message.error("复制未完成，请选中文本后复制"),
                  );
                }}
              />
            </Tooltip>
            <Tooltip title="展开编辑">
              <Button
                type="text"
                size="small"
                aria-label={`展开${label}`}
                icon={<ExpandOutlined />}
                onClick={() => setOpen(true)}
              />
            </Tooltip>
          </span>
        </div>
        <Input.TextArea
          id={id}
          aria-label={label}
          value={value}
          readOnly={readOnly}
          spellCheck={false}
          style={height ? { height, resize: "none" } : undefined}
          autoSize={
            height
              ? false
              : { minRows: compact ? 3 : 7, maxRows: compact ? 5 : 12 }
          }
          onChange={(e) => onChange?.(e.target.value)}
        />
      </div>
      <Modal
        className="fs-code-modal"
        title={label}
        open={open}
        width={920}
        footer={
          <Button type="primary" onClick={() => setOpen(false)}>
            完成
          </Button>
        }
        onCancel={() => setOpen(false)}
      >
        <p className="fs-code-modal__hint">
          {language.toUpperCase()} ·{" "}
          {readOnly ? "只读预览" : "修改即时应用到当前草稿，保存服务后持久化"}
        </p>
        <Input.TextArea
          aria-label={`${label}完整编辑器`}
          className="fs-code-modal__input"
          value={value}
          readOnly={readOnly}
          spellCheck={false}
          onChange={(e) => onChange?.(e.target.value)}
        />
      </Modal>
    </>
  );
}

export function JsonField({
  id,
  value,
  onChange,
  label,
  shape,
}: {
  id?: string;
  value?: unknown;
  onChange?: (value: unknown) => void;
  label: string;
  shape: "array" | "object";
}) {
  const [draft, setDraft] = useState<string | null>(null);
  const [error, setError] = useState("");
  const raw = JSON.stringify(value ?? (shape === "array" ? [] : {}), null, 2);
  return (
    <>
      <div className="fs-json-field">
        <pre id={id} className="fs-json-field__preview">
          {raw}
        </pre>
        <Button
          size="small"
          icon={<ExpandOutlined />}
          aria-label="编辑 JSON"
          onClick={() => {
            setDraft(raw);
            setError("");
          }}
        >
          编辑 JSON
        </Button>
      </div>
      <Modal
        className="fs-code-modal"
        title={`编辑${label}`}
        open={draft !== null}
        width={860}
        okText="应用配置"
        cancelText="取消"
        onCancel={() => setDraft(null)}
        onOk={() => {
          try {
            const parsed: unknown = JSON.parse(draft ?? "");
            if (
              shape === "array"
                ? !Array.isArray(parsed)
                : !parsed || Array.isArray(parsed) || typeof parsed !== "object"
            ) {
              setError(
                shape === "array"
                  ? "请填写 JSON 数组，例如 []。"
                  : "请填写 JSON 对象，例如 {}。",
              );
              return;
            }
            onChange?.(parsed);
            setDraft(null);
          } catch {
            setError("JSON 格式有误，请检查引号、逗号和括号后重新应用。");
          }
        }}
      >
        <p className="fs-code-modal__hint">
          修改后点击“应用配置”。取消会保留原有内容。
        </p>
        <Input.TextArea
          aria-label={`${label} JSON 编辑器`}
          className="fs-code-modal__input"
          value={draft ?? ""}
          spellCheck={false}
          status={error ? "error" : undefined}
          onChange={(e) => {
            setDraft(e.target.value);
            setError("");
          }}
        />
        {error && (
          <p role="alert" className="fs-field-error">
            {error}
          </p>
        )}
      </Modal>
    </>
  );
}
