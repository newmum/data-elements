import { normalizeCanvasDsl, type CanvasDsl } from '../types/dsl.ts';
import { sameSavedContent } from './syncSettings.ts';

function comparableDsl(value: unknown, includeLayout: boolean) {
  const dsl = normalizeCanvasDsl(value);
  return {
    version: dsl.version,
    nodes: dsl.nodes.map(node => ({
      id: node.id, manifestKey: node.manifestKey, label: node.label, category: node.category,
      config: node.config ?? {},
      ...(includeLayout ? { x: node.x, y: node.y } : {}),
    })).sort((a, b) => a.id.localeCompare(b.id)),
    edges: dsl.edges.map(edge => ({
      source: edge.source, target: edge.target, outlet: edge.outlet ?? null,
    })).sort((a, b) => a.source.localeCompare(b.source) || a.target.localeCompare(b.target)
      || (a.outlet ?? '').localeCompare(b.outlet ?? '')),
  };
}

/** Matches DslHasher's execution fields. Moving cards does not change a deployment. */
export function sameExecutableDsl(left: unknown, right: unknown): boolean {
  return sameSavedContent(comparableDsl(left, false), comparableDsl(right, false));
}

export function sameDesignDsl(left: unknown, right: unknown): boolean {
  return sameSavedContent(comparableDsl(left, true), comparableDsl(right, true));
}

/** Accept server-side field binding only while the submitted execution design is unchanged. */
export function reconcileDeployedDsl(current: CanvasDsl, submitted: CanvasDsl, deployed: CanvasDsl): CanvasDsl | undefined {
  if (!sameExecutableDsl(current, submitted)) return undefined;
  const positions = new Map(current.nodes.map(node => [node.id, node]));
  const connectionKey = (edge: CanvasDsl['edges'][number]) => JSON.stringify([edge.source, edge.target, edge.outlet ?? null]);
  const connectionIds = new Map(current.edges.map(edge => [connectionKey(edge), edge.id]));
  return {
    ...deployed,
    nodes: deployed.nodes.map(node => {
      const original = positions.get(node.id);
      return original ? { ...node, x: original.x, y: original.y } : node;
    }),
    edges: deployed.edges.map(edge => ({ ...edge, id: connectionIds.get(connectionKey(edge)) ?? edge.id })),
  };
}
