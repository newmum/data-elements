import type { Column, TableDef } from "./data";
import type { FilterRule } from "./tableFilters";
import type { TableSort } from "./tableRowActions";

export type Organization = {
  id: string;
  name: string;
  children: Organization[];
};
export type SharedSource = {
  tid: string;
  name: string;
  orgId: string;
  dbType: string;
  database?: string;
};
export type SharedTable = {
  tid: string;
  tableName: string;
  tableComment: string;
  objectType: "TABLE" | "VIEW";
  recordCount: number;
};
type Preview = { rows: TableDef["rows"]; total: number; columns: string[] };
type Field = {
  columnName: string;
  columnType: string;
  columnComment: string;
  primaryKey: number;
  nullable: number;
};
export const metadataBase = new URL(
  import.meta.env.VITE_METADATA_BASE_URL ||
    (import.meta.env.DEV ? "http://localhost:3000/" : "/"),
  typeof location === "undefined" ? "http://localhost:3020/" : location.href,
);
export const metadataHome = new URL("#/datasource-manage", metadataBase).href;

/** 通过元数据平台同源只读桥复用现有接口，风驰不保存第二份业务数据或会话。 */
export class MockMetadataClient {
  private frame = document.createElement("iframe");
  private waiters = new Map<
    string,
    {
      resolve: (value: unknown) => void;
      reject: (error: Error) => void;
      timer: ReturnType<typeof setTimeout>;
    }
  >();
  private ready: Promise<void>;
  private cleanup: () => void = () => {};
  constructor(private onAccessError: (message: string) => void = () => {}) {
    this.frame.hidden = true;
    this.frame.title = "元数据共享工作区";
    const url = new URL(metadataBase);
    url.searchParams.set("mockBridge", "1");
    url.hash = "";
    this.frame.src = url.href;
    this.ready = new Promise((resolve, reject) => {
      const timeout = setTimeout(
        () =>
          reject(
            new Error("无法读取元数据平台，请先返回平台登录并确认服务已启动"),
          ),
        20000,
      );
      const listener = (event: MessageEvent) => {
        if (
          event.origin !== metadataBase.origin ||
          event.source !== this.frame.contentWindow
        )
          return;
        const data = event.data;
        if (data?.type === "data-elements:mock-ready") {
          clearTimeout(timeout);
          resolve();
          return;
        }
        if (data?.type !== "data-elements:mock-response") return;
        const waiter = this.waiters.get(data.nonce);
        if (!waiter) return;
        clearTimeout(waiter.timer);
        this.waiters.delete(data.nonce);
        if ([401, 403, 404].includes(data.status))
          this.onAccessError(data.body?.message || "当前数据范围不可访问");
        if (data.status !== 200 || data.body?.code !== 0)
          waiter.reject(new Error(data.body?.message || "共享数据读取失败"));
        else waiter.resolve(data.body.data);
      };
      window.addEventListener("message", listener);
      this.cleanup = () => {
        clearTimeout(timeout);
        window.removeEventListener("message", listener);
        reject(new Error("工作区已关闭"));
      };
    });
    document.body.append(this.frame);
  }
  dispose() {
    this.cleanup();
    this.frame.remove();
    for (const waiter of this.waiters.values()) {
      clearTimeout(waiter.timer);
      waiter.reject(new Error("工作区已关闭"));
    }
    this.waiters.clear();
  }
  async request<T>(
    method: string,
    path: string,
    body: Record<string, unknown> = {},
    query: Record<string, unknown> = {},
  ): Promise<T> {
    await this.ready;
    return new Promise((resolve, reject) => {
      const nonce = crypto.randomUUID();
      const timer = setTimeout(() => {
        this.waiters.delete(nonce);
        reject(new Error("共享数据读取超时，请重试"));
      }, 15000);
      this.waiters.set(nonce, {
        resolve: (value) => resolve(value as T),
        reject,
        timer,
      });
      this.frame.contentWindow!.postMessage(
        {
          type: "data-elements:mock-request",
          nonce,
          method,
          path,
          body,
          query,
        },
        metadataBase.origin,
      );
    });
  }
}

export type SharedWorkspace = {
  organizations: Organization[];
  sources: SharedSource[];
  source: SharedSource;
  catalog: SharedTable[];
  tables: Record<string, TableDef>;
  expanded: string[];
  selectSource: (id: string) => void;
  refresh: () => void;
  readPage: (
    name: string,
    page: number,
    size: number,
    keyword: string,
    rules: FilterRule[],
    sort: TableSort,
  ) => Promise<{ table: TableDef; total: number }>;
};

