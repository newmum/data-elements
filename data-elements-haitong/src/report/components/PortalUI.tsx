import { useId, useState, type ReactNode } from 'react';
import { Popover } from 'antd';
import Icon from './Icon';

/** Two accessible view tabs; inactive views stay mounted to preserve filter and page state. */
export function PortalTabs({ activeKey, onChange, items, className = '', tabBarExtraContent }: {
    activeKey: string; onChange: (key: string) => void; items: { key: string; label: ReactNode; children: ReactNode }[];
    className?: string; animated?: boolean; tabBarExtraContent?: ReactNode;
}) {
    const id = useId();
    return <div className={`${className} report-ant-tabs report-ant-tabs-top`}>
        <div className="report-ant-tabs-nav"><div role="tablist" aria-label="数据视图" className="portal-tab-buttons">
            {items.map((item, index) => <button type="button" key={item.key} id={`${id}-${item.key}-tab`} role="tab"
                aria-selected={activeKey === item.key} aria-controls={`${id}-${item.key}-panel`} tabIndex={activeKey === item.key ? 0 : -1}
                className={`portal-tab-button ${activeKey === item.key ? 'active' : ''}`} onClick={() => onChange(item.key)}
                onKeyDown={e => {
                    const next = e.key === 'ArrowRight' ? (index + 1) % items.length : e.key === 'ArrowLeft' ? (index - 1 + items.length) % items.length : e.key === 'Home' ? 0 : e.key === 'End' ? items.length - 1 : -1;
                    if (next >= 0) { e.preventDefault(); onChange(items[next].key); document.getElementById(`${id}-${items[next].key}-tab`)?.focus(); }
                }}>{item.label}</button>)}
        </div>{tabBarExtraContent}</div>
        <div className="report-ant-tabs-content-holder">{items.map(item => <div key={item.key} id={`${id}-${item.key}-panel`} role="tabpanel"
            aria-labelledby={`${id}-${item.key}-tab`} hidden={activeKey !== item.key}>{item.children}</div>)}</div>
    </div>;
}

/** Small contextual menu, rendered by the same Ant Design Popover in both builds. */
export function PortalMenu({ menu, children }: { menu: { items: { key: string; label: string; danger?: boolean }[]; onClick: (info: { key: string }) => void }; children: ReactNode; trigger?: string[] }) {
    const [open, setOpen] = useState(false);
    return <Popover trigger="click" placement="bottomRight" open={open} onOpenChange={setOpen} content={<div className="portal-context-menu" role="menu">
        {menu.items.map(item => <button role="menuitem" type="button" key={item.key} className={item.danger ? 'danger' : ''} onClick={() => { menu.onClick({ key: item.key }); setOpen(false); }}>{item.label}</button>)}
    </div>}>{children}</Popover>;
}
export function PortalEmpty({ description }: { description: string }) {
    return <div className="portal-empty-copy"><Icon name="folder" size={38}/><p>{description}</p></div>;
}
