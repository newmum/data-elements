import { isolateHistory } from '@codemirror/commands';
import type { EditorState, TransactionSpec } from '@codemirror/state';

/** Both chip insertion and generation are single, independent undo steps. */
export function insertSqlField(state: EditorState, identifier: string): TransactionSpec {
  const { from, to } = state.selection.main;
  const before = state.sliceDoc(0, from);
  const after = state.sliceDoc(to);
  const prefix = /[\w\u4e00-\u9fa5`"\])]$/.test(before) ? ' ' : '';
  const existingComma = /^\s*,\s*/.exec(after);
  const insert = `${prefix}${identifier}, `;
  return {
    changes: { from, to: to + (existingComma?.[0].length ?? 0), insert },
    selection: { anchor: from + insert.length },
    annotations: isolateHistory.of('full'),
    userEvent: 'input',
    scrollIntoView: true,
  };
}

export function replaceSqlStatement(state: EditorState, sql: string): TransactionSpec {
  return {
    changes: { from: 0, to: state.doc.length, insert: sql },
    selection: { anchor: 0 },
    annotations: isolateHistory.of('full'),
    userEvent: 'input',
    scrollIntoView: true,
  };
}
