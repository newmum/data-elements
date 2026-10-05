import { useState, type CSSProperties } from 'react';
import { Avatar, Dropdown } from 'antd';
import type { MenuProps } from 'antd';
import { CaretDownFilled, CheckOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { BrandMark } from '../components/Brand';
import { workspaceName } from '../domain/branding';
import { centerAddresses, productGroups, productCenterUrl } from './platformNavigation';

export function ProductCenterSwitcher({ collapsed = false, logoUrl = '' }: { collapsed?: boolean; logoUrl?: string }) {
    const [open, setOpen] = useState(false);
    const items: MenuProps['items'] = productGroups.map(group => ({
        type: 'group', label: group.name,
        children: group.centers.map(center => ({
            key: center.id,
            label: <div className={`iam-center-item${center.id === 'idaas' ? ' current' : ''}`}>
                <span className="iam-center-mark" style={{ '--center-color': center.color } as CSSProperties}>
                    {center.id === 'idaas' ? <BrandMark size={34}/> : <b>{center.mark}</b>}
                </span>
                <span className="iam-center-copy"><b>{center.name}</b><small>{center.description}</small></span>
                {center.id === 'idaas' && <span className="iam-center-current"><CheckOutlined/> 当前</span>}
            </div>,
        })),
    }));
    return <Dropdown trigger={['click']} placement="bottomLeft" open={open} onOpenChange={setOpen}
        classNames={{ root: 'iam-center-popover' }}
        menu={{ items, onClick: ({ key }) => {
            setOpen(false);
            const center = productGroups.flatMap(group => [...group.centers]).find(item => item.id === key);
            if (center && center.id !== 'idaas') window.location.assign(productCenterUrl(center, centerAddresses(), window.location.href));
        } }}>
        <button type="button" className="sider-brand iam-product-switcher" aria-label="切换工作中心" aria-haspopup="menu" aria-expanded={open} title={`${workspaceName} · 切换工作中心`}>
            {logoUrl ? <Avatar shape="square" size={30} src={logoUrl} icon={<SafetyCertificateOutlined/>}/> : <BrandMark size={30}/>}
            {!collapsed && <span className="sider-brand-text"><span className="iam-product-title"><strong>{workspaceName}</strong><CaretDownFilled/></span><small>安全 · 连接 · 赋能</small></span>}
        </button>
    </Dropdown>;
}
