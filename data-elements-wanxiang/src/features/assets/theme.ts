import type { ThemeConfig } from 'antd';
import { productTheme } from '../../design/theme';
export function assetTheme(mode: 'light'|'dark'): ThemeConfig {
 const base=productTheme(mode),dark=mode==='dark';
 return {...base,token:{...base.token,colorPrimary:dark?'#d7aa62':'#95651f',colorLink:dark?'#dfb877':'#895b18',colorInfo:dark?'#d7aa62':'#95651f',colorBgLayout:dark?'#11131f':'#f7f6f3',colorBgContainer:dark?'#1b1f30':'#ffffff',colorBgElevated:dark?'#24293d':'#ffffff',colorBorder:dark?'#343a4f':'#e5e1d9',colorText:dark?'#e9ecf4':'#29313e',colorTextSecondary:dark?'#a8b1c8':'#697181',controlHeight:34},components:{...base.components,Layout:{headerBg:dark?'#191d2d':'#ffffff',siderBg:dark?'#171a28':'#ffffff',bodyBg:dark?'#11131f':'#f7f6f3'},Table:{...base.components?.Table,headerBg:dark?'#24293d':'#f5f3ef',rowHoverBg:dark?'#2b2b3d':'#faf5eb'},Button:{...base.components?.Button,primaryShadow:'none'}}};
}
