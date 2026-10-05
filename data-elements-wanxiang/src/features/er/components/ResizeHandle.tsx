import { useEffect, useRef } from 'react';
import { clampPanelWidth } from '../core/preferences';
export function ResizeHandle({ side, width, onChange }: { side: 'library' | 'inspector'; width: number; onChange: (width: number) => void }) {
  const start = useRef<{ x: number; width: number } | null>(null);
  const restore = () => { document.body.style.removeProperty('cursor'); document.body.style.removeProperty('user-select'); start.current = null; };
  useEffect(() => restore, []);
  return <div className={`resize-handle resize-${side}`} role="separator" aria-label={side === 'library' ? '调整对象库宽度' : '调整详情面板宽度'}
    aria-orientation="vertical" aria-valuenow={width} aria-valuemin={side === 'library' ? 240 : 380} aria-valuemax={side === 'library' ? 380 : 620} tabIndex={0}
    title="拖动调整宽度，双击恢复默认；支持方向键"
    onPointerDown={e => { if (e.button !== 0) return; e.preventDefault(); start.current = { x: e.clientX, width }; e.currentTarget.setPointerCapture(e.pointerId); document.body.style.cursor = 'col-resize'; document.body.style.userSelect = 'none'; }}
    onPointerMove={e => { if (start.current) onChange(clampPanelWidth(start.current.width + (e.clientX - start.current.x) * (side === 'library' ? 1 : -1), side)); }}
    onPointerUp={e => { if (e.currentTarget.hasPointerCapture(e.pointerId)) e.currentTarget.releasePointerCapture(e.pointerId); restore(); }}
    onLostPointerCapture={restore} onPointerCancel={restore}
    onDoubleClick={() => onChange(side === 'library' ? 280 : 440)}
    onKeyDown={e => { if (e.key === 'Home') { e.preventDefault(); onChange(side === 'library' ? 280 : 440); } if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') { e.preventDefault(); onChange(clampPanelWidth(width + (e.key === 'ArrowRight' ? 16 : -16) * (side === 'library' ? 1 : -1), side)); } }}><span /></div>;
}
