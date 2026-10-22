import {
  Alert,
  Button,
  Divider,
  Drawer,
  List,
  Select,
  Space,
  Tabs,
  Typography,
} from "antd";
import { CodeOutlined } from "@ant-design/icons";
import { useMemo, useState } from "react";
import { compile } from "@/compiler";
import { useVersions } from "@/services/flows";
import { useEditorStore } from "@/store/editorStore";
import type { Flow } from "@/domain/flow-dsl";
import { CodeField } from "../node-config/CodeField";
import "./ScriptPreview.css";
import { DiffView } from "./DiffView";

function semanticDiff(from?: Flow, to?: Flow): string[] {
  if (!from || !to) return [];
  const oldNodes = new Map(from.nodes.map((node) => [node.id, node]));
  const newNodes = new Map(to.nodes.map((node) => [node.id, node]));
  const changes: string[] = [];
  for (const [id, node] of newNodes) {
    const old = oldNodes.get(id);
    if (!old) {
      changes.push(`新增节点：${node.name ?? node.type} (${node.type})`);
      continue;
    }
    if (old.type !== node.type)
      changes.push(
        `节点类型变化：${old.name ?? id} ${old.type} -> ${node.type}`,
      );
    else if (
      JSON.stringify(old.data) !== JSON.stringify(node.data) ||
      old.name !== node.name
    ) {
      changes.push(`节点配置变化：${node.name ?? node.type} (${node.type})`);
    }
  }
  for (const [id, node] of oldNodes) {
    if (!newNodes.has(id))
      changes.push(`删除节点：${node.name ?? node.type} (${node.type})`);
  }
  const oldEdges = new Set(
    from.edges.map(
      (edge) =>
        `${edge.source}:${edge.sourcePort}->${edge.target}:${edge.targetPort}`,
    ),
  );
  const newEdges = new Set(
    to.edges.map(
      (edge) =>
        `${edge.source}:${edge.sourcePort}->${edge.target}:${edge.targetPort}`,
    ),
  );
  const addedEdges = [...newEdges].filter((edge) => !oldEdges.has(edge)).length;
  const removedEdges = [...oldEdges].filter(
    (edge) => !newEdges.has(edge),
  ).length;
  if (addedEdges) changes.push(`新增连线：${addedEdges} 条`);
  if (removedEdges) changes.push(`删除连线：${removedEdges} 条`);
  return changes;
}

export function ScriptPreview() {
  const [open, setOpen] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [fromVersion, setFromVersion] = useState<number | undefined>();
  const [toVersion, setToVersion] = useState<number | undefined>();
  const flow = useEditorStore((s) => s.flow);
  const result = useMemo(() => (flow ? compile(flow) : null), [flow]);
  const {
    data: versions = [],
    isLoading,
    isError,
    refetch,
  } = useVersions(open && historyOpen ? flow?.flowId : undefined);
  const versionOptions = versions.map((item) => ({
    value: item.version,
    label: `v${item.version} · ${item.status} · ${item.publishTime}`,
  }));
  const from =
    versions.find((item) => item.version === fromVersion) ?? versions[1];
  const to = versions.find((item) => item.version === toVersion) ?? versions[0];
  const fromDsl = from ? JSON.stringify(from.dsl, null, 2) : "";
  const toDsl = to ? JSON.stringify(to.dsl, null, 2) : "";
  const nodeChanges = semanticDiff(from?.dsl, to?.dsl);

  return (
    <>
      <Button icon={<CodeOutlined />} onClick={() => setOpen(true)}>
        生成脚本
      </Button>
      <Drawer
        title="生成脚本预览"
        rootClassName="fs-script-preview"
        extra={
          <Button size="small" onClick={() => setHistoryOpen(!historyOpen)}>
            {historyOpen ? "收起历史版本" : "查看历史版本"}
          </Button>
        }
        size="large"
        open={open}
        onClose={() => setOpen(false)}
      >
        {result && !result.ok && (
          <Alert
            type="error"
            showIcon
            message="编译存在错误"
            description={result.problems.map((p) => p.message).join("；")}
            style={{ marginBottom: 12 }}
          />
        )}
        <p className="fs-script-preview__hint">
          根据当前草稿生成 · 只读预览 · 可复制或展开查看
        </p>
        <CodeField
          label="生成脚本"
          height={historyOpen ? "32vh" : "calc(100dvh - 180px)"}
          language="javascript"
          value={result?.script ?? ""}
          readOnly
        />
        {historyOpen && isError && (
          <Alert
            style={{ marginTop: 16 }}
            type="warning"
            showIcon
            title="历史版本暂时无法读取"
            description="请检查版本接口是否可用，当前脚本预览不受影响。"
            action={
              <Button size="small" onClick={() => void refetch()}>
                重试
              </Button>
            }
          />
        )}
        {historyOpen && isLoading && (
          <p className="fs-script-preview__hint">正在读取历史版本…</p>
        )}
        {historyOpen && !isError && !isLoading && versions.length < 2 && (
          <p className="fs-script-preview__hint">
            至少有两个历史版本后，可以查看配置和脚本差异。
          </p>
        )}
        {historyOpen && versions.length >= 2 && (
          <>
            <Divider>版本对比</Divider>
            <Space style={{ marginBottom: 12 }} wrap>
              <Typography.Text type="secondary">旧版本</Typography.Text>
              <Select
                style={{ width: 260 }}
                options={versionOptions}
                value={from?.version}
                onChange={setFromVersion}
              />
              <Typography.Text type="secondary">新版本</Typography.Text>
              <Select
                style={{ width: 260 }}
                options={versionOptions}
                value={to?.version}
                onChange={setToVersion}
              />
            </Space>
            <Tabs
              items={[
                {
                  key: "semantic",
                  label: "节点变化",
                  children: (
                    <List
                      size="small"
                      bordered
                      dataSource={
                        nodeChanges.length
                          ? nodeChanges
                          : ["节点和连线无语义变化"]
                      }
                      renderItem={(item) => <List.Item>{item}</List.Item>}
                    />
                  ),
                },
                {
                  key: "script",
                  label: "脚本差异",
                  children: (
                    <DiffView
                      original={from?.script ?? ""}
                      modified={to?.script ?? ""}
                    />
                  ),
                },
                {
                  key: "dsl",
                  label: "编排结构差异",
                  children: <DiffView original={fromDsl} modified={toDsl} />,
                },
              ]}
            />
          </>
        )}
      </Drawer>
    </>
  );
}
