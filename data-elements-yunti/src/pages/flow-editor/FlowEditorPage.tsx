import {
  ArrowLeftOutlined,
  CloseOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  PlayCircleOutlined,
  SaveOutlined,
  UploadOutlined,
  ApartmentOutlined,
  CheckCircleOutlined,
  WarningOutlined,
  DownOutlined,
} from "@ant-design/icons";
import {
  App as AntdApp,
  Button,
  Modal,
  Select,
  Space,
  Spin,
  Tooltip,
  Typography,
} from "antd";
import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Canvas } from "@/features/canvas/Canvas";
import { ScriptPreview } from "@/features/compiler-preview/ScriptPreview";
import { ConfigPanel } from "@/features/node-config/ConfigPanel";
import { Palette } from "@/features/node-palette/Palette";
import { RunPanel } from "@/features/run-panel/RunPanel";
import { useDebug, useFlow, usePublish, useSaveFlow } from "@/services/flows";
import { useRuntimeNodes } from "@/services/runtimeNodes";
import { useEditorStore } from "@/store/editorStore";
import { useUiStore } from "@/store/uiStore";
import { resolveReturnTarget } from "@/utils/returnNavigation";
import { resolveParentOrigin } from "@/services/embeddedSession";
import "./FlowEditorPage.css";