export async function readSharedCatalog(client: MockMetadataClient) {
  await client.request("GET", "/sym/tenant/current");
  const [organizations, statistics] = await Promise.all([
    client.request<Organization[]>("POST", "/sym/org/getOrgTree"),
    client.request<{
      orgApps: Record<
        string,
        Array<{
          datasources: Array<{ value: string; label: string; dbType: string }>;
        }>
      >;
    }>("POST", "/dst/database/treeStatistics", { mode: "tree" }),
  ]);
  const sources = Object.entries(statistics.orgApps).flatMap(([orgId, apps]) =>
    apps.flatMap((app) =>
      app.datasources.map((row) => ({
        tid: row.value,
        name: row.label,
        orgId,
        dbType: row.dbType,
      })),
    ),
  );
  return { organizations, sources };
}

export async function readSharedWorkspace(
  client: MockMetadataClient,
  base: Awaited<ReturnType<typeof readSharedCatalog>>,
  id: string,
  selectSource: (id: string, refresh?: boolean) => void,
): Promise<SharedWorkspace> {
  const source = base.sources.find((row) => row.tid === id);
  if (!source) throw new Error("数据源不存在");
  const [catalog, detail] = await Promise.all([
    client.request<SharedTable[]>(
      "GET",
      "/dst/database/metadata/tables",
      {},
      { dbId: id, pageSize: 100 },
    ),
    client.request<{ database: string; dbName?: string }>(
      "POST",
      "/dst/database/detail",
      {
        tid: id,
      },
    ),
  ]);
  const findPath = (nodes: Organization[], path: string[] = []): string[] => {
    for (const node of nodes) {
      const next = [...path, `org:${node.id}`];
      if (node.id === source.orgId) return next;
      const match = findPath(node.children || [], next);
      if (match.length) return match;
    }
    return [];
  };
  const fields = new Map<string, Column[]>();
  return {
    ...base,
    source: {
      ...source,
      name: detail.dbName || source.name,
      database: detail.database,
    },
    catalog,
    selectSource,
    refresh: () => selectSource(id, true),
    tables: Object.fromEntries(
      catalog.map((row) => [
        row.tableName,
        {
          name: row.tableName,
          comment: row.tableComment,
          columns: [],
          rows: [],
        },
      ]),
    ),
    expanded: [...findPath(base.organizations), `source:${id}`, `tables:${id}`],
    async readPage(name, page, size, keyword, rules, sort) {
      const target = catalog.find((row) => row.tableName === name);
      if (!target) throw new Error("数据表不存在");
      if (!fields.has(name)) {
        const rows = await client.request<Field[]>(
          "GET",
          "/dst/database/metadata/columns",
          {},
          { tid: target.tid, snapshotOnly: true },
        );
        fields.set(
          name,
          rows.map((row) => ({
            name: row.columnName,
            type: row.columnType,
            comment: row.columnComment,
            primary: row.primaryKey === 1,
            nullable: row.nullable === 1,
          })),
        );
      }
      const columns = fields.get(name)!;
      const conditions = rules.map((rule) => {
        const column = columns.find((row) => row.name === rule.field);
        if (!column) throw new Error("筛选字段不存在");
        const numeric = /^(int|decimal|numeric|float|double)/i.test(
          column.type,
        );
        const type =
          (
            {
              equals: "eq",
              notEquals: "ne",
              contains: "like",
              startsWith: "like",
              endsWith: "like",
              isNull: "isnull",
              isNotNull: "isnotnull",
            } as Record<string, string>
          )[rule.operator] || rule.operator;
        const value =
          rule.operator === "contains"
            ? `%${rule.value}%`
            : rule.operator === "startsWith"
              ? `${rule.value}%`
              : rule.operator === "endsWith"
                ? `%${rule.value}`
                : numeric
                  ? Number(rule.value)
                  : rule.value;
        return { field: rule.field, type, value };
      });
      if (keyword)
        conditions.push({ field: "name", type: "like", value: `%${keyword}%` });
      const preview = await client.request<Preview>(
        "POST",
        "/dst/database/metadata/table/preview-data",
        {
          tableId: target.tid,
          pageNo: page,
          pageSize: size,
          conditions,
          sortField: sort?.field || "id",
          sortDir: sort?.order === "descend" ? "desc" : "asc",
          countMode: "exact",
        },
      );
      return {
        table: {
          name,
          comment: target.tableComment,
          columns,
          rows: preview.rows,
        },
        total: preview.total,
      };
    },
  };
}
