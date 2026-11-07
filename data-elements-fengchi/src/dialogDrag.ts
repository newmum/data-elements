export interface DialogPosition {
  x: number;
  y: number;
}

export interface DialogRectangle {
  left: number;
  top: number;
  width: number;
  height: number;
}

export interface DialogDragBounds {
  minX: number;
  maxX: number;
  minY: number;
  maxY: number;
}

/** Convert the currently translated rectangle to limits for an absolute offset. */
export function getDialogDragBounds(
  rectangle: DialogRectangle,
  position: DialogPosition,
  viewport: DialogRectangle,
  margin = 8,
): DialogDragBounds {
  const minX = viewport.left + margin - rectangle.left + position.x;
  const minY = viewport.top + margin - rectangle.top + position.y;
  const maxX = viewport.left + viewport.width - margin - rectangle.left - rectangle.width + position.x;
  const maxY = viewport.top + viewport.height - margin - rectangle.top - rectangle.height + position.y;

  // An oversized custom dialog cannot fit. Keep its title and leading edge reachable.
  return { minX, maxX: Math.max(minX, maxX), minY, maxY: Math.max(minY, maxY) };
}

export function clampDialogPosition(position: DialogPosition, bounds: DialogDragBounds): DialogPosition {
  return {
    x: Math.max(bounds.minX, Math.min(bounds.maxX, position.x)),
    y: Math.max(bounds.minY, Math.min(bounds.maxY, position.y)),
  };
}
