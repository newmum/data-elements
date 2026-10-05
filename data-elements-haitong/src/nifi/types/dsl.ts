// Canvas DSL — frontend-only model that the backend will compile to NiFi.
// A pipeline is a directed graph of "nodes" (one per dropped component)
// connected by "edges" (one per X6 line), each carrying a chosen outlet.

import type { ComponentCategory } from './manifest';

export interface CanvasNode {
  /** Stable client-side id (nanoid). Used as X6 node id. */
  id: string;
  /** manifest.key, e.g. "source.mysql" */
  manifestKey: string;
  /** Display label (defaults to manifest.label, user-editable). */
  label: string;
  category: ComponentCategory;
  /** Canvas position. */
  x: number;
  y: number;
  /** User-entered field values keyed by FieldSchema.key. */
  config: Record<string, unknown>;
}

export interface CanvasEdge {
  id: string;
  source: string;        // CanvasNode.id
  target: string;        // CanvasNode.id
  /** outlet name on the source component, e.g. "success" / "matched" / "unmatched". */
  outlet?: string;
}

export interface CanvasDsl {
  /** schema version — bump if the shape ever changes. */
  version: 1;
  nodes: CanvasNode[];
  edges: CanvasEdge[];
}

/**
 * A saved canvas can predate the DSL field, or a template endpoint can return
 * an empty body while a task is being created.  Treat that state as a blank
 * canvas instead of allowing callers to dereference `undefined.nodes`.
 *
 * Keep this normalization at the model boundary so opening historical tasks,
 * creating a new task, and restoring a saved task all follow the same rule.
 */
export const EMPTY_CANVAS_DSL: CanvasDsl = Object.freeze({
  version: 1,
  nodes: [],
  edges: [],
});

// Keep the initial three-step flow visually readable at the canvas's native
// node size (360 × 220).  The horizontal gap leaves room for ports and the
// connection label, while the vertical step leaves room for a second branch.
const INITIAL_NODE_WIDTH = 360;
const INITIAL_NODE_HEIGHT = 220;
const INITIAL_NODE_GAP_X = 180;
const INITIAL_NODE_GAP_Y = 80;
const INITIAL_NODE_START_X = 0;
const INITIAL_NODE_START_Y = 120;