export default function FlowEditorPage() {
  const navigate = useNavigate();
  const { message, modal } = AntdApp.useApp();
  const { flowId } = useParams();
  const { data, isLoading, isError, error, refetch } = useFlow(flowId);
  const save = useSaveFlow();
  const debug = useDebug();
  const publish = usePublish();
  const {
    data: runtimeNodes = [],
    isError: runtimeError,
    isLoading: runtimeLoading,
    refetch: reloadRuntime,
  } = useRuntimeNodes();
  const [publishOpen, setPublishOpen] = useState(false);
  const loadedFlow = useRef("");
  const [runError, setRunError] = useState("");
  const reportError = (action: string, error: unknown) => {
    const detail =
      error instanceof Error ? error.message : "请检查网络连接后重试";
    const text = detail.includes("No static resource")
      ? `当前环境尚未接通${action}接口，草稿仍保留在当前页面。`
      : `${action}未完成：${detail}`;
    void message.error(text);
    return text;
  };
  const defaultRuntimeNodeId =
    runtimeNodes.find((node) => node.defaultNode)?.id ?? runtimeNodes[0]?.id;
  const [targetNodeId, setTargetNodeId] = useState<string | undefined>();
  const flow = useEditorStore((s) => s.flow);
  const dirty = useEditorStore((s) => s.dirty);
  const lockVersion = useEditorStore((s) => s.lockVersion);
  const problems = useEditorStore((s) => s.problems);
  const leftCollapsed = useUiStore((s) => s.leftCollapsed);
  const rightCollapsed = useUiStore((s) => s.rightCollapsed);
  const bottomOpen = useUiStore((s) => s.bottomOpen);
  const selectedNodeIds = useEditorStore((s) => s.selectedNodeIds);
  const showConfigPanel = selectedNodeIds.length > 0 && !rightCollapsed;
  const embeddedMode = useMemo(() => {
    const params = new URLSearchParams(window.location.search);
    return (
      params.get("communication") === "postMessage" || window.parent !== window
    );
  }, []);
  const returnTarget = useMemo(
    () => resolveReturnTarget(window.location.search, window.location.href),
    [],
  );

  useEffect(() => {
    if (!data) return;
    if (
      loadedFlow.current === data.flow.flowId &&
      useEditorStore.getState().dirty
    )
      return;
    loadedFlow.current = data.flow.flowId;
    useEditorStore.getState().setFlow(data.flow, data.lockVersion);
    useEditorStore.temporal.getState().clear();
  }, [data]);

  const saveFlow = async () => {
    if (!flow || save.isPending) return;
    try {
      const result = await save.mutateAsync({
        id: flow.flowId,
        flow,
        lockVersion,
      });
      useEditorStore.getState().markSaved(result.lockVersion);
      void message.success("草稿已保存");
    } catch (error) {
      reportError("保存", error);
    }
  };

  const runFlow = async () => {
    if (!flow || debug.isPending) return;
    setRunError("");
    useUiStore.getState().setBottomOpen(true);
    useUiStore.getState().setBottomActiveKey("result");
    useEditorStore.getState().setRunning(true);
    useEditorStore.getState().clearRunStatus();
    try {
      const result = await debug.mutateAsync(flow);
      useEditorStore.getState().setRunTrace(result.trace);
      for (const entry of result.trace) {
        useEditorStore
          .getState()
          .setNodeStatus(
            entry.nodeId,
            entry.status === "success" ? "success" : "fail",
          );
      }
      void message[result.success ? "success" : "error"](
        result.success ? "试运行完成" : (result.error ?? "试运行失败"),
      );
    } catch (error) {
      setRunError(reportError("运行", error));
    } finally {
      useEditorStore.getState().setRunning(false);
    }
  };

  const publishFlow = async (selectedTargetNodeId?: string) => {
    if (!flow || publish.isPending) return;
    if (dirty) {
      void message.warning("请先保存当前草稿，再发布服务。");
      return;
    }
    const errors = problems.filter((p) => p.severity === "error");
    if (errors.length > 0) {
      void message.error("发布前请先修复错误");
      return;
    }
    try {
      const result = await publish.mutateAsync({
        id: flow.flowId,
        remark: "frontend publish",
        targetNodeId: selectedTargetNodeId ?? defaultRuntimeNodeId,
      });
      setPublishOpen(false);
      void message.success(`已发布到 ${result.runtimeNodeName}：${result.url}`);
    } catch (error) {
      reportError("发布", error);
    }
  };

  const runtimeNodeOptions = useMemo(
    () =>
      runtimeNodes
        .filter((node) => node.enabled)
        .map((node) => ({
          value: node.id,
          label: `${node.defaultNode ? "默认 · " : ""}${node.businessDomain} / ${node.serviceType} / ${node.name}`,
        })),
    [runtimeNodes],
  );

  const closeEditor = () => {
    if (dirty) {
      modal.confirm({
        title: "离开当前编排？",
        content: "当前草稿有未保存的修改，离开后这些修改将丢失。",
        okText: "离开",
        cancelText: "继续编辑",
        okButtonProps: { danger: true },
        onOk: leaveEditor,
      });
      return;
    }
    leaveEditor();
  };
  const leaveEditor = () => {
    if (embeddedMode) {
      const payload = { flowId, returnTo: returnTarget };
      const origin = resolveParentOrigin(
        window.location.href,
        document.referrer,
      );
      if (origin)
        window.parent.postMessage({ type: "YUNTI_APP_CLOSE", payload }, origin);
      return;
    }
    if (returnTarget) {
      window.location.assign(returnTarget);
      return;
    }
    if (window.history.length > 1) {
      navigate(-1);
      return;
    }
    navigate("/");
  };

  if (isError) {
    return (
      <div className="fs-editor-loading" role="alert">
        <Typography.Text strong>服务编排加载失败</Typography.Text>
        <Typography.Text type="secondary">
          {error instanceof Error
            ? error.message
            : "请检查登录状态和当前服务的访问权限"}
        </Typography.Text>
        <Space>
          <Button onClick={closeEditor}>返回数据服务</Button>
          <Button type="primary" onClick={() => void refetch()}>
            重新加载
          </Button>
        </Space>
      </div>
    );
  }

  if (isLoading || !flow) {
    return (
      <div className="fs-editor-loading">
        <Spin size="large" />
        <Typography.Text type="secondary">正在加载服务编排…</Typography.Text>
      </div>
    );
  }

  return (
    <div className="fs-editor">
      <header className="fs-editor__topbar">
        <div className="fs-editor__identity">
          <Tooltip title="返回数据服务">
            <Button
              type="text"
              icon={<ArrowLeftOutlined />}
              onClick={closeEditor}
              aria-label="返回数据服务"
            />
          </Tooltip>
          <span className="fs-editor__identity-divider" />
          <span className="fs-editor__brand-icon">
            <ApartmentOutlined />
          </span>
          <div className="fs-editor__name">
            <span>服务编排</span>
            <strong title={flow.name}>{flow.name}</strong>
          </div>
          <span className={`fs-editor__save-state ${dirty ? "is-dirty" : ""}`}>
            {dirty ? (
              "待保存"
            ) : (
              <>
                <CheckCircleOutlined /> 已保存
              </>
            )}
          </span>
        </div>
        <Space className="fs-editor__actions" size={8}>
          <Button
            icon={<SaveOutlined />}
            loading={save.isPending}
            onClick={saveFlow}
          >
            保存
          </Button>
          <Button
            icon={<PlayCircleOutlined />}
            loading={debug.isPending}
            onClick={runFlow}
          >
            运行
          </Button>
          <Button
            icon={<UploadOutlined />}
            type="primary"
            loading={publish.isPending}
            onClick={() => {
              setTargetNodeId(defaultRuntimeNodeId);
              setPublishOpen(true);
            }}
          >
            发布
          </Button>
          <ScriptPreview />
          <Button
            className="fs-editor__close"
            icon={<CloseOutlined />}
            onClick={closeEditor}
            aria-label="关闭"
          />
        </Space>
      </header>
      <main className="fs-editor__main">
        {!leftCollapsed && <Palette />}
        <section className="fs-editor__workbench">
          <Button
            className="fs-editor__side-toggle fs-editor__side-toggle--left"
            aria-label={leftCollapsed ? "展开组件库" : "收起组件库"}
            title={leftCollapsed ? "展开组件库" : "收起组件库"}
            icon={leftCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() =>
              useUiStore.getState().setLeftCollapsed(!leftCollapsed)
            }
          />
          <Button
            className="fs-editor__side-toggle fs-editor__side-toggle--right"
            aria-label={showConfigPanel ? "收起组件配置" : "展开组件配置"}
            title={showConfigPanel ? "收起组件配置" : "选择节点后展开配置"}
            icon={
              showConfigPanel ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />
            }
            disabled={selectedNodeIds.length === 0}
            onClick={() =>
              useUiStore.getState().setRightCollapsed(!rightCollapsed)
            }
          />
          <Canvas />
          {bottomOpen && <RunPanel errorMessage={runError} />}
        </section>
        {showConfigPanel && <ConfigPanel />}
      </main>
      <footer className="fs-editor__statusbar">
        <span>
          {flow.nodes.length} 个节点
          <span className="fs-editor__statusbar-sep">/</span>
          {flow.edges.length} 条连线
        </span>
        <button
          type="button"
          onClick={() => {
            useUiStore.getState().setBottomActiveKey("problems");
            useUiStore.getState().setBottomOpen(!bottomOpen);
          }}
        >
          <span className={problems.length ? "has-problems" : ""}>
            {problems.length ? <WarningOutlined /> : <CheckCircleOutlined />}
            {problems.length ? `${problems.length} 项配置提示` : "配置检查通过"}
          </span>
        </button>
        <button
          type="button"
          className="fs-editor__run-toggle"
          onClick={() => useUiStore.getState().setBottomOpen(!bottomOpen)}
        >
          <span>运行与检查</span>
          <DownOutlined rotate={bottomOpen ? 0 : 180} />
        </button>
      </footer>
      <Modal
        title="选择发布节点"
        open={publishOpen}
        confirmLoading={publish.isPending}
        okText="确认发布"
        cancelText="取消"
        okButtonProps={{
          disabled:
            runtimeError || runtimeLoading || runtimeNodeOptions.length === 0,
        }}
        onCancel={() => setPublishOpen(false)}
        onOk={() => publishFlow(targetNodeId)}
      >
        <Typography.Paragraph type="secondary">
          按业务域和服务类型选择目标运行节点，用于分摊负载和隔离服务。
        </Typography.Paragraph>
        <Select
          style={{ width: "100%" }}
          value={targetNodeId ?? defaultRuntimeNodeId}
          options={runtimeNodeOptions}
          onChange={setTargetNodeId}
          placeholder="选择发布节点"
        />
        {runtimeError && (
          <p role="alert" className="fs-field-error">
            运行节点加载失败。
            <Button
              size="small"
              type="link"
              onClick={() => void reloadRuntime()}
            >
              重新加载
            </Button>
          </p>
        )}
        {!runtimeError && !runtimeLoading && !runtimeNodeOptions.length && (
          <p className="fs-field-error">
            暂无启用的运行节点，请先配置运行节点。
          </p>
        )}
      </Modal>
    </div>
  );
}
