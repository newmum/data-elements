/** 仅投影当前库已有配置，不补造菜单层级或机构。 */
export function organizationTree(rows) {
  const nodes = new Map(
    rows
      .filter((row) => row.id !== "ROOT")
      .map((row) => [
        row.id,
        {
          ...structuredClone(row),
          tid: row.id,
          orgName: row.name,
          value: row.id,
          label: row.name,
          children: [],
        },
      ]),
  );
  const roots = [];
  for (const node of nodes.values()) {
    if (nodes.has(node.parentId)) nodes.get(node.parentId).children.push(node);
    else roots.push(node);
  }
  return roots;
}

export function menuRoutes(rows) {
  const byId = new Map(rows.map((row) => [row.id, row]));
  const fullPath = (row, seen = new Set()) => {
    if (seen.has(row.id)) throw new Error("菜单配置存在循环");
    seen.add(row.id);
    const parent = byId.get(row.parentId);
    return (parent ? fullPath(parent, seen) : "") + (row.url || "");
  };
  const build = (row) => {
    const node =
      row.resourceType === 2
        ? {
            path: `__group_${row.sno || row.name}__`,
            meta: { type: "group", title: row.name },
          }
        : {
            path: row.url || "",
            name: /^https?:/.test(row.url || "")
              ? row.code || row.sno
              : fullPath(row).replace(/^\//, "").replaceAll("/", "-"),
            meta: { title: row.name },
          };
    if (row.resourceType !== 2) {
      if (row.sno?.startsWith("inner_")) node.component = row.sno.slice(6);
      else if (row.sno) node.componentName = row.sno;
      if (row.status === 1) node.meta.hidden = true;
      if (row.staticIcon) node.meta.icon = row.staticIcon;
      if (row.resourceType === 3 || /^https?:/.test(row.url || "")) {
        if (row.openType === 1) node.meta.openMode = "iframe";
        else node.meta.target = row.openType === 2 ? "_blank" : "_self";
      }
    }
    const children = rows
      .filter(
        (item) =>
          item.parentId === row.id &&
          item.url !== "/my-workplace" &&
          item.sno !== "my-workplace",
      )
      .map(build);
    if (children.length) node.children = children;
    if (node.path === "/workplace") node.redirect = "/workplace/my-apply/list";
    return node;
  };
  return rows
    .filter((row) => row.parentId === "ROOT" || !byId.has(row.parentId))
    .map(build);
}
