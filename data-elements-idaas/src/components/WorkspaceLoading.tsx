import { AppstoreOutlined, SafetyCertificateOutlined, TeamOutlined } from '@ant-design/icons';
import { BrandMark } from './Brand';

/** A single loading state for session restore, domain changes and lazy pages. */
export function WorkspaceLoading({ label, fullScreen = false }: { label: string; fullScreen?: boolean }) {
    return <div className={`workspace-loading${fullScreen ? ' workspace-loading-full' : ''}`} role="status" aria-live="polite" aria-label={label}>
        <div className="workspace-loading-panel">
            <div className="workspace-loading-brand"><BrandMark size={32}/><span>统一身份管理平台</span></div>
            <div className="workspace-loading-illustration" aria-hidden="true">
                <span className="workspace-loading-link"/>
                <span className="workspace-loading-node"><AppstoreOutlined /></span>
                <span className="workspace-loading-node"><TeamOutlined /></span>
                <span className="workspace-loading-node"><SafetyCertificateOutlined /></span>
            </div>
            <h1>{label}</h1>
            <p>正在准备你有权访问的内容，请稍候…</p>
            <span className="workspace-loading-progress" aria-hidden="true"><span/></span>
        </div>
    </div>;
}
