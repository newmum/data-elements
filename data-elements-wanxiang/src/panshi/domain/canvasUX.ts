import type { FieldMode } from '../../features/er/core/model';
import type { DesignEntity } from './types';
import type { Relationship, DiagramNode } from '../../features/er/types/domain';
import { visibleFields } from '../../features/er/core/relations';

/** Same interaction defaults as the Wanxiang ER workbench. Only business commands differ. */
export function canvasInteractionProps(panMode: boolean, spaceHeld: boolean) {
  return {
    multiSelectionKeyCode: 'Shift', selectionOnDrag: !panMode && !spaceHeld,
    panOnDrag: panMode || spaceHeld ? true : [1, 2], panActivationKeyCode: 'Space',
    selectionKeyCode: 'Shift', zoomOnDoubleClick: false, snapGrid: [12, 12] as [number, number],
    minZoom: 0.15, maxZoom: 1.8, connectionRadius: 22,
  };
}
export function designVisibleFields(entity: DesignEntity, relations: Relationship[], mode: FieldMode, node?: Pick<DiagramNode, 'collapsed' | 'pinnedFieldIds'>) {
  return visibleFields(entity, relations, node?.collapsed ? 'summary' : mode, node?.pinnedFieldIds ?? []);
}
export function parseDesignHandle(handle: string | null | undefined): string {
  if (!handle?.startsWith('f:')) return '';
  const end = handle.lastIndexOf('::');
  return end > 2 ? handle.slice(2, end) : '';
}
export function applySelectionChanges(previous: string[], changes: Array<{ type: string; id: string; selected?: boolean }>): string[] {
  const next = new Set(previous);
  for (const c of changes) if (c.type === 'select') c.selected ? next.add(c.id) : next.delete(c.id);
  return [...next];
}
export function pinDesignField(node: DiagramNode, fieldId: string): DiagramNode {
  return { ...node, collapsed: false, pinnedFieldIds: [fieldId, ...(node.pinnedFieldIds ?? []).filter(id => id !== fieldId)].slice(0, 12) };
}
export interface CanvasViewPreference { x: number; y: number; zoom: number; }
export function validViewport(value: unknown): value is CanvasViewPreference {
  if (!value || typeof value !== 'object') return false;
  const v = value as Partial<CanvasViewPreference>;
  return typeof v.x === 'number' && Number.isFinite(v.x) && typeof v.y === 'number' && Number.isFinite(v.y) && typeof v.zoom === 'number' && Number.isFinite(v.zoom) && v.zoom >= .15 && v.zoom <= 1.8;
}
