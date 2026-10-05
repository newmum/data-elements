import { metadataWritable as canWrite } from '../../services/metadata';
import { createSerialQueue } from './core/saveQueue';
import { requestStudioNavigation } from './core/navigation';
import { create } from 'zustand';
import type { DiagramNode, Position, RelationCreate, Viewport } from './types/domain';
import type { ConnectionDraft, JobProgress, SidePanel, ThemeMode, ViewModel, WorkspaceData } from './core/model';
import { clone, errorText } from './core/model';
import { validateRelation } from './core/relations';
import { workspaceService } from './services/workspaceService';
import { readTheme, readPreferences, savePreferences, clampPanelWidth, THEME_KEY } from './core/preferences';
const initialPreferences = readPreferences();
type SaveState = 'saved' | 'dirty' | 'saving' | 'error';
interface StudioStore {
    data: WorkspaceData | null;
    ready: boolean;
    loadError: string | null;
    theme: ThemeMode;
    page: 'canvas' | 'diagrams' | 'capabilities';
    panel: SidePanel;
    selectedEntityId: string | null;
    selectedRelationshipId: string | null;
    focusEntityId: string | null;
    editor: ConnectionDraft | null;
    saveState: SaveState;
    saveError: string | null;
    revision: number;
    dragging: boolean;
    dragBefore: ViewModel | null;
    past: ViewModel[];
    future: ViewModel[];
    libraryOpen: boolean;
    panMode: boolean;
    canvasGroup: number;
    setCanvasGroup(value: number): void;
    minimap: boolean;
    libraryWidth: number;
    inspectorWidth: number;
    setPanelWidth(side: 'library' | 'inspector', value: number): void;
    setLocked(ids: string[], locked: boolean): void;
    job: JobProgress | null;
    jobController: AbortController | null;
    initialized: boolean;
    initialize(): Promise<void>;
    setTheme(theme: ThemeMode): void;
    setPage(page: StudioStore['page']): void;
    setPanel(panel: SidePanel): void;
    selectEntity(id: string | null, details?: boolean): void;
    selectRelationship(id: string): void;
    setFocus(id: string | null): void;
    openEditor(draft: ConnectionDraft | null): void;
    updateView(patch: Partial<ViewModel>, history?: boolean): void;
    addEntities(ids: string[], position?: Position): void;
    removeEntities(ids: string[]): void;
    updatePositions(positions: Record<string, Position>, history?: boolean): void;
    startDrag(): void;
    endDrag(): void;
    toggleLock(id: string): void;
    toggleCollapse(id: string): void;
    pinField(entityId: string, fieldId: string): void;
    setViewport(v: Viewport): void;
    undo(): void;
    redo(): void;
    switchDiagram(id: string): Promise<void>;
    createDiagram(name: string, empty?: boolean): Promise<void>;
    duplicateDiagram(id: string): Promise<void>;
    deleteDiagram(id: string): Promise<void>;
    renameDiagram(id: string, name: string, description?: string): Promise<void>;
    save(): Promise<void>;
    importData(data: WorkspaceData): void;
    reset(): void;
    saveRelationship(value: RelationCreate, editingId?: string): Promise<string>;
    decide(id: string, status: 'confirmed' | 'rejected' | 'archived' | 'suggested'): Promise<void>;
    removeRelationship(id:string):Promise<void>;
    hideRelationship(id: string): void;
    runDiscovery(scope: string[]): Promise<void>;
    cancelDiscovery(): void;
    setLibraryOpen(value: boolean): void;
    setPanMode(value: boolean): void;
    setMinimap(value: boolean): void;
}
export const activeDiagram = (state: Pick<StudioStore, 'data'>): ViewModel | undefined => state.data?.diagrams.find(d => d.id === state.data?.activeDiagramId);
const enqueueSave = createSerialQueue();
export const useStudio = create<StudioStore>((set, get) => ({
    data: null, ready: false, loadError: null, theme: readTheme(), page: 'canvas', panel: null, selectedEntityId: null, selectedRelationshipId: null, focusEntityId: null, editor: null, saveState: 'saved', saveError: null, revision: 0, dragging: false, dragBefore: null, past: [], future: [], libraryOpen: !(typeof window !== 'undefined' && window.innerWidth < 1000), panMode: true, canvasGroup: 0, minimap: initialPreferences.minimap, libraryWidth: initialPreferences.libraryWidth, inspectorWidth: initialPreferences.inspectorWidth, job: null, jobController: null, initialized: false,
    async initialize() {
        if (get().initialized)
            return;
        set({ initialized: true });
        try {
            const data = await workspaceService.load();
            set({ data, ready: true, loadError: null, saveState: 'saved', revision: get().revision + 1 });
        }
        catch (e) {
            set({ ready: true, initialized: false, loadError: `无法读取共享元数据或本地画布布局：${errorText(e)}。原布局未被覆盖。` });
        }
    },
    setTheme(theme) { try {
        localStorage.setItem(THEME_KEY, theme);
    }
    catch { /* state still works in private mode */ } set({ theme }); },
    setPage(page) { set({ page, panel: null, editor: null, focusEntityId: null, ...(page === 'canvas' ? { panMode: true } : {}) }); requestStudioNavigation(page, get().data?.activeDiagramId); },
    setPanel(panel) { set({ panel }); },
    selectEntity(id, details = false) { set({ selectedEntityId: id, selectedRelationshipId: null, ...(details && id ? { panel: { type: 'entity' as const, id } } : {}) }); },
    selectRelationship(id) { set({ selectedRelationshipId: id, selectedEntityId: null, panel: { type: 'relationship', id } }); },
    setFocus(id) { set({ focusEntityId: id }); },
    openEditor(editor) { if (editor && !canWrite())
        throw new Error('当前角色不能维护关系'); set({ editor }); },
    updateView(patch, history = true) {
        if (!canWrite())
            return;
        const s = get(), d = activeDiagram(s);
        if (!s.data || !d)
            return;
        const updated = { ...d, ...patch, id: d.id, updatedAt: new Date().toISOString(), version: d.version + 1 };
        set({ data: { ...s.data, diagrams: s.data.diagrams.map(x => x.id === d.id ? updated : x) }, saveState: 'dirty', revision: s.revision + 1, ...(history ? { past: [...s.past, clone(d)].slice(-60), future: [] } : {}) });
    },
    addEntities(ids, position) {
        const s = get(), d = activeDiagram(s);
        if (!d || !s.data)
            return;
        const known = new Set(d.nodes.map(n => n.entityId));
        const nodes = [...d.nodes];
        let offset = 0;
        for (const id of ids) {
            if (known.has(id) || !s.data.snapshot.entities.some(e => e.id === id))
                continue;
            let p = position ? { x: position.x + offset * 36, y: position.y + offset * 36 } : { x: (nodes.length % 3) * 404, y: Math.floor(nodes.length / 3) * 440 };
            while (nodes.some(n => Math.abs(n.position.x - p.x) < 320 && Math.abs(n.position.y - p.y) < 300))
                p = { ...p, y: p.y + 440 };
            nodes.push({ entityId: id, position: p, locked: false, collapsed: false, pinnedFieldIds: [] });
            known.add(id);
            offset++;
        }
        if (nodes.length > 20000)
            throw new Error('单张画布最多显示 20000 个实体，请按业务范围拆分。');
        if (offset) {
            const newSources=ids.map(id=>s.data!.snapshot.entities.find(e=>e.id===id)?.sourceId).filter((id):id is string=>!!id);
            get().updateView({ nodes, librarySourceIds:[...new Set([...(d.librarySourceIds??[]),...newSources])] });
        }
        if (ids.length === 1)
            get().selectEntity(ids[0]);
    },
    removeEntities(ids) { const s = get(), d = activeDiagram(s); if (!d)
        return; get().updateView({ nodes: d.nodes.filter(n => !ids.includes(n.entityId)) }); set({ selectedEntityId: null, selectedRelationshipId: null, panel: null, focusEntityId: null }); },
    updatePositions(positions, history = false) { const d = activeDiagram(get()); if (!d)
        return; let changed=false;const nodes = d.nodes.map(n => {const p=positions[n.entityId];if(!p||n.locked||(p.x===n.position.x&&p.y===n.position.y))return n;changed=true;return {...n,position:p};}); if(changed)get().updateView({ nodes }, history); },
    startDrag() { const d = activeDiagram(get()); if (d)
        set({ dragging: true, dragBefore: clone(d) }); },
    endDrag() { const s = get(), d = activeDiagram(s); const changed = s.dragBefore && d && JSON.stringify(s.dragBefore.nodes.map(n => n.position)) !== JSON.stringify(d.nodes.map(n => n.position)); set({ dragging: false, dragBefore: null, ...(changed ? { past: [...s.past, s.dragBefore!].slice(-60), future: [] } : {}) }); },
    toggleLock(id) { const d = activeDiagram(get()); if (d)
        get().updateView({ nodes: d.nodes.map(n => n.entityId === id ? { ...n, locked: !n.locked } : n) }); },
    toggleCollapse(id) { const d = activeDiagram(get()); if (d)
        get().updateView({ nodes: d.nodes.map(n => n.entityId === id ? { ...n, collapsed: !n.collapsed } : n) }); },
    pinField(entityId, fieldId) { const d = activeDiagram(get()); if (d)
        get().updateView({ fieldMode: d.fieldMode === 'summary' ? 'key' : d.fieldMode, nodes: d.nodes.map(n => n.entityId === entityId ? { ...n, collapsed: false, pinnedFieldIds: [fieldId, ...(n.pinnedFieldIds ?? []).filter(id => id !== fieldId)].slice(0, 12) } : n) }); },
    setViewport(v) { const d = activeDiagram(get()); if (d && (Math.abs((d.viewport?.x ?? 0) - (v.x ?? 0)) > .5 || Math.abs((d.viewport?.y ?? 0) - (v.y ?? 0)) > .5 || Math.abs((d.viewport?.zoom ?? 1) - (v.zoom ?? 1)) > .001))
        get().updateView({ viewport: v }, false); },
    undo() { const s = get(), d = activeDiagram(s), previous = s.past.at(-1); if (!previous || !d || !s.data)
        return; set({ data: { ...s.data, diagrams: s.data.diagrams.map(x => x.id === d.id ? { ...clone(previous), version: d.version + 1, updatedAt: new Date().toISOString() } : x) }, past: s.past.slice(0, -1), future: [clone(d), ...s.future].slice(0, 60), saveState: 'dirty', revision: s.revision + 1 }); },
    redo() { const s = get(), d = activeDiagram(s), next = s.future[0]; if (!next || !d || !s.data)
        return; set({ data: { ...s.data, diagrams: s.data.diagrams.map(x => x.id === d.id ? { ...clone(next), version: d.version + 1, updatedAt: new Date().toISOString() } : x) }, past: [...s.past, clone(d)].slice(-60), future: s.future.slice(1), saveState: 'dirty', revision: s.revision + 1 }); },
    async switchDiagram(id) { const state = get(); if (!state.data?.diagrams.some(d => d.id === id))
        throw new Error('此模型不存在或无权访问'); if (state.saveState !== 'saved') {
        await get().save();
        if (get().saveState !== 'saved')
            throw new Error(get().saveError ?? '请先保存当前模型');
    } const data = await workspaceService.activate(get().data!, id); set({ data, page: 'canvas', panMode: true, canvasGroup: 0, panel: null, editor: null, past: [], future: [], selectedEntityId: null, selectedRelationshipId: null, focusEntityId: null, saveState: 'saved' }); requestStudioNavigation('canvas', id); },
    async createDiagram(name, empty = true) { const state = get(); if (!state.data)
        return; await get().save(); if (get().saveState !== 'saved')
        throw new Error('当前画布尚未保存'); const view = await workspaceService.create(name, empty ? undefined : activeDiagram(get())); set({ data: { ...get().data!, diagrams: [...get().data!.diagrams, view] }, saveState: 'saved' }); await get().switchDiagram(view.id); },
    async duplicateDiagram(id) { const state = get(), view = state.data?.diagrams.find(d => d.id === id); if (!view || !state.data)
        return; await get().save(); if (get().saveState !== 'saved')
        throw new Error('请先保存当前模型'); const copy = await workspaceService.create(view.name + ' · 副本', view); set({ data: { ...get().data!, diagrams: [...get().data!.diagrams, copy] } }); },
    async deleteDiagram(id) { const state = get(), view = state.data?.diagrams.find(d => d.id === id); if (!view || !state.data)
        return; await get().save(); if (get().saveState !== 'saved')
        throw new Error('请先保存当前模型'); await workspaceService.remove(view); const data = await workspaceService.load(); set({ data, saveState: 'saved', panel: null, past: [], future: [] }); },
    async renameDiagram(id, name, description) { const state = get(), view = state.data?.diagrams.find(d => d.id === id); if (!view || !state.data)
        return; await get().save(); if (get().saveState !== 'saved')
        throw new Error('请先保存当前模型'); const changed = await workspaceService.rename(view, name, description); set({ data: { ...get().data!, diagrams: get().data!.diagrams.map(d => d.id === id ? changed : d) }, saveState: 'saved' }); },
    async save() {
        await enqueueSave(async () => {
            const current = get();
            if (!current.data || current.dragging || current.saveState === 'saved')
                return;
            const revision = current.revision;
            // Only local diagrams are written. Immutable catalog/relations need
            // not be serialized and cloned after every drag of a small model.
            const snapshot = {...current.data,diagrams:current.data.diagrams.map(d=>clone(d))};
            set({ saveState: 'saving', saveError: null });
            try {
                await workspaceService.save(snapshot);
                set({ saveState: get().revision === revision ? 'saved' : 'dirty' });
            }
            catch (error) {
                set({ saveState: 'error', saveError: errorText(error) });
            }
        });
    },
    importData(_data) { throw new Error('请使用顶部迁移与备份执行本地导入'); },
    reset() { throw new Error('不会清空本地工作区，请从模型管理操作'); },
    async saveRelationship(value, editingId) {
        const state = get();
        if (!state.data)
            throw new Error('工作区尚未加载');
        if (!canWrite())
            throw new Error('当前角色不能维护关系');
        const old = state.data.relationships.find(r => r.id === editingId);
        if (old?.origin === 'catalog')
            throw new Error('数据库外键只读');
        const errors = validateRelation(value, state.data.snapshot, state.data.relationships, editingId);
        if (errors.length)
            throw new Error(errors.join('\n'));
        await get().save();
        if (get().saveState !== 'saved')
            throw new Error(get().saveError ?? '请先保存模型');
        const relation = await workspaceService.saveRelationship(state.data.activeDiagramId, value, old);
        const live = get().data!;
        set({ data: { ...live, relationships: [...live.relationships.filter(r => r.id !== relation.id), relation] }, editor: null, panel: { type: 'relationship', id: relation.id }, selectedRelationshipId: relation.id, selectedEntityId: null });
        return relation.id;
    },
    async decide(id, status) { const r = get().data?.relationships.find(x => x.id === id); if (!r)
        return; if (r.origin === 'catalog')
        throw new Error('数据库外键只读'); const changed = await workspaceService.decide(r, status); const data = get().data!; set(s=>({ data: { ...data, relationships: [...data.relationships.filter(x=>x.id!==id&&x.id!==changed.id),changed] }, selectedRelationshipId:s.selectedRelationshipId===id?changed.id:s.selectedRelationshipId,panel:s.panel?.type==='relationship'&&s.panel.id===id?{type:'relationship',id:changed.id}:s.panel })); },
    hideRelationship(id) { const d = activeDiagram(get()); if (d)
        get().updateView({ hiddenRelationshipIds: [...new Set([...d.hiddenRelationshipIds, id])] }); set({ panel: null, selectedRelationshipId: null }); },
    async removeRelationship(id){const data=get().data;if(!data)return;if(!canWrite())throw new Error('请先登录');const relation=data.relationships.find(r=>r.id===id);if(!relation||relation.origin==='catalog')throw new Error('不能删除此目录约束');await workspaceService.removeRelationship(id);set(s=>({data:s.data?{...s.data,relationships:s.data.relationships.filter(r=>r.id!==id)}:null,panel:null,selectedRelationshipId:null}));},
    async runDiscovery(scope) { const state = get(); if (!state.data || state.job?.status === 'running')
        return; await get().save(); if (get().saveState !== 'saved')
        throw new Error('请先保存模型'); const controller = new AbortController(); const modelId = state.data.activeDiagramId; set({ jobController: controller, panel: { type: 'discovery' }, job: { id: '', status: 'running', stage: 0, title: '正在请求共享结构关系分析', processed: 0, total: scope.length, newCount: 0, skippedCount: 0 } }); try {
        const result = await workspaceService.discover(state.data.snapshot, state.data.relationships, scope, controller.signal, job => set({ job }));
        if (get().data?.activeDiagramId !== modelId)
            return;
        set({ data: { ...get().data!, relationships: result.relationships, evidence: [...get().data!.evidence.filter(e=>result.relationships.some(r=>r.id===e.relationshipId)),...result.evidence] }, job: result.job, jobController: null });
    }
    catch (error) {
        set({ job: get().job ? { ...get().job!, status: 'failed', title: '任务未完成', error: errorText(error) } : null, jobController: null });
    } },
    cancelDiscovery() { get().jobController?.abort();void workspaceService.cancel().catch(error => set({ job: get().job ? { ...get().job!, error: errorText(error) } : null })); },
    setCanvasGroup(canvasGroup) { set({ canvasGroup }); },
    setLibraryOpen(libraryOpen) { set({ libraryOpen }); }, setPanMode(panMode) { set({ panMode }); }, setMinimap(minimap) { set({ minimap }); const s = get(); savePreferences({ libraryWidth: s.libraryWidth, inspectorWidth: s.inspectorWidth, minimap }); },
    setPanelWidth(side, value) { const width = clampPanelWidth(value, side); set(side === 'library' ? { libraryWidth: width } : { inspectorWidth: width }); const s = get(); savePreferences({ libraryWidth: s.libraryWidth, inspectorWidth: s.inspectorWidth, minimap: s.minimap }); },
    setLocked(ids, locked) { const d = activeDiagram(get()); if (d)
        get().updateView({ nodes: d.nodes.map(n => ids.includes(n.entityId) ? { ...n, locked } : n) }); },
}));
export function currentNodes(): DiagramNode[] { return activeDiagram(useStudio.getState())?.nodes ?? []; }
