export interface DraftOrderItem { id: string; value: unknown }
export function draftOrderRequest(items: DraftOrderItem[], kind: 'text' | 'number' | 'time', direction: 'asc' | 'desc') {
  if (items.length > 1000) throw new Error('字段排序最多支持 1000 条规则，当前仍按执行顺序展示全部规则');
  return { items, kind, direction, wholeCollection: true };
}
export function checkedDraftKeys(items: DraftOrderItem[], result: { keys: string[]; total: number }): string[] {
  const wanted = new Set(items.map(item => item.id));
  if (wanted.size !== items.length || result.total !== wanted.size || result.keys.length !== wanted.size || new Set(result.keys).size !== wanted.size || result.keys.some(id => !wanted.has(id))) {
    throw new Error('排序结果不完整或超出当前草稿范围，已保留原执行顺序');
  }
  return result.keys;
}
