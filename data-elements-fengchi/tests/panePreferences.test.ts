import { describe, expect, it, vi } from 'vitest';
import { CONNECTION_PANE_DEFAULT, CONNECTION_PANE_WIDTH_KEY, connectionPaneWidth, readConnectionPaneWidth, rememberConnectionPaneWidth, inspectorPaneWidth, readInspectorPaneWidth, rememberInspectorPaneWidth, INSPECTOR_PANE_WIDTH_KEY } from '../src/panePreferences';

describe('connection pane width preference', () => {
  it.each([null, '', ' ', 'bad', 'NaN', 'Infinity'])('defaults invalid or absent %j to the original default', value => {
    expect(connectionPaneWidth(value)).toBe(CONNECTION_PANE_DEFAULT);
  });
  it('retains the manual choice and clamps only to supported absolute limits', () => {
    expect(connectionPaneWidth('240')).toBe(240);
    expect(connectionPaneWidth('180.5')).toBe(180.5);
    expect(connectionPaneWidth('120')).toBe(160);
    expect(connectionPaneWidth('900')).toBe(420);
  });
  it('saves and restores a manual width without replacing it with the default', () => {
    const values = new Map<string, string>();
    const storage = { getItem: (key: string) => values.get(key) ?? null, setItem: (key: string, value: string) => { values.set(key, value); } };
    expect(readConnectionPaneWidth(storage)).toBe(220);
    expect(rememberConnectionPaneWidth(230, storage)).toBe(230);
    expect(values.get(CONNECTION_PANE_WIDTH_KEY)).toBe('230');
    expect(readConnectionPaneWidth(storage)).toBe(230);
    expect(rememberConnectionPaneWidth(160, storage)).toBe(160);
    expect(readConnectionPaneWidth(storage)).toBe(160);
  });
  it('keeps local interaction usable if browser persistence is unavailable', () => {
    expect(readConnectionPaneWidth({ getItem: () => { throw new Error('blocked'); } })).toBe(220);
    const setItem = vi.fn(() => { throw new Error('full'); });
    expect(rememberConnectionPaneWidth(250, { setItem })).toBe(250);
    expect(setItem).toHaveBeenCalledWith(CONNECTION_PANE_WIDTH_KEY, '250');
  });
});


describe('corrected sidebar defaults', () => {
  it('migrates the transient left minimum without discarding other manual choices', () => {
    expect(readConnectionPaneWidth({ getItem: key => key.endsWith('-v1') ? '160' : null })).toBe(220);
    expect(readConnectionPaneWidth({ getItem: key => key.endsWith('-v1') ? '275' : null })).toBe(275);
    expect(readConnectionPaneWidth({ getItem: key => key === CONNECTION_PANE_WIDTH_KEY ? '160' : null })).toBe(160);
  });
  it.each([null, '', 'NaN', 'bad', 'Infinity'])('uses minimum right width for missing or invalid %j', value => {
    expect(inspectorPaneWidth(value)).toBe(180);
  });
  it('preserves, clamps and independently persists right widths', () => {
    expect(inspectorPaneWidth('260')).toBe(260);
    expect(inspectorPaneWidth('120')).toBe(180);
    expect(inspectorPaneWidth('900')).toBe(420);
    const values = new Map<string, string>();
    const storage = { getItem: (key: string) => values.get(key) ?? null, setItem: (key: string, value: string) => { values.set(key, value); } };
    expect(readInspectorPaneWidth(storage)).toBe(180);
    expect(rememberInspectorPaneWidth(250, storage)).toBe(250);
    expect(values.get(INSPECTOR_PANE_WIDTH_KEY)).toBe('250');
    expect(readInspectorPaneWidth(storage)).toBe(250);
    expect(readConnectionPaneWidth(storage)).toBe(220);
  });
  it('keeps either pane usable without persistence', () => {
    expect(readInspectorPaneWidth({ getItem: () => { throw new Error('blocked'); } })).toBe(180);
    expect(rememberInspectorPaneWidth(250, { setItem: () => { throw new Error('blocked'); } })).toBe(250);
  });
});
