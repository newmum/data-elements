import fs from "node:fs/promises";
import path from "node:path";
import { createHash } from "node:crypto";
import { fileURLToPath } from "node:url";

export const repository = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "..",
);
export const sourceDigest = (source) =>
  createHash("sha256").update(source.replace(/\r\n?/g, "\n")).digest("hex");

function metadata(source) {
  const separator = source.indexOf("================================");
  return JSON.parse(separator >= 0 ? source.slice(0, separator) : source);
}

/** 非 HTTP 的共享函数依赖单独校验，不能伪造为接口或仅放在不执行的说明字段中。 */
export async function checkFunctionDependency(root, dependency) {
  const errors = [], source = dependency.source;
  if (source?.kind !== "magic-function" || !source.file?.startsWith("magic/function/") || !dependency.review) {
    return ["共享函数依赖须登记 magic-function 源码及审阅说明"];
  }
  const filename = path.resolve(root, source.file), relative = path.relative(root, filename);
  if (relative.startsWith("..") || path.isAbsolute(relative)) return ["共享函数源码路径越界"];
  try {
    const content = await fs.readFile(filename, "utf8"), meta = metadata(content);
    if (sourceDigest(content) !== source.reviewedSha256) errors.push(`共享函数已变化，须核对 Mock：${source.file}`);
    const parts = [meta.path];
    let id = meta.groupId, folder = path.dirname(filename);
    const seen = new Set();
    while (id && !["0", "function:0"].includes(id)) {
      if (seen.has(id)) throw new Error("函数分组循环");
      seen.add(id);
      const group = metadata(await fs.readFile(path.join(folder, "group.json"), "utf8"));
      if (group.id !== id) throw new Error("函数分组身份不一致");
      parts.unshift(group.path || ""); id = group.parentId; folder = path.dirname(folder);
    }
    const route = `/${parts.join("/").split("/").filter(Boolean).join("/")}`;
    if (meta.id !== source.id || route !== source.path) errors.push(`共享函数身份或路径已变化：${source.file}`);
  } catch { errors.push(`共享函数或分组无法核验：${source.file}`); }
  return errors;
}

/** 校验真实资源身份及审阅摘要；脚本内部变化也提示人工判断是否影响契约。 */
export async function checkContracts(root = repository) {
  const specification = JSON.parse(
    await fs.readFile(path.join(root, "mock/contracts/chengtian.json"), "utf8"),
  );
  const index = JSON.parse(
    await fs.readFile(path.join(root, "magic/.manifest.json"), "utf8"),
  );
  const groups = new Map();
  for (const entry of index.resources.filter(
    (row) => row.path.startsWith("api/") && row.path.endsWith("/group.json"),
  )) {
    const meta = metadata(
      await fs.readFile(path.join(root, "magic", entry.path), "utf8"),
    );
    groups.set(meta.id, meta);
  }
  const prefix = (id, seen = new Set()) => {
    if (!id || ["0", "api:0"].includes(id)) return "";
    if (seen.has(id) || !groups.has(id))
      throw new Error(`Magic 分组断链：${id}`);
    seen.add(id);
    const group = groups.get(id);
    return `${prefix(group.parentId, seen)}/${String(group.path || "").replace(/^\/+|\/+$/g, "")}`;
  };
  const errors = [],
    operations = new Set();
  for (const entry of specification.interfaces) {
    if (operations.has(entry.operation))
      errors.push(`接口重复：${entry.operation}`);
    operations.add(entry.operation);
    const filename = path.resolve(root, entry.source.file);
    const relative = path.relative(root, filename);
    if (relative.startsWith("..") || path.isAbsolute(relative)) {
      errors.push(`源码路径越界：${entry.operation}`);
      continue;
    }
    let source;
    try {
      source = await fs.readFile(filename, "utf8");
    } catch {
      errors.push(`真实实现不存在：${entry.source.file}`);
      continue;
    }
    if (sourceDigest(source) !== entry.source.reviewedSha256)
      errors.push(
        `真实实现已变化，须核对 mock 契约并更新审阅摘要及说明：${entry.operation}`,
      );
    if (entry.source.kind === "magic") {
      const meta = metadata(source);
      const route = `/${`${prefix(meta.groupId)}/${meta.path || ""}`.split("/").filter(Boolean).join("/")}`;
      if (
        meta.id !== entry.source.id ||
        `${meta.method.toUpperCase()} ${route}` !== entry.operation
      )
        errors.push(`Magic 身份或路由已变化：${entry.operation}`);
    } else if (
      !["technical-retained", "pending-magic"].includes(entry.source.state) ||
      !entry.source.reason
    ) {
      errors.push(
        `Java 必须登记待迁移状态或具体技术保留理由：${entry.operation}`,
      );
    }
    if (!entry.response || !entry.request || !entry.review)
      errors.push(`缺少请求、响应或审阅说明：${entry.operation}`);
  }
  for (const dependency of specification.dependencies || []) {
    if (!Array.isArray(dependency.operations) || !dependency.operations.length ||
        new Set(dependency.operations).size !== dependency.operations.length ||
        dependency.operations.some((operation) => !operations.has(operation)))
      errors.push("共享函数依赖必须关联已登记且不重复的 HTTP operation");
    errors.push(...await checkFunctionDependency(root, dependency));
  }
  return { interfaces: specification.interfaces.length, dependencies: specification.dependencies?.length || 0, errors };
}

/** 小型 JSON Schema 子集，明确覆盖本阶段实际校验的对象、数组和标量。 */
export function validateShape(value, schema, at = "$") {
  if (!schema || schema.type === "any") return;
  const type = Array.isArray(value)
    ? "array"
    : value === null
      ? "null"
      : typeof value;
  if (type !== schema.type)
    throw new Error(`${at} 应为 ${schema.type}，实际为 ${type}`);
  if (schema.type === "object") {
    for (const key of schema.required || [])
      if (!Object.hasOwn(value, key)) throw new Error(`${at}.${key} 缺失`);
    for (const [key, child] of Object.entries(schema.properties || {}))
      if (Object.hasOwn(value, key))
        validateShape(value[key], child, `${at}.${key}`);
  }
  if (schema.type === "array" && schema.items)
    value.forEach((item, i) =>
      validateShape(item, schema.items, `${at}[${i}]`),
    );
}

if (
  process.argv[1] &&
  path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)
) {
  const result = await checkContracts();
  if (result.errors.length) {
    console.error(result.errors.join("\n"));
    process.exitCode = 1;
  } else
    console.log(
      `澄天 mock 契约：${result.interfaces} 项接口、${result.dependencies} 项共享函数依赖的身份与审阅摘要通过`,
    );
}
