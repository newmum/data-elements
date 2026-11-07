import { describe, expect, it } from 'vitest';
import { clampDialogPosition, getDialogDragBounds } from '../src/dialogDrag';

const viewport = { left: 0, top: 0, width: 1200, height: 800 };
const rectangle = { left: 300, top: 200, width: 600, height: 400 };

describe('dialog drag bounds', () => {
  it('keeps an 8px visible margin on every viewport edge', () => {
    expect(getDialogDragBounds(rectangle, { x: 0, y: 0 }, viewport)).toEqual({ minX: -292, maxX: 292, minY: -192, maxY: 192 });
  });

  it('keeps the same bounds after a previous drag', () => {
    expect(getDialogDragBounds({ ...rectangle, left: 400, top: 250 }, { x: 100, y: 50 }, viewport))
      .toEqual(getDialogDragBounds(rectangle, { x: 0, y: 0 }, viewport));
  });

  it('clamps movement in every direction without changing a valid position', () => {
    const bounds = getDialogDragBounds(rectangle, { x: 0, y: 0 }, viewport);
    expect(clampDialogPosition({ x: -5000, y: -5000 }, bounds)).toEqual({ x: -292, y: -192 });
    expect(clampDialogPosition({ x: 5000, y: 5000 }, bounds)).toEqual({ x: 292, y: 192 });
    expect(clampDialogPosition({ x: 71, y: -50 }, bounds)).toEqual({ x: 71, y: -50 });
  });

  it('brings a moved dialog back inside a smaller viewport', () => {
    const position = { x: 250, y: 150 };
    const bounds = getDialogDragBounds({ ...rectangle, left: 550, top: 350 }, position, { ...viewport, width: 900, height: 600 });
    expect(clampDialogPosition(position, bounds)).toEqual({ x: -8, y: -8 });
  });

  it('accounts for a panned visual viewport', () => {
    expect(getDialogDragBounds(rectangle, { x: 0, y: 0 }, { ...viewport, left: 100, top: 50 }))
      .toEqual({ minX: -192, maxX: 392, minY: -142, maxY: 242 });
  });

  it('keeps the title and leading edge accessible when a custom dialog is oversized', () => {
    const bounds = getDialogDragBounds({ left: -100, top: -100, width: 1600, height: 1000 }, { x: 0, y: 0 }, viewport);
    expect(bounds).toEqual({ minX: 108, maxX: 108, minY: 108, maxY: 108 });
    expect(clampDialogPosition({ x: 400, y: -200 }, bounds)).toEqual({ x: 108, y: 108 });
  });
});
