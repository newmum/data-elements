import { useLayoutEffect, useRef, type ReactNode, type RefObject } from 'react';
import { Modal as AntModal, type ModalProps } from 'antd';
import { clampDialogPosition, getDialogDragBounds, type DialogDragBounds, type DialogPosition } from './dialogDrag';

interface DragController {
  reset: () => void;
  clamp: () => void;
}

interface DragSession {
  pointerId: number;
  clientX: number;
  clientY: number;
  origin: DialogPosition;
  bounds: DialogDragBounds;
}

const interactiveSelector = 'button,input,select,textarea,a,label,[role="button"],[role="link"],[contenteditable]:not([contenteditable="false"]),[tabindex]:not([tabindex="-1"]),[data-dialog-no-drag],[draggable="true"]';

function DraggableDialog({ children, controllerRef, prefixCls = 'ant-modal' }: {
  children: ReactNode;
  controllerRef?: RefObject<DragController | null>;
  prefixCls?: string;
}) {
  const frameRef = useRef<HTMLDivElement>(null);

  useLayoutEffect(() => {
    const frame = frameRef.current;
    if (!frame) return;
    const dialog = frame.closest<HTMLElement>('[role="dialog"]');
    const wrap = frame.closest<HTMLElement>(`.${prefixCls}-wrap`);
    const titleSelector = `.${prefixCls}-header,.${prefixCls}-confirm-title,[data-dialog-drag-handle]`;
    let position: DialogPosition = { x: 0, y: 0 };
    let drag: DragSession | null = null;
    let animationFrame = 0;
    let gestureStartedInside = false;

    function rememberGesture(event: PointerEvent) {
      if (!event.isPrimary || event.button !== 0) return;
      gestureStartedInside = event.target instanceof Node && frame!.contains(event.target);
    }

    function clearGesture() {
      gestureStartedInside = false;
    }

    function guardMaskClick(event: MouseEvent) {
      const startedInside = gestureStartedInside;
      clearGesture();
      // A drag released on the mask can generate a click on the common ancestor.
      // Block only that mask click, never clicks on controls or keyboard clicks.
      // A fresh pointerdown replaces the origin, so the next outside click works
      // immediately, without a timeout that could swallow a legitimate dismissal.
      if (startedInside && event.detail > 0 && event.target === wrap) {
        event.stopPropagation();
      }
    }

    function viewport() {
      const visual = window.visualViewport;
      return {
        left: visual?.offsetLeft ?? 0,
        top: visual?.offsetTop ?? 0,
        width: visual?.width ?? document.documentElement.clientWidth,
        height: visual?.height ?? document.documentElement.clientHeight,
      };
    }

    function applyPosition(next: DialogPosition) {
      position = next;
      // Keep pointer movement local, without re-rendering the form or editor inside.
      frame!.style.transform = `translate3d(${next.x}px, ${next.y}px, 0)`;
    }

    function stopDragging() {
      const pointerId = drag?.pointerId;
      drag = null;
      frame!.removeAttribute('data-dragging');
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', finish);
      window.removeEventListener('pointercancel', finish);
      window.removeEventListener('blur', stopDragging);
      if (pointerId !== undefined && frame!.hasPointerCapture?.(pointerId)) {
        frame!.releasePointerCapture(pointerId);
      }
    }

    function clamp() {
      // Enter animations temporarily scale the ancestor; wait for its real geometry.
      if (dialog?.getAnimations?.().some(animation => animation.playState === 'running')) return;
      const rectangle = frame!.getBoundingClientRect();
      if (!rectangle.width || !rectangle.height) return;
      applyPosition(clampDialogPosition(position, getDialogDragBounds(rectangle, position, viewport())));
    }

    function scheduleClamp() {
      stopDragging();
      frame!.style.setProperty('--dialog-viewport-height', `${viewport().height}px`);
      cancelAnimationFrame(animationFrame);
      animationFrame = requestAnimationFrame(clamp);
    }

    function reset() {
      stopDragging();
      clearGesture();
      cancelAnimationFrame(animationFrame);
      applyPosition({ x: 0, y: 0 });
    }

    function onMotionEnd(event: Event) {
      if (event.target === dialog) scheduleClamp();
    }

    function move(event: PointerEvent) {
      if (!drag || event.pointerId !== drag.pointerId) return;
      if (event.pointerType === 'mouse' && event.buttons === 0) {
        stopDragging();
        return;
      }
      if (event.cancelable) event.preventDefault();
      applyPosition(clampDialogPosition({
        x: drag.origin.x + event.clientX - drag.clientX,
        y: drag.origin.y + event.clientY - drag.clientY,
      }, drag.bounds));
    }

    function finish(event: PointerEvent) {
      if (event.pointerId === drag?.pointerId) stopDragging();
    }

    function start(event: PointerEvent) {
      if (!event.isPrimary || event.button !== 0 || drag) return;
      const target = event.target;
      if (!(target instanceof Element) || !frame!.contains(target)) return;
      const handle = target.closest(titleSelector);
      const interactive = target.closest(interactiveSelector);
      // rc-dialog wraps the entire content in a focus sentinel with tabIndex=0.
      // Only controls inside our frame should disable a title drag.
      if (!handle || !frame!.contains(handle) || (interactive && frame!.contains(interactive))) return;

      const rectangle = frame!.getBoundingClientRect();
      if (!rectangle.width || !rectangle.height) return;
      drag = {
        pointerId: event.pointerId,
        clientX: event.clientX,
        clientY: event.clientY,
        origin: { ...position },
        bounds: getDialogDragBounds(rectangle, position, viewport()),
      };
      frame!.setAttribute('data-dragging', 'true');
      if (event.cancelable) event.preventDefault();
      // Window listeners also cover browsers where pointer capture is unavailable.
      window.addEventListener('pointermove', move, { passive: false });
      window.addEventListener('pointerup', finish);
      window.addEventListener('pointercancel', finish);
      window.addEventListener('blur', stopDragging);
      try { frame!.setPointerCapture?.(event.pointerId); } catch { /* The pointer may already be gone. */ }
    }

    const controller = { reset, clamp: scheduleClamp };
    if (controllerRef) controllerRef.current = controller;
    frame.addEventListener('pointerdown', start);
    frame.addEventListener('lostpointercapture', finish);
    wrap?.addEventListener('pointerdown', rememberGesture, true);
    wrap?.addEventListener('click', guardMaskClick, true);
    window.addEventListener('pointercancel', clearGesture);
    window.addEventListener('blur', clearGesture);
    window.addEventListener('resize', scheduleClamp);
    window.visualViewport?.addEventListener('resize', scheduleClamp);
    window.visualViewport?.addEventListener('scroll', scheduleClamp);
    dialog?.addEventListener('animationend', onMotionEnd);
    dialog?.addEventListener('transitionend', onMotionEnd);
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(scheduleClamp);
    observer?.observe(frame);
    scheduleClamp();

    return () => {
      stopDragging();
      cancelAnimationFrame(animationFrame);
      observer?.disconnect();
      frame.removeEventListener('pointerdown', start);
      frame.removeEventListener('lostpointercapture', finish);
      wrap?.removeEventListener('pointerdown', rememberGesture, true);
      wrap?.removeEventListener('click', guardMaskClick, true);
      window.removeEventListener('pointercancel', clearGesture);
      window.removeEventListener('blur', clearGesture);
      window.removeEventListener('resize', scheduleClamp);
      window.visualViewport?.removeEventListener('resize', scheduleClamp);
      window.visualViewport?.removeEventListener('scroll', scheduleClamp);
      dialog?.removeEventListener('animationend', onMotionEnd);
      dialog?.removeEventListener('transitionend', onMotionEnd);
      if (controllerRef?.current === controller) controllerRef.current = null;
    };
  }, [controllerRef, prefixCls]);

  return <div ref={frameRef} className="draggable-modal-frame">{children}</div>;
}

/** Pass to App.useApp().modal.confirm/info/etc. Each invocation owns its own position. */
export const draggableModalRender: NonNullable<ModalProps['modalRender']> = node => (
  <DraggableDialog>{node}</DraggableDialog>
);

/** Drop-in JSX Modal; existing props, callbacks and custom modalRender are preserved. */
export default function DraggableModal({ modalRender, afterOpenChange, afterClose, ...props }: ModalProps) {
  const controllerRef = useRef<DragController | null>(null);
  const open = props.open ?? props.visible ?? false;

  useLayoutEffect(() => {
    // rc-dialog memoizes its hidden children, so reset from the controlling component.
    controllerRef.current?.reset();
    if (open) controllerRef.current?.clamp();
  }, [open]);

  return <AntModal
    centered
    maskClosable
    {...props}
    modalRender={node => <DraggableDialog controllerRef={controllerRef} prefixCls={props.prefixCls}>
      {modalRender ? modalRender(node) : node}
    </DraggableDialog>}
    afterOpenChange={visible => {
      if (visible) controllerRef.current?.clamp();
      else controllerRef.current?.reset();
      afterOpenChange?.(visible);
    }}
    afterClose={() => {
      controllerRef.current?.reset();
      afterClose?.();
    }}
  />;
}
