import { platformApi } from './platformApi';

export type NativeFlowAssociation = {
  id: string;
  groupName: string;
  processGroupId: string;
  nodeName: string;
  sourceTableId: string;
  tableName: string;
  tableNameCn?: string;
  datasourceName?: string;
  canUnlink: boolean;
  createdAt: number;
};

export type NativeFlowObservation = {
  groupName: string;
  processGroupId: string;
  nodeName: string;
  runtimeLabel: string;
  runtimeFresh: boolean;
  logs?: { id: string; level: string; source: string; message: string; time: string }[];
};

const path = '/ods/registry/native';
export const nativeFlowAssociations = {
  page: (page: number, size: number, keyword: string, signal: AbortSignal) =>
    platformApi<{ list: NativeFlowAssociation[]; total: number }>(path, { action: 'list', page, size, keyword }, { signal }),
  inspect: (sourceTableId: string, processGroupId: string) =>
    platformApi<NativeFlowObservation>(path, { action: 'inspect', sourceTableId, processGroupId }),
  bind: (sourceTableId: string, processGroupId: string) =>
    platformApi<{ id: string }>(path, { action: 'bind', sourceTableId, processGroupId }),
  detail: (id: string, signal: AbortSignal) =>
    platformApi<NativeFlowObservation>(path, { action: 'detail', id }, { signal }),
  unlink: (id: string) => platformApi<{ id: string }>(path, { action: 'unlink', id }),
};
