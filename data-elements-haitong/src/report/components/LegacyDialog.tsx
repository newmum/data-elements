import { useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import type { ReactNode } from 'react';
import Icon from './Icon';
import {ConfigProvider} from 'antd';

/**
 * A small, dependency-free modal layer for report workflows.  Some legacy
 * Chromium installations place a component-library mask above its content,
 * leaving visible controls unable to receive pointer events.
 */
export default function LegacyDialog({ open, onClose, title, footer, children, className = '', ariaLabel,busy=false }: {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  footer?: ReactNode;
  children: ReactNode;
  className?: string;
  ariaLabel?: string;
  busy?: boolean;
}) {
  const closeButton = useRef<HTMLButtonElement | null>(null);
  const closeRef=useRef(onClose),busyRef=useRef(busy);closeRef.current=onClose;busyRef.current=busy;
  useEffect(() => {
    if (!open) return;
    const previous=document.activeElement as HTMLElement|null;
    const previousOverflow=document.body.style.overflow;document.body.style.overflow='hidden';
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape'&&!busyRef.current) closeRef.current();
      if(event.key==='Tab'){
        const dialog=closeButton.current?.closest('section');
        const targets=Array.from(dialog?.querySelectorAll<HTMLElement>('button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),[tabindex="0"]')||[]).filter(element=>element.getClientRects().length>0);
        const first=targets[0],last=targets[targets.length-1];
        if(!first){event.preventDefault();dialog?.focus();return;}
        if(!dialog?.contains(document.activeElement)||document.activeElement===dialog){event.preventDefault();(event.shiftKey?last:first)?.focus();return;}
        if(event.shiftKey&&document.activeElement===first){event.preventDefault();last?.focus();}
        else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first?.focus();}
      }
    };
    document.addEventListener('keydown', onKeyDown);
    const timer=window.setTimeout(() => {if(closeButton.current?.disabled)closeButton.current.closest('section')?.focus();else closeButton.current?.focus();}, 0);
    return () => {clearTimeout(timer);document.removeEventListener('keydown', onKeyDown);document.body.style.overflow=previousOverflow;if(previous?.isConnected)previous.focus();};
  }, [open]);
  if (!open) return null;
  return createPortal(<div className="report-dialog-backdrop" role="presentation" onMouseDown={()=>{if(!busy)onClose();}}>
    <ConfigProvider theme={{token:{zIndexPopupBase:11000}}}><section className={`report-dialog ${className}`} role="dialog" tabIndex={-1} aria-modal="true" aria-busy={busy} aria-label={ariaLabel || (typeof title==='string'?title:'提示窗口')} onMouseDown={event => event.stopPropagation()}>
      <header className="report-dialog-header"><div>{title}</div><button ref={closeButton} type="button" className="report-dialog-close" disabled={busy} aria-label="关闭窗口" onClick={onClose}><Icon name="close" size={21}/></button></header>
      <div className="report-dialog-body">{children}</div>
      {footer !== undefined && <footer className="report-dialog-footer">{footer}</footer>}
    </section></ConfigProvider>
  </div>, document.body);
}
