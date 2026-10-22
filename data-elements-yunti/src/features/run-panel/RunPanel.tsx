import {
  DownOutlined,
  FieldTimeOutlined,
  NodeIndexOutlined,
  WarningOutlined,
} from "@ant-design/icons";
import { Alert, Button, Empty, Progress, Tag, Tabs, Typography } from "antd";
import type { PointerEvent as ReactPointerEvent } from "react";
import { useEditorStore } from "@/store/editorStore";
import { useUiStore } from "@/store/uiStore";
import "./RunPanel.css";

function JsonBlock({ value }: { value: unknown }) {
  return <pre className="fs-json">{JSON.stringify(value, null, 2)}</pre>;
}

function statusColor(status: string) {
  if (status === "success") return "success";
  if (status === "fail") return "error";
  return "default";
}

export function RunPanel({ errorMessage = "" }: { errorMessage?: string }) {
  const problems = useEditorStore((s) => s.problems);
  const trace = useEditorStore((s) => s.runTrace);
  const running = useEditorStore((s) => s.running);
  const bottomHeight = useUiStore((s) => s.bottomHeight);
  const activeKey = useUiStore((s) => s.bottomActiveKey);
  const totalMs = trace?.reduce((sum, entry) => sum + entry.ms, 0) ?? 0;
  const successCount =
    trace?.filter((entry) => entry.status === "success").length ?? 0;
  const failCount =
    trace?.filter((entry) => entry.status === "fail").length ?? 0;

  const startResize = (event: ReactPointerEvent) => {
    event.preventDefault();
    const startY = event.clientY;
    const startHeight = bottomHeight;
    (event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId);
    document.body.classList.add("fs-editor--resizing");
    const onMove = (moveEvent: PointerEvent) => {
      useUiStore
        .getState()
        .setBottomHeight(startHeight + startY - moveEvent.clientY);
    };
    const onUp = () => {
      document.body.classList.remove("fs-editor--resizing");
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("pointerup", onUp);
      window.removeEventListener("pointercancel", onUp);
    };
    window.addEventListener("pointermove", onMove);
    window.addEventListener("pointerup", onUp);
    window.addEventListener("pointercancel", onUp);
  };

  return (
    <section className="fs-run-panel" style={{ height: bottomHeight }}>
      <div
        className="fs-run-panel__resize"
        role="separator"
        aria-label="调整运行面板高度"
        aria-orientation="horizontal"
        tabIndex={0}
        onKeyDown={(event) => {
          if (event.key === "ArrowUp" || event.key === "ArrowDown") {
            event.preventDefault();
            useUiStore
              .getState()
              .setBottomHeight(
                bottomHeight + (event.key === "ArrowUp" ? 24 : -24),
              );
          }
        }}
        onPointerDown={startResize}
      />
      <Button
        className="fs-run-panel__collapse"
        icon={<DownOutlined />}
        aria-label="收起运行面板"
        onClick={() => useUiStore.getState().setBottomOpen(false)}
      />
      {errorMessage && (
        <Alert
          type="error"
          showIcon
          title={errorMessage}
          style={{ margin: "4px 40px 10px 0" }}
        />
      )}
      <Tabs
        size="small"
        activeKey={activeKey}
        onChange={(key) =>
          useUiStore
            .getState()
            .setBottomActiveKey(key as "result" | "timeline" | "problems")
        }
        items={[
          {
            key: "result",
            label: "运行结果",
            children:
              trace && trace.length > 0 ? (
                <div className="fs-run-panel__content">
                  <div className="fs-run-summary">
                    <div>
                      <Typography.Text type="secondary">节点</Typography.Text>
                      <strong>{trace.length}</strong>
                    </div>
                    <div>
                      <Typography.Text type="secondary">成功</Typography.Text>
                      <strong className="ok">{successCount}</strong>
                    </div>
                    <div>
                      <Typography.Text type="secondary">失败</Typography.Text>
                      <strong className="bad">{failCount}</strong>
                    </div>
                    <div>
                      <Typography.Text type="secondary">
                        累计耗时
                      </Typography.Text>
                      <strong>{totalMs}ms</strong>
                    </div>
                  </div>
                  <div className="fs-run-panel__trace">
                    {trace.map((entry, index) => (
                      <article
                        key={`${entry.nodeId}-${index}`}
                        className="fs-run-card"
                      >
                        <div className="fs-run-card__head">
                          <span className="fs-run-card__index">
                            {index + 1}
                          </span>
                          <div>
                            <Typography.Text strong>
                              {entry.name}
                            </Typography.Text>
                            <Typography.Text type="secondary">
                              {entry.nodeType}
                            </Typography.Text>
                          </div>
                          <Tag color={statusColor(entry.status)}>
                            {entry.status}
                          </Tag>
                        </div>
                        <Progress
                          percent={
                            totalMs > 0
                              ? Math.max(
                                  4,
                                  Math.round((entry.ms / totalMs) * 100),
                                )
                              : 0
                          }
                          showInfo={false}
                          strokeColor={
                            entry.status === "success" ? "#52c41a" : "#ff4d4f"
                          }
                        />
                        <div className="fs-run-card__meta">
                          <FieldTimeOutlined />
                          <span>{entry.ms}ms</span>
                        </div>
                        <JsonBlock value={entry.output} />
                      </article>
                    ))}
                  </div>
                </div>
              ) : (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description={running ? "运行中" : "暂无运行结果"}
                />
              ),
          },
          {
            key: "timeline",
            label: "时间线",
            children:
              trace && trace.length > 0 ? (
                <div className="fs-run-timeline">
                  {trace.map((entry, index) => (
                    <div
                      className="fs-run-timeline__row"
                      key={`${entry.nodeId}-${index}`}
                    >
                      <span
                        className="fs-run-timeline__dot"
                        data-status={entry.status}
                      >
                        <NodeIndexOutlined />
                      </span>
                      <div className="fs-run-timeline__body">
                        <div>
                          <strong>{entry.name}</strong>
                          <Tag color={statusColor(entry.status)}>
                            {entry.status}
                          </Tag>
                        </div>
                        <Progress
                          percent={
                            totalMs > 0
                              ? Math.max(
                                  4,
                                  Math.round((entry.ms / totalMs) * 100),
                                )
                              : 0
                          }
                          showInfo={false}
                          size="small"
                        />
                      </div>
                      <span className="fs-run-timeline__ms">{entry.ms}ms</span>
                    </div>
                  ))}
                </div>
              ) : (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="运行后显示节点耗时"
                />
              ),
          },
          {
            key: "problems",
            label: `问题 (${problems.length})`,
            children:
              problems.length > 0 ? (
                <div className="fs-run-panel__problems">
                  {problems.map((problem) => (
                    <Alert
                      key={problem.id}
                      type={problem.severity === "error" ? "error" : "warning"}
                      showIcon
                      icon={<WarningOutlined />}
                      message={problem.message}
                      description={problem.fixHint}
                      action={
                        problem.nodeId ? (
                          <Button
                            size="small"
                            type="link"
                            onClick={() => {
                              const nodeId = problem.nodeId;
                              if (nodeId) {
                                useEditorStore
                                  .getState()
                                  .setSelection([nodeId], []);
                                useEditorStore.setState({
                                  focusNodeId: nodeId,
                                });
                                useUiStore.getState().setRightCollapsed(false);
                              }
                            }}
                          >
                            定位节点
                          </Button>
                        ) : undefined
                      }
                    />
                  ))}
                </div>
              ) : (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="未发现问题"
                />
              ),
          },
        ]}
      />
    </section>
  );
}
