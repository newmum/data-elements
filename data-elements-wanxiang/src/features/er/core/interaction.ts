/** The workspace modal is a canvas container, not a blocking editor/dialog. */
export const BLOCKING_LAYER_SELECTOR = [
  '.ant-modal-wrap:not(.canvas-workspace-wrap)',
  '.ant-drawer:not(.ant-drawer-hidden)',
  '.ant-select-dropdown:not(.ant-select-dropdown-hidden)',
  '.ant-dropdown:not(.ant-dropdown-hidden)',
  '.ant-popover:not(.ant-popover-hidden)',
  '.ant-picker-dropdown:not(.ant-picker-dropdown-hidden)',
].join(',');
export function isEditingTarget(target: EventTarget | null): boolean {
  return !!(target && typeof (target as Element).closest === 'function' &&
    (target as Element).closest('input,textarea,select,[contenteditable="true"],[role="combobox"]'));
}
export function hasBlockingLayer(root: ParentNode = document): boolean {
  return Array.from(root.querySelectorAll<HTMLElement>(BLOCKING_LAYER_SELECTOR)).some(element =>
    element.getClientRects().length > 0 && getComputedStyle(element).visibility !== 'hidden');
}
export interface EscapeContext {
  blocking: boolean;
  editing: boolean;
  panel: boolean;
  focused: boolean;
  presentation: boolean;
}
export function escapeAction(context: EscapeContext): 'ignore' | 'panel' | 'focus' | 'presentation' | 'close' {
  if (context.blocking || context.editing) return 'ignore';
  if (context.presentation) return 'presentation';
  if (context.panel) return 'panel';
  if (context.focused) return 'focus';
  return 'close';
}
