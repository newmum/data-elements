import { describe, expect, it } from 'vitest';
import { EditorState } from '@codemirror/state';
import { history, undo } from '@codemirror/commands';
import { blankRowTransaction, viewportRowCount } from '../src/sqlEditorPadding';

describe('visible SQL blank rows', () => {
  it('fills all visible rows while bounding malformed or enormous viewports', () => {
    expect(viewportRowCount(240, 20, 12)).toBe(12);
    expect(viewportRowCount(100000, 1)).toBe(200);
    expect(viewportRowCount(0, 20)).toBe(1);
    expect(viewportRowCount(100, 0)).toBe(1);
    expect(viewportRowCount(Infinity, 20)).toBe(1);
  });

  it('appends real blank lines without changing SQL, user newlines, or selection', () => {
    const source = 'SELECT *\nFROM users;\n\n';
    let state = EditorState.create({ doc: source, selection: { anchor: 7, head: 12 } });
    state = state.update(blankRowTransaction(state, 12)!).state;
    expect(state.doc.lines).toBe(12);
    expect(state.doc.toString().startsWith(source)).toBe(true);
    expect(state.doc.toString().trim()).toBe(source.trim());
    expect(state.selection.main.anchor).toBe(7);
    expect(state.selection.main.head).toBe(12);
    expect(blankRowTransaction(state, 12)).toBeNull();
    expect(blankRowTransaction(state, 5)).toBeNull();
  });

  it('retains EOF caret and allows editing any of the additional lines', () => {
    let state = EditorState.create({ doc: 'SELECT 1;', selection: { anchor: 9 } });
    state = state.update(blankRowTransaction(state, 20)!).state;
    expect(state.selection.main.head).toBe(9);
    const line = state.doc.line(15);
    state = state.update({ changes: { from: line.from, insert: 'SELECT 2;' } }).state;
    expect(state.doc.line(15).text).toBe('SELECT 2;');
    expect(blankRowTransaction(state, 20)).toBeNull();
  });

  it('keeps automatic padding out of undo history', () => {
    let state = EditorState.create({ doc: 'SELECT ', extensions: [history()] });
    state = state.update({ changes: { from: 7, insert: '1;' } }).state;
    state = state.update(blankRowTransaction(state, 12)!).state;
    expect(undo({ state, dispatch: transaction => { state = transaction.state; } })).toBe(true);
    expect(state.doc.toString()).toBe('SELECT ' + '\n'.repeat(11));
    expect(undo({ state, dispatch: transaction => { state = transaction.state; } })).toBe(false);
  });

  it('never trims existing long documents and does not repeatedly inflate padding', () => {
    let state = EditorState.create({ doc: 'SELECT 1;' });
    for (let i = 0; i < 20; i++) {
      const transaction = blankRowTransaction(state, 18);
      if (transaction) state = state.update(transaction).state;
    }
    expect(state.doc.lines).toBe(18);
    const long = EditorState.create({ doc: '\n'.repeat(300) });
    expect(blankRowTransaction(long, 200)).toBeNull();
  });
});
