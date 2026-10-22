import type { Flow, FlowNode } from '@/domain/flow-dsl';
import {
  buildAdjacency,
  reachableFrom,
  successors,
  topoSort,
  type Adjacency,
} from '@/domain/graph-utils';
import { CodeBuffer } from './code-buffer';
import { getGenerator } from './generators';

/**
 * Holds all state for one compilation pass and exposes the emit helpers that
 * node generators use (sequential emit + control-flow branch emit).
 */
export class CompileContext {
  readonly flow: Flow;
  readonly buf: CodeBuffer;
  readonly adj: Adjacency;
  readonly order: string[];
  readonly emitted = new Set<string>();
  readonly vars = new Set<string>();

  constructor(flow: Flow) {
    this.flow = flow;
    this.buf = new CodeBuffer();
    this.adj = buildAdjacency(flow);
    this.order = topoSort(flow).order;
  }

  node(id: string): FlowNode | undefined {
    return this.adj.nodeById.get(id);
  }

  /** Record an output variable so downstream completion / inference can use it. */
  declareVar(name?: string): void {
    if (name) this.vars.add(name);
  }

  /** Emit one node (delegating to its generator). Skips trigger/note/emitted. */
  emitNode(id: string): void {
    if (this.emitted.has(id)) return;
    const node = this.node(id);
    if (!node) return;
    if (node.type === 'trigger' || node.type === 'note') {
      this.emitted.add(id);
      return;
    }
    this.emitted.add(id);
    const gen = getGenerator(node.type);
    gen(node, this);
  }

  /** Emit a list of node ids in topological order. */
  emitSequence(ids: string[]): void {
    const set = new Set(ids);
    for (const id of this.order) {
      if (set.has(id)) this.emitNode(id);
    }
  }

  /**
   * Compute the nodes that belong exclusively to one branch (out port) of a
   * control node, i.e. reachable from this port but not from its sibling ports.
   * Shared/convergence nodes are excluded (emitted after the control structure).
   */
  branchNodes(controlId: string, port: string): string[] {
    const controlNode = this.node(controlId);
    if (!controlNode) return [];
    const reachByPort = reachableFrom(this.flow, successors(this.adj, controlId, port));
    const otherPorts = this.outPorts(controlId).filter((p) => p !== port);
    const reachByOthers = new Set<string>();
    for (const op of otherPorts) {
      for (const n of reachableFrom(this.flow, successors(this.adj, controlId, op))) {
        reachByOthers.add(n);
      }
    }
    const own = [...reachByPort].filter((id) => id !== controlId && !reachByOthers.has(id));
    const ownSet = new Set(own);
    return this.order.filter((id) => ownSet.has(id));
  }

  /** Emit a branch's subgraph (used by control-flow generators). */
  emitBranch(controlId: string, port: string): void {
    this.emitSequence(this.branchNodes(controlId, port));
  }

  /** All nodes reachable forward from the given start ids (inclusive). */
  reach(ids: string[]): Set<string> {
    return reachableFrom(this.flow, ids);
  }

  /**
   * Nodes exclusively downstream of one successor (used by `parallel`, whose
   * concurrent branches all share the same out port). Excludes nodes also
   * reachable from sibling successors.
   */
  exclusiveSuccessorNodes(successorId: string, siblings: string[]): string[] {
    const mine = this.reach([successorId]);
    const others = new Set<string>();
    for (const sib of siblings) {
      if (sib === successorId) continue;
      for (const id of this.reach([sib])) others.add(id);
    }
    const own = new Set([...mine].filter((id) => !others.has(id)));
    return this.order.filter((id) => own.has(id));
  }

  /** Distinct source ports actually used on a node's outgoing edges. */
  outPorts(nodeId: string): string[] {
    const ports = new Set<string>();
    for (const e of this.adj.out.get(nodeId) ?? []) ports.add(e.sourcePort);
    return [...ports];
  }

  /** Direct successors of a node via a given port. */
  portSuccessors(nodeId: string, port: string): string[] {
    return successors(this.adj, nodeId, port);
  }

  toString(): string {
    return this.buf.toString();
  }
}
