import { Collapse, Empty, Input, Segmented, Tooltip } from "antd";
import { AppstoreOutlined, SearchOutlined } from "@ant-design/icons";
import { useMemo, useState } from "react";
import { groupedNodeDefs, requireNodeDef } from "@/domain/node-registry";
import { useEditorStore } from "@/store/editorStore";
import { useUiStore } from "@/store/uiStore";
import { resolveIcon } from "@/features/canvas/iconMap";
import "./Palette.css";

export function Palette() {
  const [keyword, setKeyword] = useState("");
  const [mode, setMode] = useState("library");
  const flow = useEditorStore((s) => s.flow);
  const selectedId = useEditorStore((s) => s.selectedNodeIds[0]);

  const groups = useMemo(() => {
    const q = keyword.trim().toLowerCase();
    return groupedNodeDefs()
      .map((group) => ({
        ...group,
        defs: group.defs.filter(
          (def) =>
            !q ||
            def.label.toLowerCase().includes(q) ||
            def.type.toLowerCase().includes(q) ||
            def.description.toLowerCase().includes(q),
        ),
      }))
      .filter((group) => group.defs.length > 0);
  }, [keyword]);
  const outlineNodes = (flow?.nodes ?? [])
    .map((node, index) => ({ node, index }))
    .filter(({ node }) =>
      `${node.name} ${requireNodeDef(node.type).label}`
        .toLowerCase()
        .includes(keyword.trim().toLowerCase()),
    );

  const addNode = (type: string) => {
    useUiStore.getState().setRightCollapsed(false);
    const count = flow?.nodes.length ?? 0;
    useEditorStore.getState().addNode(type, {
      x: 180 + (count % 4) * 40,
      y: 160 + count * 18,
    });
  };

  return (
    <aside className="fs-palette" aria-label="组件库">
      <div className="fs-palette__header">
        <div className="fs-palette__title">
          <AppstoreOutlined />
          {mode === "outline" ? "流程节点" : "组件库"}
          <span>
            {mode === "outline"
              ? outlineNodes.length
              : groups.reduce((n, g) => n + g.defs.length, 0)}
          </span>
        </div>
        <Segmented
          size="small"
          block
          value={mode}
          onChange={setMode}
          options={[
            { label: "添加组件", value: "library" },
            { label: "流程节点", value: "outline" },
          ]}
        />
        <Input
          prefix={<SearchOutlined />}
          allowClear
          size="small"
          placeholder={mode === "library" ? "搜索组件" : "搜索流程节点"}
          aria-label="搜索组件"
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
        />
      </div>
      <div className="fs-palette__body">
        {mode === "outline" ? (
          <div className="fs-palette__outline">
            {outlineNodes.length === 0 && (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="无匹配流程节点"
              />
            )}
            {outlineNodes.map(({ node, index }) => {
              const def = requireNodeDef(node.type);
              const Icon = resolveIcon(def.icon);
              return (
                <button
                  key={node.id}
                  type="button"
                  aria-label={`配置${node.name || def.label}`}
                  aria-pressed={selectedId === node.id}
                  className={`fs-palette__outline-item ${selectedId === node.id ? "is-selected" : ""}`}
                  onClick={() => {
                    useEditorStore.getState().setSelection([node.id], []);
                    useUiStore.getState().setRightCollapsed(false);
                  }}
                >
                  <span className="fs-palette__outline-number">
                    {String(index + 1).padStart(2, "0")}
                  </span>
                  <span style={{ color: def.color }}>
                    <Icon />
                  </span>
                  <span>
                    <strong>{node.name || def.label}</strong>
                    <small>{def.label}</small>
                  </span>
                </button>
              );
            })}
          </div>
        ) : groups.length === 0 ? (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="无匹配节点"
          />
        ) : (
          <Collapse
            ghost
            styles={{
              header: { padding: "8px 4px" },
              body: { padding: "0 0 4px" },
            }}
            defaultActiveKey={groups.map((g) => g.category)}
            items={groups.map((group) => ({
              key: group.category,
              label: (
                <span className="fs-palette__group-label">
                  {group.label}
                  <span>{group.defs.length}</span>
                </span>
              ),
              children: (
                <div className="fs-palette__grid">
                  {group.defs.map((def) => {
                    const Icon = resolveIcon(def.icon);
                    const disabled = Boolean(
                      def.singleton &&
                      flow?.nodes.some((n) => n.type === def.type),
                    );
                    return (
                      <Tooltip
                        key={def.type}
                        title={def.description}
                        placement="right"
                      >
                        <button
                          className="fs-palette__item"
                          type="button"
                          draggable={!disabled}
                          disabled={disabled}
                          onClick={() => addNode(def.type)}
                          onDragStart={(event) => {
                            event.dataTransfer.setData(
                              "application/x-yunti-node-type",
                              def.type,
                            );
                            event.dataTransfer.effectAllowed = "copy";
                          }}
                        >
                          <span
                            className="fs-palette__icon"
                            style={{
                              color: def.color,
                              background: `${def.color}18`,
                            }}
                          >
                            <Icon />
                          </span>
                          <span className="fs-palette__meta">
                            <span className="fs-palette__name">
                              {def.label}
                            </span>
                          </span>
                        </button>
                      </Tooltip>
                    );
                  })}
                </div>
              ),
            }))}
          />
        )}
      </div>
      <footer className="fs-palette__footer">
        {mode === "outline" ? "点击节点打开配置" : "点击添加 · 拖拽到画布定位"}
      </footer>
    </aside>
  );
}
