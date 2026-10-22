import { memo, type CSSProperties } from "react";
import { Badge, Tooltip } from "antd";
import type { Node } from "@antv/x6";
import { requireNodeDef } from "@/domain/node-registry";
import { useEditorStore, type NodeRunStatus } from "@/store/editorStore";
import { useUiStore } from "@/store/uiStore";
import { resolveIcon } from "../iconMap";
import "./BaseNode.css";

interface BaseNodeData {
  flowNodeId: string;
  type: string;
}

const statusLabel: Record<NodeRunStatus, string> = {
  idle: "未运行",
  running: "运行中",
  success: "成功",
  fail: "失败",
};

function statusClass(status: NodeRunStatus): string {
  return status === "idle" ? "" : `is-${status}`;
}

export const BaseNode = memo(function BaseNode({ node }: { node: Node }) {
  const data = node.getData<BaseNodeData>();
  const def = requireNodeDef(data.type);
  const flowNode = useEditorStore((s) =>
    s.flow?.nodes.find((n) => n.id === data.flowNodeId),
  );
  const selected = useEditorStore((s) =>
    s.selectedNodeIds.includes(data.flowNodeId),
  );
  const status = useEditorStore((s) => s.nodeStatus[data.flowNodeId] ?? "idle");
  const allProblems = useEditorStore((s) => s.problems);
  const problems = allProblems.filter((p) => p.nodeId === data.flowNodeId);
  const Icon = resolveIcon(def.icon);
  const hasError = problems.some((p) => p.severity === "error");
  const hasWarning = problems.some((p) => p.severity === "warning");

  return (
    <div
      className={[
        "fs-node",
        selected ? "is-selected" : "",
        hasError ? "has-error" : "",
        hasWarning ? "has-warning" : "",
        statusClass(status),
      ].join(" ")}
      role="button"
      tabIndex={0}
      aria-label={`${flowNode?.name || def.label}，${def.label}${hasError ? "，配置有误" : ""}`}
      aria-pressed={selected}
      onKeyDown={(event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          event.stopPropagation();
          useEditorStore.getState().setSelection([data.flowNodeId], []);
          useUiStore.getState().setRightCollapsed(false);
        }
      }}
      style={{ "--fs-node-accent": def.color } as CSSProperties}
    >
      <div
        className="fs-node__icon"
        style={{ color: def.color, background: `${def.color}18` }}
      >
        <Icon />
      </div>
      <div className="fs-node__main">
        <div className="fs-node__title" title={flowNode?.name || def.label}>
          {flowNode?.name || def.label}
        </div>
        <div className="fs-node__desc">
          {def.label}
          {typeof flowNode?.data.outputVar === "string" && (
            <span> → {flowNode.data.outputVar}</span>
          )}
        </div>
      </div>
      <Tooltip
        title={
          problems.length
            ? problems.map((p) => p.message).join("；")
            : statusLabel[status]
        }
      >
        <Badge
          aria-label={
            hasError
              ? "配置有误"
              : hasWarning
                ? "配置提示"
                : statusLabel[status]
          }
          status={
            status === "running"
              ? "processing"
              : status === "success"
                ? "success"
                : hasError || status === "fail"
                  ? "error"
                  : hasWarning
                    ? "warning"
                    : "default"
          }
        />
      </Tooltip>
    </div>
  );
});
