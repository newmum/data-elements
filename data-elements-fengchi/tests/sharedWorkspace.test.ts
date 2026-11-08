import { describe, it, expect, vi } from "vitest";
import {
  readSharedCatalog,
  readSharedWorkspace,
  type MockMetadataClient,
} from "../src/sharedWorkspace";
import { sharedConnectionTree } from "../src/SharedConnectionTree";

describe("shared metadata contracts", () => {
  it("reads the organization/source tree in a fixed batch and paginates one selected table", async () => {
    const request = vi.fn(
      async (
        _method: string,
        path: string,
        body: Record<string, unknown> = {},
        _query: Record<string, unknown> = {},
      ) => {
        if (path === "/sym/tenant/current") return { tid: "tenant" };
        if (path === "/sym/org/getOrgTree")
          return [{ id: "org", name: "实际机构", children: [] }];
        if (path === "/dst/database/treeStatistics")
          return {
            orgApps: {
              org: [
                {
                  datasources: [
                    { value: "source", label: "共享数据源", dbType: "mysql" },
                  ],
                },
              ],
            },
          };
        if (path === "/dst/database/detail")
          return { database: "shared_database" };
        if (path === "/dst/database/metadata/tables")
          return [
            {
              tid: "table",
              tableName: "shared_table",
              tableComment: "共享表",
              objectType: "TABLE",
              recordCount: 120,
            },
          ];
        if (path === "/dst/database/metadata/columns")
          return [
            {
              columnName: "id",
              columnType: "int",
              columnComment: "主键",
              primaryKey: 1,
              nullable: 0,
            },
          ];
        if (path === "/dst/database/metadata/table/preview-data")
          return {
            rows: Array.from({ length: Number(body.pageSize) }, (_, i) => ({
              id: i + 1,
            })),
            columns: ["id"],
            total: 120,
          };
        throw new Error("Unexpected interface");
      },
    );
    const client = { request } as unknown as MockMetadataClient;
    const catalog = await readSharedCatalog(client);
    expect(request).toHaveBeenCalledTimes(3);
    const workspace = await readSharedWorkspace(
      client,
      catalog,
      "source",
      vi.fn(),
    );
    expect(workspace.source.database).toBe("shared_database");
    expect(workspace.expanded).toEqual([
      "org:org",
      "source:source",
      "tables:source",
    ]);
    const tree = sharedConnectionTree(workspace, "");
    expect(tree[0].key).toBe("org:org");
    expect(tree[0].children?.[0].key).toBe("source:source");
    expect(tree[0].children?.[0].children?.map((row) => row.key)).toEqual([
      "tables:source",
      "views:source",
    ]);
    await workspace.readPage("shared_table", 1, 20, "", [], null);
    const before = request.mock.calls.length;
    const result = await workspace.readPage(
      "shared_table",
      1,
      100,
      "",
      [],
      null,
    );
    expect(request.mock.calls.length - before).toBe(1);
    expect(result.total).toBe(120);
    expect(result.table.rows).toHaveLength(100);
    expect(request.mock.calls.at(-1)?.[2]).toMatchObject({
      tableId: "table",
      pageNo: 1,
      pageSize: 100,
    });
  });
});
