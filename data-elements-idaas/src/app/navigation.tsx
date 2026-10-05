import type { ReactNode } from 'react';
import { AppstoreOutlined, ApartmentOutlined, AuditOutlined, DashboardOutlined, KeyOutlined, SettingOutlined, SyncOutlined, UserOutlined, TeamOutlined, SafetyCertificateOutlined, FileSearchOutlined } from '@ant-design/icons';
export { pages, publicPageKeys } from './navigation-data';
export type { NavPage } from './navigation-data';
export const groupIcons: Record<string, ReactNode> = { '总览': <DashboardOutlined />, '应用管理': <AppstoreOutlined />, '组织与用户': <ApartmentOutlined />, '授权管理': <KeyOutlined />, '同步管理': <SyncOutlined />, '审计日志': <AuditOutlined />, '系统管理': <SettingOutlined />, '公众身份': <TeamOutlined />, '个人中心': <UserOutlined /> };
