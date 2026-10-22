import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { useEditorStore } from './editorStore';
import { createEmptyFlow } from '@/domain/flow-dsl';

function reset() {
  const flow = createEmptyFlow('test');
  useEditorStore.getState().setFlow(flow, 1);
  useEditorStore.temporal.getState().clear();
  return flow;
}

describe('editorStore', () => {
  beforeEach(() => reset());
  afterEach(() => {
    vi.useRealTimers();
  });

  it('addNode appends a node with registry defaults', () => {
    const before = useEditorStore.getState().flow!.nodes.length;
    const id = useEditorStore.getState().addNode('sql-query', { x: 100, y: 100 });
    expect(id).toBeTruthy();
    const flow = useEditorStore.getState().flow!;
    expect(flow.nodes.length).toBe(before + 1);
    const node = flow.nodes.find((n) => n.id === id)!;
    expect(node.type).toBe('sql-query');
    expect(node.data.outputVar).toBe('result');
  });

  it('rejects a second trigger (singleton)', () => {
    const id = useEditorStore.getState().addNode('trigger', { x: 0, y: 0 });
    expect(id).toBeNull();
  });

  it('updateNodeData merges data', () => {
    const id = useEditorStore.getState().addNode('sql-query', { x: 0, y: 0 })!;
    useEditorStore.getState().updateNodeData(id, { sql: 'SELECT 1' });
    const node = useEditorStore.getState().flow!.nodes.find((n) => n.id === id)!;
    expect(node.data.sql).toBe('SELECT 1');
    expect(node.data.outputVar).toBe('result'); // untouched
  });

  it('deleteSelection removes nodes and incident edges', () => {
    const id = useEditorStore.getState().addNode('sql-query', { x: 0, y: 0 })!;
    const trigger = useEditorStore.getState().flow!.nodes.find((n) => n.type === 'trigger')!;
    useEditorStore.getState().addEdge({ source: trigger.id, target: id });
    useEditorStore.getState().setSelection([id], []);
    useEditorStore.getState().deleteSelection();
    const flow = useEditorStore.getState().flow!;
    expect(flow.nodes.find((n) => n.id === id)).toBeUndefined();
    expect(flow.edges.some((e) => e.target === id)).toBe(false);
  });

  it('undo reverts and redo reapplies addNode', () => {
    const before = useEditorStore.getState().flow!.nodes.length;
    useEditorStore.getState().addNode('filter', { x: 0, y: 0 });
    expect(useEditorStore.getState().flow!.nodes.length).toBe(before + 1);
    useEditorStore.getState().undo();
    expect(useEditorStore.getState().flow!.nodes.length).toBe(before);
    useEditorStore.getState().redo();
    expect(useEditorStore.getState().flow!.nodes.length).toBe(before + 1);
  });

  it('dirty flips on mutation and clears on markSaved', () => {
    expect(useEditorStore.getState().dirty).toBe(false);
    useEditorStore.getState().addNode('log', { x: 0, y: 0 });
    expect(useEditorStore.getState().dirty).toBe(true);
    useEditorStore.getState().markSaved(2);
    expect(useEditorStore.getState().dirty).toBe(false);
    expect(useEditorStore.getState().lockVersion).toBe(2);
  });

  it('recomputes problems on mutation', () => {
    vi.useFakeTimers();
    // adding an unconnected sql-query with empty config yields errors
    useEditorStore.getState().addNode('sql-query', { x: 0, y: 0 });
    vi.advanceTimersByTime(180);
    const problems = useEditorStore.getState().problems;
    expect(problems.some((p) => p.severity === 'error')).toBe(true);
  });
});
