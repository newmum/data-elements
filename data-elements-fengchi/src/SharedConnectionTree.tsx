import type { DataNode } from "antd/es/tree";
import type { SharedWorkspace, Organization } from "./sharedWorkspace";
import NativeIcon from "./NativeIcon";
import DatabaseLogo from "./DatabaseLogo";
import ConnectionTreeLabel from "./ConnectionTreeLabel";

export function sharedSearchKeys(workspace: SharedWorkspace): string[] {
  const visit = (nodes: Organization[]): string[] =>
    nodes.flatMap((node) => [`org:${node.id}`, ...visit(node.children || [])]);
  return [...visit(workspace.organizations), ...workspace.expanded];
}

export function sharedConnectionTree(
  workspace: SharedWorkspace,
  search: string,
): DataNode[] {
  const byOrg = new Map<string, DataNode[]>();
  for (const source of workspace.sources) {
    const current = source.tid === workspace.source.tid;
    const objects = workspace.catalog.filter(
      (table) =>
        !search ||
        source.name.includes(search) ||
        table.tableName.includes(search) ||
        table.tableComment.includes(search),
    );
    const folders = current
      ? (["TABLE", "VIEW"] as const).map((type) => ({
          key: `${type === "TABLE" ? "tables" : "views"}:${source.tid}`,
          title: (
            <ConnectionTreeLabel
              label={type === "TABLE" ? "表" : "视图"}
              count={objects.filter((row) => row.objectType === type).length}
            />
          ),
          icon: <NativeIcon name="folder" size={14} />,
          children: objects
            .filter((row) => row.objectType === type)
            .map((row) => ({
              key: `table-${row.tableName}`,
              title: (
                <ConnectionTreeLabel
                  label={row.tableName}
                  count={row.recordCount}
                />
              ),
              icon: (
                <NativeIcon
                  name={type === "VIEW" ? "view" : "table"}
                  size={14}
                />
              ),
              isLeaf: true,
            })),
        }))
      : undefined;
    if (
      search &&
      !source.name.includes(search) &&
      !folders?.some((folder) => folder.children.length)
    )
      continue;
    const node: DataNode = {
      key: `source:${source.tid}`,
      title: <ConnectionTreeLabel label={source.name} />,
      icon: <DatabaseLogo type={source.dbType} size={14} />,
      isLeaf: false,
      ...(folders ? { children: folders } : {}),
    };
    byOrg.set(source.orgId, [...(byOrg.get(source.orgId) || []), node]);
  }
  const visit = (org: Organization): DataNode | null => {
    const children = [
      ...(org.children || [])
        .map(visit)
        .filter((node): node is DataNode => node !== null),
      ...(byOrg.get(org.id) || []),
    ];
    // 与元数据平台一致，只展示当前数据范围内含数据源的机构分支。
    if (!children.length) return null;
    return {
      key: `org:${org.id}`,
      title: <ConnectionTreeLabel label={org.name} />,
      icon: <NativeIcon name="folder" size={14} />,
      children,
    };
  };
  const roots = workspace.organizations
    .map(visit)
    .filter((node): node is DataNode => node !== null);
  if (byOrg.has(""))
    roots.push({
      key: "unassigned-sources",
      title: "未分配机构的数据源",
      icon: <NativeIcon name="folder" size={14} />,
      children: byOrg.get(""),
    });
  return roots;
}
