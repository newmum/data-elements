import { Layout, Menu, Space, Typography } from 'antd';
import {
  ApartmentOutlined,
  DatabaseOutlined,
  DashboardOutlined,
  FileTextOutlined,
  CloudServerOutlined,
} from '@ant-design/icons';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { useT } from '@/i18n';
import { BRAND } from '@/theme/tokens';

const { Header, Content } = Layout;

const navItems = [
  { key: '/', icon: <ApartmentOutlined />, labelKey: 'nav.flows' },
  { key: '/platform-datasources', icon: <DatabaseOutlined />, labelKey: 'nav.datasources', platformRoute: '/datasource-manage/index' },
  { key: '/runtime-nodes', icon: <CloudServerOutlined />, labelKey: 'nav.runtimeNodes' },
  { key: '/monitor', icon: <DashboardOutlined />, labelKey: 'nav.monitor' },
  { key: '/docs', icon: <FileTextOutlined />, labelKey: 'nav.docs' },
];

function platformRouteUrl(route: string) {
  const configured = import.meta.env.VITE_PLATFORM_FRONTEND_URL as string | undefined;
  if (configured) return `${configured.replace(/\/$/, '')}/#${route}`;
  const yuntiIndex = window.location.pathname.indexOf('/yunti');
  const mountPrefix = yuntiIndex >= 0 ? window.location.pathname.slice(0, yuntiIndex) : '';
  return `${window.location.origin}${mountPrefix}/#${route}`;
}

export default function AppLayout() {
  const t = useT();
  const location = useLocation();

  const selectedKey =
    navItems
      .map((i) => i.key)
      .filter((k) => (k === '/' ? location.pathname === '/' : location.pathname.startsWith(k)))
      .sort((a, b) => b.length - a.length)[0] ?? '/';

  return (
    <Layout style={{ height: '100%' }}>
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 24,
          paddingInline: 20,
          background: 'var(--ant-color-bg-container)',
          borderBottom: '1px solid var(--ant-color-border-secondary)',
        }}
      >
        <Space size={8} align="center" className="app-brand">
          <span className="app-logo" aria-hidden="true">
            <svg className="app-logo__svg" viewBox="0 0 32 32" role="img">
              <path className="app-logo__cloud" d="M10.4 23.8h12.4a5.5 5.5 0 0 0 .9-10.9 8 8 0 0 0-15.2-1.8 6.4 6.4 0 0 0 1.9 12.7Z" />
              <path className="app-logo__route" d="M10 21 C13 19, 13 15, 16 14 S20 11, 22 8" />
              <circle className="app-logo__dot app-logo__dot--a" cx="10" cy="21" r="2.3" />
              <circle className="app-logo__dot app-logo__dot--b" cx="16" cy="14" r="2.3" />
              <circle className="app-logo__dot app-logo__dot--c" cx="22" cy="8" r="2.3" />
            </svg>
          </span>
          <Typography.Text strong style={{ fontSize: 18, color: BRAND }}>
            云梯服务编排平台
          </Typography.Text>
        </Space>
        <Menu
          mode="horizontal"
          selectedKeys={[selectedKey]}
          style={{ flex: 1, minWidth: 0, borderBottom: 'none', background: 'transparent' }}
          items={navItems.map((i) => ({
            key: i.key,
            icon: i.icon,
            label: i.platformRoute
              ? <a href={platformRouteUrl(i.platformRoute)}>{t(i.labelKey)}</a>
              : <Link to={i.key}>{t(i.labelKey)}</Link>,
          }))}
        />
      </Header>
      <Content style={{ overflow: 'auto' }}>
        <Outlet />
      </Content>
    </Layout>
  );
}
