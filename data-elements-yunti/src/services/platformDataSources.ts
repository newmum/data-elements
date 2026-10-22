import { useQuery } from "@tanstack/react-query";
import { platformRequest } from "./client";

export interface PlatformDataSource {
  id: string;
  name: string;
  type: string;
  appName: string;
  connectionStatus: string;
  showConnect: boolean;
}

interface PlatformDataSourceRow {
  tid?: string;
  id?: string;
  dbName?: string;
  name?: string;
  dbType?: string;
  type?: string;
  appName?: string;
  connectionStatus?: string;
  showConnect?: number | boolean;
}

const platformDataSourceKeys = {
  all: ["platform-datasources"] as const,
};

export function usePlatformDataSources(enabled = true) {
  return useQuery({
    queryKey: platformDataSourceKeys.all,
    enabled,
    queryFn: async () => {
      const rows = await platformRequest<PlatformDataSourceRow[]>(
        "POST",
        "/database/list",
        {},
      );
      return rows
        .map(
          (row): PlatformDataSource => ({
            id: String(row.tid ?? row.id ?? ""),
            name: String(row.dbName ?? row.name ?? ""),
            type: String(row.dbType ?? row.type ?? "other").toLowerCase(),
            appName: String(row.appName ?? ""),
            connectionStatus: String(row.connectionStatus ?? "unknown"),
            showConnect: row.showConnect !== 0 && row.showConnect !== false,
          }),
        )
        .filter((row) => row.id && row.name && row.showConnect);
    },
    staleTime: 30_000,
  });
}
