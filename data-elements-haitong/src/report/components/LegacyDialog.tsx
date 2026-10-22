import { useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import type { ReactNode } from 'react';
import Icon from './Icon';

/**
 * A small, dependency-free modal layer for report workflows.  Some legacy
 * Chromium installations place a component-library mask above its content,
 * leaving visible controls unable to receive pointer events.
 */
export default function LegacyDialog({ open, onClose, title, footer, children, className = '', ariaLabel }: {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  footer?: ReactNode;
  children: ReactNode;
  className?: string;
  ariaLabel?: string;
}) {
  const closeButton = useRef<HTMLButtonElement | null>(null);
  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKeyDown);
    window.setTimeout(() => closeButton.current?.focus(), 0);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [open, onClose]);
  if (!open) return null;
  return createPortal(<div className="report-dialog-backdrop" role="presentation" onMouseDown={onClose}>
    <section className={`report-dialog ${className}`} role="dialog" aria-modal="true" aria-label={ariaLabel || '提示窗口'} onMouseDown={event => event.stopPropagation()}>
      <header className="report-dialog-header"><div>{title}</div><button ref={closeButton} type="button" className="report-dialog-close" aria-label="关闭窗口" onClick={onClose}><Icon name="close" size={21}/></button></header>
      <div className="report-dialog-body">{children}</div>
      {footer !== undefined && <footer className="report-dialog-footer">{footer}</footer>}
    </section>
  </div>, document.body);
}