const INITIAL_CATEGORY_ORDER: Record<string, number> = {
  source: 0,
  transform: 1,
  branch: 1,
  sink: 2,
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

export function normalizeCanvasDsl(value: unknown): CanvasDsl {
  // Older saved templates may serialize DSL as JSON, and older node records
  // do not necessarily carry every field required by the newest TypeScript
  // model.  Preserve those valid records exactly as returned; only the graph
  // container itself is normalized here.
  let candidate = value;
  if (typeof candidate === 'string') {
    try {
      candidate = JSON.parse(candidate);
    } catch {
      return { ...EMPTY_CANVAS_DSL, nodes: [], edges: [] };
    }
  }
  if (!isRecord(candidate)) return { ...EMPTY_CANVAS_DSL, nodes: [], edges: [] };

  return {
    version: 1,
    nodes: Array.isArray(candidate.nodes) ? candidate.nodes as CanvasNode[] : [],
    edges: Array.isArray(candidate.edges) ? candidate.edges as CanvasEdge[] : [],
  };
}

/**
 * Templates are returned before a pipeline has been saved.  Some historical
 * template responses contain identical or otherwise intersecting coordinates,
 * which makes the source, mapping and sink nodes look like a single node on
 * their first render.  Arrange only those colliding templates; do not alter a
 * valid saved layout or a user's manually moved nodes.
 */
export function arrangeInitialPipelineLayout(dsl: CanvasDsl): CanvasDsl {
  const nodes = dsl.nodes;
  if (nodes.length < 2 || !hasNodeCollision(nodes)) return dsl;

  const orderById = new Map(nodes.map((node, index) => [node.id, index]));
  const incoming = new Map(nodes.map((node) => [node.id, 0]));
  const outgoing = new Map(nodes.map((node) => [node.id, [] as string[]]));
  dsl.edges.forEach((edge) => {
    if (!incoming.has(edge.source) || !incoming.has(edge.target)) return;
    incoming.set(edge.target, (incoming.get(edge.target) ?? 0) + 1);
    outgoing.get(edge.source)?.push(edge.target);
  });

  const compareNodes = (left: CanvasNode, right: CanvasNode) =>
    (INITIAL_CATEGORY_ORDER[left.category] ?? 9) - (INITIAL_CATEGORY_ORDER[right.category] ?? 9)
    || (orderById.get(left.id) ?? 0) - (orderById.get(right.id) ?? 0);
  const byId = new Map(nodes.map((node) => [node.id, node]));
  const queue = nodes.filter((node) => incoming.get(node.id) === 0).sort(compareNodes);
  const ordered: CanvasNode[] = [];

  while (queue.length) {
    const node = queue.shift();
    if (!node) break;
    ordered.push(node);
    outgoing.get(node.id)?.forEach((targetId) => {
      const next = (incoming.get(targetId) ?? 1) - 1;
      incoming.set(targetId, next);
      if (next === 0) {
        const target = byId.get(targetId);
        if (target) {
          queue.push(target);
          queue.sort(compareNodes);
        }
      }
    });
  }

  // Cyclic or disconnected nodes still need a deterministic, non-overlapping
  // position.  Keep their original order after the reachable flow.
  nodes.forEach((node) => {
    if (!ordered.some((item) => item.id === node.id)) ordered.push(node);
  });

  const laneByStage = new Map<number, number>();
  const stageById = new Map<string, number>();
  ordered.forEach((node) => {
    const upstream = dsl.edges
      .filter((edge) => edge.target === node.id)
      .map((edge) => stageById.get(edge.source) ?? -1);
    const inheritedStage = upstream.length ? Math.max(...upstream) + 1 : undefined;
    // Connected nodes follow their actual flow order.  The category only
    // supplies a sensible fallback for disconnected template records.
    stageById.set(node.id, inheritedStage ?? INITIAL_CATEGORY_ORDER[node.category] ?? 0);
  });

  const positioned = new Map<string, CanvasNode>();
  ordered.forEach((node) => {
    const stage = stageById.get(node.id) ?? 0;
    const lane = laneByStage.get(stage) ?? 0;
    laneByStage.set(stage, lane + 1);
    positioned.set(node.id, {
      ...node,
      x: INITIAL_NODE_START_X + stage * (INITIAL_NODE_WIDTH + INITIAL_NODE_GAP_X),
      y: INITIAL_NODE_START_Y + lane * (INITIAL_NODE_HEIGHT + INITIAL_NODE_GAP_Y),
    });
  });

  return { ...dsl, nodes: nodes.map((node) => positioned.get(node.id) ?? node) };
}

/**
 * The data-access task generator used to persist its first canvas at
 * `-160 → 180 → 520`.  Cards are 360px wide, so those positions overlap by
 * 20px even though they look like three separate columns in the raw DSL.
 *
 * This is deliberately narrower than the generic template repair above:
 * saved canvases normally belong to the user and must not be rearranged merely
 * because two components happen to intersect.  Only the exact, known generated
 * shape is safe to repair when an existing task is opened.
 */
export function arrangeLegacyGeneratedPipelineLayout(dsl: CanvasDsl): CanvasDsl {
  const source = dsl.nodes.find((node) => node.category === 'source');
  const transform = dsl.nodes.find((node) => node.category === 'transform' || node.category === 'branch');
  const sinks = dsl.nodes.filter((node) => node.category === 'sink');

  const isLegacySource = source?.x === -160 && source.y === 120;
  const isLegacyTransform = transform?.x === 180 && transform.y === 120;
  const isLegacySinks = sinks.length > 0 && sinks.every((node, index) =>
    node.x === 520 && node.y === 120 + index * 180,
  );

  return isLegacySource && isLegacyTransform && isLegacySinks
    ? arrangeInitialPipelineLayout(dsl)
    : dsl;
}

function hasNodeCollision(nodes: CanvasNode[]): boolean {
  return nodes.some((node, index) => {
    if (!Number.isFinite(node.x) || !Number.isFinite(node.y)) return true;
    return nodes.slice(index + 1).some((other) =>
      !Number.isFinite(other.x)
      || !Number.isFinite(other.y)
      || (node.x < other.x + INITIAL_NODE_WIDTH
        && node.x + INITIAL_NODE_WIDTH > other.x
        && node.y < other.y + INITIAL_NODE_HEIGHT
        && node.y + INITIAL_NODE_HEIGHT > other.y),
    );
  });
}

export type CanvasMode = 'EDIT' | 'MONITOR';
