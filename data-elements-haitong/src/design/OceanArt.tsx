
import { useId } from 'react';
export type ArtKind='overview'|'tasks'|'nifi'|'registry'|'batch'|'multi'|'ingress'|'distribution'|'clusters'|'cross'|'instant'|'inventory'|'statements';

export default function OceanArt({kind='overview'}:{kind?:ArtKind}){
 const id=useId().replaceAll(':','');
 const cube=(x:number,y:number,s=1,accent=false)=> <g transform={`translate(${x} ${y}) scale(${s})`}>
   <path d="M0 28 48 0 96 28 48 56Z" fill={accent?`url(#accentTop${id})`:`url(#top${id})`} stroke="#C8D8FF"/>
   <path d="M0 28v52l48 28V56Z" fill={accent?`url(#accentFront${id})`:`url(#front${id})`} stroke="#C8D8FF"/>
   <path d="m48 56 48-28v52L48 108Z" fill={accent?`url(#accentSide${id})`:`url(#side${id})`} stroke="#C8D8FF"/>
   <path d="m12 35 36 21 36-21M48 56v36" stroke="#E7F0FF" strokeWidth="1.5" opacity=".9"/>
  </g>;
 const plate=(x:number,y:number,w:number,h:number,opacity=.94)=> <g transform={`translate(${x} ${y})`} opacity={opacity}>
   <rect width={w} height={h} rx="16" fill="url(#glass${id})" stroke="#D5E3FF"/>
   <rect x="14" y="16" width={w-28} height="10" rx="5" fill="#D2DEF7" opacity=".9"/>
   <rect x="14" y="34" width={Math.max(40,w*0.46)} height="8" rx="4" fill="#A7BDEA" opacity=".72"/>
   <rect x="14" y="48" width={Math.max(60,w*0.64)} height="8" rx="4" fill="#BFD0F1" opacity=".55"/>
  </g>;
 const floatingCard=(x:number,y:number,w:number,h:number,label='')=> <g transform={`translate(${x} ${y})`}>
   <rect width={w} height={h} rx="14" fill="url(#glass${id})" stroke="#D3E0FB"/>
   <circle cx="18" cy="18" r="5" fill="#17A4C1" opacity=".88"/>
   <rect x="30" y="14" width={w-44} height="8" rx="4" fill="#AFC4EF" opacity=".84"/>
   <rect x="14" y="32" width={w*0.52} height="7" rx="3.5" fill="#D2DEF7"/>
   <rect x="14" y="46" width={w*0.64} height="7" rx="3.5" fill="#DCE7FB"/>
   {label ? <text x={14} y={h-14} fontSize="11" fill="#2457D6" fontWeight="600">{label}</text> : null}
  </g>;
 const stack=(x:number,y:number,accent=false)=> <g transform={`translate(${x} ${y})`}>
   <ellipse cx="112" cy="140" rx="112" ry="18" fill="#285CCF" opacity=".08"/>
   <g transform="translate(28 64)"><rect width="170" height="30" rx="15" fill="#DCE7FB" opacity=".78"/><rect x="10" y="-20" width="150" height="28" rx="14" fill="#ECF3FF" opacity=".95"/><rect x="20" y="-42" width="130" height="26" rx="13" fill="#FFFFFF" opacity=".98"/></g>
   {cube(82,14,1.12,accent)}
  </g>;
 const db=(x:number,y:number,s=1)=> <g transform={`translate(${x} ${y}) scale(${s})`}>
   <ellipse cx="44" cy="14" rx="44" ry="14" fill="url(#top${id})" stroke="#8DB0F3"/>
   <path d="M0 14v64c0 18 88 18 88 0V14" fill="url(#front${id})" stroke="#3A66CF"/>
   <path d="M0 36c0 18 88 18 88 0M0 58c0 18 88 18 88 0" fill="none" stroke="#C7D8FB" strokeWidth="2"/>
   <circle cx="67" cy="58" r="4" fill="#9FE3EC"/>
  </g>;
 const radar=(x:number,y:number)=> <g transform={`translate(${x} ${y})`}>
   <circle cx="48" cy="48" r="46" fill="url(#glass${id})" stroke="#D3E1FB"/>
   <circle cx="48" cy="48" r="32" fill="none" stroke="#AFC3EC" strokeDasharray="4 6"/>
   <circle cx="48" cy="48" r="18" fill="none" stroke="#BFD2F2" strokeDasharray="4 6"/>
   <path d="M48 48 84 32" stroke="#2457D6" strokeWidth="3" strokeLinecap="round"/>
   <path d="M48 48 63 76" stroke="#17A4C1" strokeWidth="3" strokeLinecap="round"/>
   <circle cx="48" cy="48" r="6" fill="#2457D6"/>
  </g>;
 const route=(d:string, wide=false)=> <path d={d} fill="none" stroke={wide?`url(#route${id})`:'#8FB2EF'} strokeWidth={wide?4:2.2} strokeDasharray={wide?'0':'5 6'} strokeLinecap="round"/>;
 const orbit=(cx:number,cy:number,rx:number,ry:number,rot=0)=> <ellipse cx={cx} cy={cy} rx={rx} ry={ry} transform={`rotate(${rot} ${cx} ${cy})`} fill="none" stroke="#B9CFF4" strokeWidth="1.5" opacity=".65"/>;
 const dot=(cx:number,cy:number,r=4,color='#2457D6',opacity=.9)=><circle cx={cx} cy={cy} r={r} fill={color} opacity={opacity}/>;
 const shield=(x:number,y:number)=> <g transform={`translate(${x} ${y})`}>
   <path d="M50 0 90 16v34c0 35-21 58-40 70C31 108 10 85 10 50V16Z" fill="url(#top${id})" stroke="#87A9ED"/>
   <path d="m33 55 12 12 25-28" stroke="#fff" strokeWidth="7" fill="none" strokeLinecap="round" strokeLinejoin="round"/>
  </g>;
 const magnify=(x:number,y:number)=> <g transform={`translate(${x} ${y})`}>
   <circle cx="42" cy="42" r="34" fill="url(#glass${id})" stroke="#8FB0F1" strokeWidth="6"/>
   <path d="m68 68 28 28" stroke="#2457D6" strokeWidth="10" strokeLinecap="round"/>
   <path d="m27 42 9 9 18-20" stroke="#17A4C1" strokeWidth="5" fill="none"/>
  </g>;

 const art:Record<ArtKind,React.ReactNode>={
  overview:<>
    {orbit(244,118,168,66,8)}{orbit(260,120,144,50,-12)}{route('M32 184C98 147 134 149 188 176S308 203 404 162')} {dot(128,80,5)}{dot(332,86,6,'#17A4C1')}
    {stack(118,46,true)}{floatingCard(38,88,110,74,'接入')} {floatingCard(310,74,100,74,'对账')} {floatingCard(52,176,116,62,'运维')} {floatingCard(292,178,108,62,'分发')}
  </>,
  tasks:<>
    {orbit(230,134,176,64,6)}{route('M52 178C100 140 152 132 188 149C220 164 250 191 392 149',true)}
    {floatingCard(26,76,108,86,'模板场景')} {cube(176,62,1.05,true)} {plate(136,168,140,58,.9)} {floatingCard(302,78,102,86,'字段映射')} {dot(150,72,4,'#17A4C1')} {dot(300,140,5)}
  </>,
  nifi:<>
    {orbit(230,134,176,64,6)}{route('M52 178C100 140 152 132 188 149C220 164 250 191 392 149',true)}
    {floatingCard(26,76,108,86,'流程节点')} {cube(176,62,1.05,true)} {plate(136,168,140,58,.9)} {floatingCard(302,78,102,86,'运行监控')} {dot(150,72,4,'#17A4C1')} {dot(300,140,5)}
  </>,
  registry:<>
    {route('M76 140h74',true)} {route('M214 140h76',true)} {floatingCard(28,82,104,92,'画布解析')} {cube(158,76,.96,true)} {floatingCard(294,82,110,92,'统一登记')}
    <path d="m228 88 16 16 36-36" stroke="#17A4C1" strokeWidth="8" fill="none" strokeLinecap="round" strokeLinejoin="round"/>
    {plate(142,176,134,56,.92)}
  </>,
  batch:<>
    {route('M34 192H408')} {cube(32,86,.72)} {cube(150,68,.8,true)} {cube(280,92,.72)}
    {floatingCard(24,34,100,64,'来源库')} {floatingCard(168,24,104,64,'规则匹配')} {floatingCard(308,34,100,64,'目标库')} {plate(132,168,160,58,.92)}
  </>,
  multi:<>
    {db(22,92,.84)} {route('M98 137h74v-34h40')} {route('M98 137h108')} {route('M98 137h74v36h40')} {cube(208,78,.88,true)} {floatingCard(304,70,102,88,'逐表入库')} {plate(178,180,128,52,.92)} {dot(170,102,4,'#17A4C1')} {dot(170,138,4,'#17A4C1')} {dot(170,174,4,'#17A4C1')}
  </>,
  ingress:<>
    {floatingCard(24,84,102,86,'接入任务')} {route('M124 126h72',true)} {db(196,86,.88)} {cube(326,108,.62,true)} {plate(188,182,150,54,.92)} {dot(160,126,5,'#17A4C1')}
  </>,
  distribution:<>
    {db(34,96,.84)} {route('M112 136h88V86h74',true)} {route('M112 136h220',true)} {route('M112 136h88v60h74',true)} {cube(274,54,.6)} {cube(330,118,.62,true)} {cube(274,182,.6)}
    {floatingCard(300,14,104,54,'服务链路')}
  </>,
  clusters:<>
    {cube(152,38,.95,true)} {cube(42,142,.8)} {cube(300,142,.8)} {route('M196 136 116 174',true)} {route('M244 136 320 174',true)} {route('M116 205H320',true)} {plate(144,180,156,56,.92)} {dot(116,175,5,'#17A4C1')} {dot(320,175,5,'#17A4C1')}
  </>,
  cross:<>
    <path d="M162 28v212M278 28v212" stroke="#C6D5F2" strokeWidth="2" strokeDasharray="6 8"/>{db(22,92,.84)}{db(310,92,.84)}{route('M104 136h232',true)}{cube(194,104,.62,true)}<path d="M170 72q56-40 100 0" stroke="#2457D6" strokeWidth="3" fill="none"/><path d="m260 60 12 11-17 5" fill="none" stroke="#2457D6" strokeWidth="3"/>{floatingCard(166,176,108,54,'网络边界')}
  </>,
  instant:<>
    {floatingCard(38,84,104,94,'来源记录')} {floatingCard(300,84,104,94,'目标记录')} {route('M142 132h158',true)} <path d="m211 54-20 47h32l-20 43 58-58h-33l20-32Z" fill="url(#front${id})"/>{plate(170,176,100,52,.92)}<path d="m197 198 14 14 30-32" stroke="#17A4C1" strokeWidth="6" fill="none" strokeLinecap="round"/>
  </>,
  inventory:<>
    {floatingCard(28,78,104,88,'规则配置')} {magnify(168,92)} {floatingCard(306,78,100,88,'差异结果')} {plate(150,186,136,52,.92)} {dot(154,120,4,'#17A4C1')} {dot(287,140,5)}
  </>,
  statements:<>
    {floatingCard(48,96,94,80,'执行记录')} {floatingCard(174,54,102,80,'差异明细')} {floatingCard(304,96,96,80,'脱敏导出')} {route('M144 136h34')} {route('M278 136h28')} {plate(156,182,126,52,.92)} <path d="m316 132 10 10 20-23" fill="none" stroke="#2457D6" strokeWidth="5" strokeLinecap="round"/>
  </>,
 };

 return <svg viewBox="0 0 440 270" className="ocean-art" aria-hidden="true" preserveAspectRatio="xMidYMid meet">
   <defs>
     <linearGradient id={`top${id}`} x1="0" y1="0" x2="1" y2="1"><stop stopColor="#B8D0FF"/><stop offset="1" stopColor="#6D98F0"/></linearGradient>
     <linearGradient id={`front${id}`} x1="0" y1="0" x2="1" y2="1"><stop stopColor="#4B7BEA"/><stop offset="1" stopColor="#2457D6"/></linearGradient>
     <linearGradient id={`side${id}`} x1="0" y1="0" x2="1" y2="1"><stop stopColor="#2350C8"/><stop offset="1" stopColor="#173FAD"/></linearGradient>
     <linearGradient id={`accentTop${id}`} x1="0" y1="0" x2="1" y2="1"><stop stopColor="#B4F0F3"/><stop offset="1" stopColor="#66CDE2"/></linearGradient>
     <linearGradient id={`accentFront${id}`} x1="0" y1="0" x2="1" y2="1"><stop stopColor="#5CC5D8"/><stop offset="1" stopColor="#17A4C1"/></linearGradient>
     <linearGradient id={`accentSide${id}`} x1="0" y1="0" x2="1" y2="1"><stop stopColor="#1396B3"/><stop offset="1" stopColor="#0F7692"/></linearGradient>
     <linearGradient id={`glass${id}`} x1="0" y1="0" x2="1" y2="1"><stop stopColor="#FFFFFF" stopOpacity="0.96"/><stop offset="1" stopColor="#F4F8FF" stopOpacity="0.84"/></linearGradient>
     <linearGradient id={`route${id}`} x1="0" y1="0" x2="1" y2="0"><stop stopColor="#5E8BF1"/><stop offset="1" stopColor="#17A4C1"/></linearGradient>
     <radialGradient id={`glow${id}`} cx="50%" cy="50%" r="50%"><stop stopColor="#98B9FF" stopOpacity="0.32"/><stop offset="1" stopColor="#98B9FF" stopOpacity="0"/></radialGradient>
   </defs>
   <rect x="0" y="0" width="440" height="270" rx="0" fill="transparent"/>
   <ellipse cx="318" cy="60" rx="104" ry="58" fill={`url(#glow${id})`} />
   <ellipse cx="106" cy="72" rx="74" ry="40" fill={`url(#glow${id})`} opacity=".65" />
   <path d="M8 206q84-38 175 4t249-6M0 232q88-32 180 0t252-8" fill="none" stroke="#A9C3F1" opacity=".28"/>
   {art[kind]}
 </svg>;
}
