import { useEffect, useRef, type KeyboardEvent } from 'react';
import { Tooltip } from 'antd';
import { navigation } from './routes';
import { ModuleIcon } from '../design/ModuleIcon';
/** One real 44px button centered in the 64px rail. No invisible title or inline-indent. */
export function ProductNavigation({ collapsed, selected, onNavigate }: { collapsed: boolean; selected: string; onNavigate: (key: string) => void }) {
  const root = useRef<HTMLElement>(null);
  useEffect(() => { root.current?.querySelector('[aria-current="page"]')?.scrollIntoView({block:'nearest',inline:'nearest'}); }, [selected,collapsed]);
  const move = (e: KeyboardEvent<HTMLButtonElement>) => {
    if (!['ArrowDown','ArrowUp','Home','End'].includes(e.key)) return;
    e.preventDefault();
    const buttons = [...(root.current?.querySelectorAll<HTMLButtonElement>('.wx-nav-link') ?? [])];
    const index = buttons.indexOf(e.currentTarget);
    const next = e.key === 'Home' ? 0 : e.key === 'End' ? buttons.length-1 : (index + (e.key === 'ArrowDown' ? 1 : -1) + buttons.length) % buttons.length;
    buttons[next]?.focus();
  };
  return <nav ref={root} className="wx-product-nav" data-collapsed={collapsed} aria-label="治理功能导航">{navigation.map(group => <section className="wx-nav-group" key={group.label} aria-label={group.label}>{!collapsed && <h2>{group.label}</h2>}<ul>{group.children.map(item => <li key={item.key}><Tooltip placement="right" title={collapsed ? item.label : null} mouseEnterDelay={.15}><button type="button" className="wx-nav-link" aria-label={item.label} aria-current={selected === item.key ? 'page' : undefined} onKeyDown={move} onClick={() => onNavigate(item.key)}><ModuleIcon name={item.icon}/>{!collapsed && <span className="wx-nav-label">{item.label}</span>}</button></Tooltip></li>)}</ul></section>)}</nav>;
}
