/** A single loading state for session restore, domain changes and lazy pages. */
export function WorkspaceLoading({ label, fullScreen = false }: { label: string; fullScreen?: boolean }) {
    return <div className={`workspace-loading${fullScreen ? ' workspace-loading-full' : ''}`} role="status" aria-live="polite" aria-label={label}>
        <div className="workspace-loading-panel">
            <div className="workspace-loading-visual" aria-hidden="true">
                <span className="workspace-loading-orbit"/>
                <span className="workspace-loading-orbit workspace-loading-orbit-two"/>
                <div className="workspace-loading-preview">
                    <div className="workspace-loading-preview-rail"><span/><span/><span/><span/></div>
                    <div className="workspace-loading-preview-content">
                        <span className="workspace-loading-preview-title"/>
                        <span className="workspace-loading-preview-line"/>
                        <span className="workspace-loading-preview-line"/>
                        <span className="workspace-loading-preview-line"/>
                    </div>
                    <span className="workspace-loading-preview-sheen"/>
                </div>
                <span className="workspace-loading-spark workspace-loading-spark-one"/>
                <span className="workspace-loading-spark workspace-loading-spark-two"/>
            </div>
            <h1>{label}</h1>
            <p>正在整理页面内容，请稍候…</p>
            <span className="workspace-loading-progress" aria-hidden="true"><span/></span>
        </div>
    </div>;
}
