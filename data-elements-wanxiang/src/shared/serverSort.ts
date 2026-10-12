import { useState } from 'react';
import type { SorterResult, SortOrder } from 'antd/es/table/interface';
export type ServerSort = { sortField?: string; sortDir?: 'asc' | 'desc' };
export function useServerSort(resetPage: () => void) {
  const [sort, setSort] = useState<ServerSort>({});
  return { sort, column: (key: string) => ({ key, sorter: true, sortOrder: (sort.sortField === key ? (sort.sortDir === 'asc' ? 'ascend' : 'descend') : null) as SortOrder }), change: (sorter: SorterResult<any> | SorterResult<any>[]) => {
    const selected = Array.isArray(sorter) ? sorter[0] : sorter;
    setSort(selected.order ? { sortField: String(selected.columnKey ?? selected.field), sortDir: selected.order === 'ascend' ? 'asc' : 'desc' } : {});
    resetPage();
  } };
}
