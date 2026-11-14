import type { FlowError, LiveFlowBulletin } from '../api/pipelines';

export interface DismissedFlowErrors {
  historyIds: ReadonlySet<string>;
  bulletinKeys: ReadonlySet<string>;
}

// Match only the observed occurrence. A later occurrence with the same message stays visible.
export function bulletinKey(row: LiveFlowBulletin): string {
  return JSON.stringify([row.id, row.sourceId ?? '', row.timestamp]);
}

export function visibleFlowErrors(rows: readonly FlowError[], dismissed?: DismissedFlowErrors): FlowError[] {
  return rows.filter(row => !dismissed?.historyIds.has(row.id));
}

export function visibleFlowBulletins(rows: readonly LiveFlowBulletin[], dismissed?: DismissedFlowErrors): LiveFlowBulletin[] {
  return rows.filter(row => !dismissed?.bulletinKeys.has(bulletinKey(row)));
}
