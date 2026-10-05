import { createContext, useContext, useLayoutEffect, useMemo, useState } from 'react';
import { App, ConfigProvider, theme as antTheme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
export const BRAND='#2457D6';
interface ThemeContextValue{dark:boolean;toggle:()=>void;motion:boolean;toggleMotion:()=>void;}
const Context=createContext<ThemeContextValue>({dark:false,toggle:()=>{},motion:true,toggleMotion:()=>{}});
export const useOceanTheme=()=>useContext(Context);
const preference=(key:string)=>{try{return localStorage.getItem(key);}catch{return null;}};
export function OceanTheme({children}:{children:React.ReactNode}){
 const [dark,setDark]=useState(()=>preference('haitong:theme')==='dark');
 const [motion,setMotion]=useState(()=>preference('haitong:motion')!=='off');
 useLayoutEffect(()=>{document.documentElement.dataset.theme=dark?'dark':'light';document.documentElement.dataset.motion=motion?'on':'off';try{localStorage.setItem('haitong:theme',dark?'dark':'light');localStorage.setItem('haitong:motion',motion?'on':'off');}catch{}},[dark,motion]);
 const config=useMemo(()=>({algorithm:[dark?antTheme.darkAlgorithm:antTheme.defaultAlgorithm,antTheme.compactAlgorithm],token:{colorPrimary:dark?'#729AFF':BRAND,colorInfo:dark?'#729AFF':BRAND,colorText:dark?'#E4ECFA':'#172B4D',colorTextSecondary:dark?'#ABBDD6':'#586B85',colorTextPlaceholder:dark?'#9DACC3':'#65758D',colorBgContainer:dark?'#152237':'#FFFFFF',colorBgElevated:dark?'#1B2B44':'#FFFFFF',colorBorder:dark?'#344761':'#D8E1ED',borderRadius:8,fontSize:14,controlHeight:34,controlHeightSM:30,lineHeight:1.55,motion},components:{Table:{fontSize:14,cellPaddingBlock:11,headerBg:dark?'#1A2A43':'#F6F8FC'},Button:{fontWeight:500},Input:{fontSize:14},Select:{fontSize:14},Modal:{titleFontSize:18}}}),[dark,motion]);
 return <Context.Provider value={{dark,toggle:()=>setDark(v=>!v),motion,toggleMotion:()=>setMotion(v=>!v)}}><ConfigProvider locale={zhCN} theme={config}><App>{children}</App></ConfigProvider></Context.Provider>;
}
