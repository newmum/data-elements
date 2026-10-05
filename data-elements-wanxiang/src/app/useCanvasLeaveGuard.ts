import { useEffect, useRef } from 'react';
import { App } from 'antd';
import { useBlocker } from 'react-router-dom';
import { isCanvasPath } from './canvasNavigation';
import { useStudio } from '../features/er/store';

/** Covers close button, browser Back, and internal navigation. Reload uses beforeunload. */
export function useCanvasLeaveGuard() {
  const { modal, message } = App.useApp();
  const processing = useRef(false);
  const blocker = useBlocker(({ currentLocation, nextLocation }) => {
    if (!isCanvasPath(currentLocation.pathname) || currentLocation.pathname === nextLocation.pathname) return false;
    const state = useStudio.getState();
    return !!state.editor || state.dragging || ['dirty', 'saving', 'error'].includes(state.saveState);
  });
  useEffect(() => {
    if (blocker.state !== 'blocked' || processing.current) return;
    processing.current = true;
    const finish = async (discardEditor: boolean) => {
      try {
        const studio = useStudio.getState();
        if (studio.dragging) studio.endDrag();
        await studio.save();
        const latest = useStudio.getState();
        if (latest.saveState !== 'saved') throw new Error(latest.saveError ?? '画布仍有待保存修改，请重试。');
        if (discardEditor) latest.openEditor(null);
        blocker.proceed();
      } catch (error) {
        message.error(`尚未退出：${error instanceof Error ? error.message : String(error)}`);
        blocker.reset();
      } finally { processing.current = false; }
    };
    if (useStudio.getState().editor) {
      modal.confirm({ title: '关闭尚未提交的关系表单？', content: '这份表单尚未保存。放弃表单后，将保存当前画布并返回原页面；已经保存的关系不会删除。', okText: '放弃表单并离开', cancelText: '继续编辑', okButtonProps: { danger: true }, mask: { closable: false }, onOk: () => finish(true), onCancel: () => { processing.current = false; blocker.reset(); } });
    } else { void finish(false); }
  }, [blocker, modal, message]);
  return blocker.state === 'blocked' || blocker.state === 'proceeding';
}
