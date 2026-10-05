import { useEffect, useRef, useState } from 'react';
import type { CSSProperties, ReactNode } from 'react';
import { Button, Dropdown, Modal, Tooltip } from 'antd';
import { CloseOutlined, CompressOutlined, ExpandOutlined, MoonOutlined, QuestionCircleOutlined, SaveOutlined, SearchOutlined, ShareAltOutlined, SunOutlined } from '@ant-design/icons';
import { BrandMark } from '../design/BrandMark';
import { activeDiagram, useStudio } from '../features/er/store';
import { STUDIO_ACTION_EVENT } from '../features/er/core/navigation';
import { escapeAction, hasBlockingLayer, isEditingTarget } from '../features/er/core/interaction';
interface Props {
    children: ReactNode;
    onClose: () => void;
    busy?: boolean;
    title?: string;
    kind?: 'er' | 'lineage';
    onSave?: () => void;
}
/** Portal-backed, viewport-sized workspace. No browser fullscreen permission is required. */
export function FullscreenWorkspace({ children, onClose, busy = false, title, kind = 'er', onSave }: Props) {
    const theme = useStudio(s => s.theme);
    const libraryOpen = useStudio(s => s.libraryOpen);
    const libraryWidth = useStudio(s => s.libraryWidth);
    const saveState = useStudio(s => s.saveState);
    const diagram = useStudio(activeDiagram);
    const panel = useStudio(s => s.panel);
    const editor = useStudio(s => s.editor);
    const [presentation, setPresentation] = useState(false);
    const closeRef = useRef(onClose);
    closeRef.current = onClose;
    useEffect(() => {
        if (kind === 'er' && (panel || editor))
            setPresentation(false);
    }, [panel, editor, kind]);
    const action = (name: string) => window.dispatchEvent(new CustomEvent(STUDIO_ACTION_EVENT, { detail: name }));
    useEffect(() => {
        const toggle = (event: Event) => {
            if ((event as CustomEvent<string>).detail === 'presentation')
                setPresentation(value => !value);
        };
        window.addEventListener(STUDIO_ACTION_EVENT, toggle);
        return () => window.removeEventListener(STUDIO_ACTION_EVENT, toggle);
    }, []);
    useEffect(() => {
        const key = (event: KeyboardEvent) => {
            if (event.key !== 'Escape' || event.defaultPrevented || busy)
                return;
            const state = useStudio.getState();
            const next = escapeAction({ blocking: hasBlockingLayer(), editing: isEditingTarget(event.target) || (kind === 'er' && !!state.editor), panel: kind === 'er' && !!state.panel, focused: kind === 'er' && !!state.focusEntityId, presentation });
            if (next === 'ignore')
                return;
            event.preventDefault();
            event.stopImmediatePropagation();
            if (next === 'presentation')
                setPresentation(false);
            if (next === 'panel')
                state.setPanel(null);
            if (next === 'focus')
                state.setFocus(null);
            if (next === 'close')
                closeRef.current();
        };
        // Capture distinguishes child dialogs before their bubbling Esc can close the workspace.
        window.addEventListener('keydown', key, true);
        return () => window.removeEventListener('keydown', key, true);
    }, [presentation, busy, kind]);
    const name = title ?? diagram?.name ?? 'ER 关系图';
    return <Modal open width="100vw" title={<span className="sr-only">{kind === 'er' ? 'ER 全屏工作台' : '血缘全屏工作台'}：{name}</span>} rootClassName="canvas-workspace-overlay" wrapClassName="canvas-workspace-wrap" className="canvas-workspace-modal" footer={null} closable={false} keyboard={false} mask={{ enabled: true, closable: false }} focusable={{ trap: true, focusTriggerAfterClose: true }} destroyOnHidden zIndex={900} transitionName="" maskTransitionName="" onCancel={onClose} style={{ top: 0, margin: 0, paddingBottom: 0, maxWidth: '100vw' }} styles={{ container: { padding: 0, borderRadius: 0 }, header: { margin: 0, padding: 0 }, body: { padding: 0, height: '100dvh', overflow: 'hidden' } }}>
    <section className={`fullscreen-workspace ${presentation ? 'is-presentation' : ''} ${kind === 'er' && libraryOpen && !presentation ? 'has-library' : ''}`} data-testid="fullscreen-workspace" data-kind={kind} style={{ '--er-library-width': `${libraryWidth}px` } as CSSProperties}>
      <header className="fullscreen-workspace-header">
        <div className="fullscreen-brand" aria-hidden="true"><BrandMark small/></div>
        <div className="fullscreen-heading">{kind === 'er' ? <strong>设计工作台</strong> : <><span>数据治理中心 <i>/</i> 数据血缘</span><strong title={name}>{name}</strong></>}</div>
        <span className="fullscreen-spacer"/>
        {kind === 'er' && <div className={`fullscreen-save-state save-${saveState}`} role="status" aria-live="polite"><i />{saveState === 'saved' ? '已保存到当前浏览器' : saveState === 'saving' ? '保存中…' : saveState === 'error' ? '保存失败' : '待保存'}</div>}
        <div className="fullscreen-actions">
          {kind === 'er' && <Dropdown trigger={['click']} menu={{items:[{key:'new-relation',label:'新建逻辑关系'},{key:'discover',label:'发现结构关系'},{key:'relations',label:'查看关系列表'},{type:'divider'},{key:'export',label:'导出结构与关系视图'}],onClick:({key})=>action(key)}}><Button type="text" disabled={!diagram} aria-label="关系工具" icon={<ShareAltOutlined/>}><span className="fullscreen-optional">关系工具</span></Button></Dropdown>}
          {kind === 'er' && <Tooltip title="搜索表、字段和模型（Ctrl / ⌘ + K）"><Button type="text" aria-label="搜索画布内容" icon={<SearchOutlined />} onClick={() => action('search')}/></Tooltip>}
          <Tooltip title={presentation ? '恢复工具与面板' : '隐藏面板，专注展示画布'}><Button type="text" className={presentation ? 'is-active' : ''} aria-label={presentation ? '退出专注模式' : '进入专注模式'} aria-pressed={presentation} icon={presentation ? <CompressOutlined /> : <ExpandOutlined />} onClick={() => setPresentation(value => !value)}><span className="fullscreen-optional">{presentation ? '退出专注' : '专注画布'}</span></Button></Tooltip>
          <Tooltip title={theme === 'light' ? '切换深色' : '切换浅色'}><Button type="text" aria-label={theme === 'light' ? '切换深色主题' : '切换浅色主题'} icon={theme === 'light' ? <MoonOutlined /> : <SunOutlined />} onClick={() => useStudio.getState().setTheme(theme === 'light' ? 'dark' : 'light')}/></Tooltip>
          {kind === 'er' && <Tooltip title="画布使用帮助"><Button type="text" aria-label="画布使用帮助" icon={<QuestionCircleOutlined />} onClick={() => action('help')}/></Tooltip>}
          {(kind === 'er'||onSave) && <Button className="fullscreen-save-button" aria-label={kind==='er'?'保存画布':'保存加工图'} loading={kind === 'er' ? saveState === 'saving':busy} icon={<SaveOutlined />} onClick={() => onSave ? onSave() : void useStudio.getState().save()}>保存</Button>}
          <span className="fullscreen-action-divider"/>
          <Tooltip title="保存后关闭，返回打开前的页面"><Button aria-label="关闭全屏工作台" className="fullscreen-close-button" icon={<CloseOutlined />} loading={busy} onClick={onClose}>关闭</Button></Tooltip>
        </div>
      </header>
      <div className="fullscreen-workspace-content">{children}</div>
      {presentation && <div className="presentation-hint">专注模式 · 按 Esc 恢复工具面板</div>}
    </section>
  </Modal>;
}
