import { useId } from 'react';
import { Button } from 'antd';
import { ArrowRightOutlined, ReloadOutlined } from '@ant-design/icons';
import { BrandMark } from '../design/BrandMark';
import { PanshiLogo } from '../panshi/design/PanshiLogo';
import { startupFailure, startupPhases, type StartupPhase } from './startupPresentation';

type StartupSceneProps =
  | { state: 'loading'; phase?: StartupPhase; contained?: boolean }
  | { state: 'error'; error: unknown; onRetry: () => void; contained?: boolean };

/** Repository-native artwork: no external images, no synthetic business data. */
function GovernanceConnectionArt({ interrupted }: { interrupted: boolean }) {
  const id = useId().replace(/:/g, '');
  return <div className="wx-startup-art" aria-hidden="true">
    <svg viewBox="0 0 600 400" fill="none" focusable="false">
      <defs>
        <linearGradient id={`${id}-top`} x1="220" y1="100" x2="370" y2="205" gradientUnits="userSpaceOnUse"><stop stopColor="#D6C6FF"/><stop offset="1" stopColor="#8452EA"/></linearGradient>
        <linearGradient id={`${id}-left`} x1="235" y1="165" x2="304" y2="260" gradientUnits="userSpaceOnUse"><stop stopColor="#A47AF7"/><stop offset="1" stopColor="#6830D0"/></linearGradient>
        <linearGradient id={`${id}-right`} x1="350" y1="158" x2="312" y2="250" gradientUnits="userSpaceOnUse"><stop stopColor="#7A42D9"/><stop offset="1" stopColor="#40218F"/></linearGradient>
        <linearGradient id={`${id}-base`} x1="160" y1="262" x2="411" y2="341" gradientUnits="userSpaceOnUse"><stop stopColor="var(--startup-platform-top)"/><stop offset="1" stopColor="var(--startup-platform-bottom)"/></linearGradient>
        <radialGradient id={`${id}-glow`}><stop stopColor="#A17BFF" stopOpacity=".24"/><stop offset="1" stopColor="#A17BFF" stopOpacity="0"/></radialGradient>
      </defs>
      <ellipse cx="299" cy="240" rx="215" ry="160" fill={`url(#${id}-glow)`}/>
      <g className="wx-startup-art-grid" stroke="var(--startup-art-line)" strokeWidth="1">
        <path d="m83 294 211-119 223 128M107 327l210-119 191 104M144 351l194-110 133 74M185 372l174-99 76 46"/>
        <path d="m83 294 171 96m-111-130 174 99m-116-132 175 99m-110-133 173 101m-111-136 173 101"/>
      </g>
      <ellipse cx="300" cy="298" rx="148" ry="43" fill="var(--startup-shadow)"/>
      <path d="m162 276 137-77 139 78v22l-139 78-137-78Z" fill="var(--startup-platform-side)"/>
      <path d="m162 276 137-77 139 78-139 79Z" fill={`url(#${id}-base)`} stroke="var(--startup-platform-stroke)"/>
      <path d="m181 276 118-66 120 67-120 68Z" stroke="var(--startup-platform-stroke)" opacity=".5"/>
      <path d="M299 355v21m139-99v22" stroke="var(--startup-platform-stroke)"/>
      <ellipse cx="300" cy="260" rx="204" ry="65" transform="rotate(-18 300 260)" stroke="var(--startup-orbit)" strokeWidth="1.5"/>
      <ellipse cx="300" cy="260" rx="178" ry="53" transform="rotate(-18 300 260)" stroke="var(--startup-orbit)" strokeDasharray="3 10" opacity=".5"/>
      <g stroke="var(--startup-connector)" strokeWidth="2" strokeLinecap="round">
        <path d="M130 101v45q0 20 23 20h45l35 20M473 84v56q0 21-21 21h-52l-33 24M124 295v-50q0-20 22-20h87"/>
        {interrupted ? <><path d="M368 230h32q20 0 20 20v16"/><path d="M420 290v15q0 19 20 19h42" strokeDasharray="5 6"/></> : <path d="M368 230h32q20 0 20 20v55q0 19 20 19h42"/>}
      </g>
      <g className="wx-startup-data-flow" stroke="var(--startup-pulse)" strokeWidth="3" strokeDasharray="3 120" strokeLinecap="round">
        <path d="M130 101v45q0 20 23 20h45l35 20M473 84v56q0 21-21 21h-52l-33 24M124 295v-50q0-20 22-20h87"/>
      </g>
      <g className="wx-startup-core">
        <ellipse cx="300" cy="271" rx="69" ry="19" fill="var(--startup-shadow)"/>
        <path d="M238 234v20c0 25 124 25 124 0v-20" fill={`url(#${id}-left)`}/>
        <ellipse cx="300" cy="234" rx="62" ry="17" fill={`url(#${id}-top)`}/>
        <path d="M247 254c22 11 83 11 106 0" stroke="#D7C3FF" opacity=".6"/>
        <path d="M238 211v20c0 25 124 25 124 0v-20" fill={`url(#${id}-right)`}/>
        <ellipse cx="300" cy="211" rx="62" ry="17" fill={`url(#${id}-top)`}/>
        <path d="M247 231c22 11 83 11 106 0" stroke="#D7C3FF" opacity=".65"/>
        <path d="M300 108 366 146 300 184 234 146Z" fill={`url(#${id}-top)`} stroke="#DBCBFF" strokeOpacity=".5"/>
        <path d="M234 153 294 188v58l-60-34Z" fill={`url(#${id}-left)`}/>
        <path d="m306 188 60-35v59l-60 34Z" fill={`url(#${id}-right)`}/>
        <path d="m244 183 50 29m12 0 50-29" stroke="#DCCAFF" strokeWidth="1.4" opacity=".6"/>
        <path d="m300 132 24 14-24 14-24-14Z" fill="#FBF9FF" opacity=".92"/>
        <path d="m300 138 13 8-13 8-13-8Z" fill="#9565ED"/>
        <circle cx="262" cy="211" r="3" fill="#F6EEFF"/><circle cx="273" cy="218" r="3" fill="#C1FFED"/>
        <path d="m322 212 22-13m-22 22 15-9" stroke="#D8C5FF" strokeWidth="2" strokeLinecap="round"/>
      </g>
      <circle cx="121" cy="292" r="5" fill="#72D3C1" stroke="var(--surface)" strokeWidth="3"/>
      <circle cx="445" cy="156" r="4" fill="#B092F3"/>
      <circle className="wx-startup-orbit-spark" cx="414" cy="190" r="6" fill="#B692FB" stroke="var(--surface)" strokeWidth="3"/>
      <path d="m180 105 4-8 4 8-4 8Zm218 10 3-6 3 6-3 6Z" fill="var(--startup-spark)"/>
      {interrupted && <g className="wx-startup-disconnect"><circle cx="420" cy="278" r="18" fill="var(--startup-warning-bg)" stroke="var(--startup-warning-line)"/><path d="m420 269-8 15h16Z" fill="none" stroke="var(--startup-warning)" strokeWidth="1.8" strokeLinejoin="round"/><path d="M420 274v4m0 3h.01" stroke="var(--startup-warning)" strokeWidth="1.8" strokeLinecap="round"/></g>}
    </svg>
    <div className="wx-startup-node wx-startup-node--metadata"><span className="wx-startup-node-icon"><svg viewBox="0 0 24 24"><ellipse cx="12" cy="6" rx="7" ry="3"/><path d="M5 6v11c0 4 14 4 14 0V6M5 11c0 4 14 4 14 0"/></svg></span><span>元数据<small>结构与目录</small></span><i/></div>
    <div className="wx-startup-node wx-startup-node--standards"><span className="wx-startup-node-icon"><svg viewBox="0 0 24 24"><path d="M7 3h8l4 4v14H5V3h2ZM14 3v5h5m-11 7 3 3 5-6"/></svg></span><span>数据标准<small>统一业务定义</small></span><i/></div>
    <div className="wx-startup-node wx-startup-node--quality"><span className="wx-startup-node-icon"><svg viewBox="0 0 24 24"><path d="m12 3 8 4v6c0 5-8 8-8 8s-8-3-8-8V7Zm-4 9 3 3 5-6"/></svg></span><span>数据质量<small>可信治理</small></span><i/></div>
    <div className="wx-startup-node wx-startup-node--service"><span className="wx-startup-node-icon"><svg viewBox="0 0 24 24"><rect x="4" y="4" width="16" height="6" rx="2"/><rect x="4" y="14" width="16" height="6" rx="2"/><path d="M8 7h.01M8 17h.01m4-10h5m-5 10h5"/></svg></span><span>后端服务<small>{interrupted ? '连接待恢复' : '安全连接'}</small></span><i/></div>
    <span className="wx-startup-core-caption">治理核心<span>DATA GOVERNANCE</span></span>
  </div>;
}

function ResourceConnectionArt({ interrupted }: { interrupted: boolean }) {
 const id = useId().replace(/:/g, '');
 return <div className="wx-startup-art ps-startup-art" aria-hidden="true">
  <svg viewBox="0 0 600 400" fill="none" focusable="false">
   <defs>
    <linearGradient id={`${id}-surface`} x1="225" y1="118" x2="370" y2="315" gradientUnits="userSpaceOnUse"><stop stopColor="#D6EDFF"/><stop offset=".5" stopColor="#76AEFA"/><stop offset="1" stopColor="#3465C7"/></linearGradient>
    <linearGradient id={`${id}-side`} x1="215" y1="200" x2="385" y2="305" gradientUnits="userSpaceOnUse"><stop stopColor="#4D83DD"/><stop offset="1" stopColor="#24499A"/></linearGradient>
    <radialGradient id={`${id}-halo`}><stop stopColor="#83BDFF" stopOpacity=".28"/><stop offset="1" stopColor="#83BDFF" stopOpacity="0"/></radialGradient>
   </defs>
   <ellipse cx="300" cy="239" rx="229" ry="160" fill={`url(#${id}-halo)`}/>
   <g className="wx-startup-art-grid" stroke="var(--startup-art-line)"><path d="m82 300 218-126 218 126M120 327l180-103 180 103M161 351l139-80 139 80M82 300l218 125m-158-160 218 125m-158-160 218 125m-158-160 218 125"/></g>
   <ellipse cx="300" cy="317" rx="167" ry="37" fill="var(--startup-shadow)"/>
   <path d="m137 294 163-95 163 95v23l-163 94-163-94Z" fill="var(--startup-platform-side)"/>
   <path d="m137 294 163-95 163 95-163 94Z" fill="var(--startup-platform-top)" stroke="var(--startup-platform-stroke)"/>
   <path d="m170 295 130-75 130 75-130 75Z" stroke="var(--startup-platform-stroke)" opacity=".6"/>
   <g stroke="var(--startup-connector)" strokeWidth="2" strokeLinecap="round"><path d="M128 111v61q0 18 18 18h76M472 95v71q0 19-22 19h-63M120 293v-52q0-19 22-19h78"/>{interrupted?<path d="M380 233h36v48" strokeDasharray="5 7"/>:<path d="M380 233h36v71q0 18 18 18h40"/>}</g>
   <g className="wx-startup-data-flow" stroke="var(--startup-pulse)" strokeWidth="3" strokeDasharray="3 105" strokeLinecap="round"><path d="M128 111v61q0 18 18 18h76M472 95v71q0 19-22 19h-63M120 293v-52q0-19 22-19h78"/></g>
   {[0,1,2].map((level) => <g key={level} className="ps-startup-level" style={{ animationDelay: `${level * .22}s` }} transform={`translate(0 ${level * 40})`}>
    <path d="m207 174 93-54 93 54v31l-93 54-93-54Z" fill={`url(#${id}-side)`} stroke="#8AB7F2" strokeOpacity=".55"/>
    <path d="m207 174 93-54 93 54-93 54Z" fill={`url(#${id}-surface)`} stroke="#D4EAFF" strokeOpacity=".8"/>
    <path d="m228 174 72-42 72 42-72 42Z" stroke="#EDF7FF" strokeOpacity=".7"/>
    <path d="m220 204 80 46 80-46" stroke="#A6D1FF" strokeOpacity=".75"/>
   </g>)}
   <path d="M300 130v105" stroke="#F3FAFF" strokeWidth="2" strokeDasharray="5 8" opacity=".65"/>
   <circle className="wx-startup-orbit-spark" cx="462" cy="205" r="6" fill="#65CFE0"/>
   <circle cx="145" cy="223" r="4" fill="#90B9FA"/>
  </svg>
  <div className="wx-startup-node wx-startup-node--metadata"><span className="wx-startup-node-icon">▤</span><span>数仓分层<small>按层组织资源</small></span><i/></div>
  <div className="wx-startup-node wx-startup-node--standards"><span className="wx-startup-node-icon">▦</span><span>数据模型<small>融合与物化</small></span><i/></div>
  <div className="wx-startup-node wx-startup-node--quality"><span className="wx-startup-node-icon">◇</span><span>资源目录<small>编目与审核</small></span><i/></div>
  <div className="wx-startup-node wx-startup-node--service"><span className="wx-startup-node-icon">↔</span><span>统一服务<small>{interrupted ? '连接待恢复' : '安全连接'}</small></span><i/></div>
  <span className="wx-startup-core-caption">资源建设<span>RESOURCE CENTER</span></span>
 </div>;
}

const resourceStartupPhases = {
 session: { title: '正在准备资源工作空间', description: '正在建立安全连接，读取您的账户与租户信息。', activity: '正在连接身份与后端服务', step: 0 },
 workspace: { title: '正在打开资源工作空间', description: '正在读取数仓规划、数据模型与资源目录。', activity: '正在读取资源配置', step: 2 },
 module: { title: '正在准备资源页面', description: '正在装载分层、分库与资源视图，请稍候。', activity: '正在加载资源视图', step: 2 },
 redirect: { title: '正在返回登录页面', description: '当前登录状态已失效，请在主平台重新登录后继续。', activity: '正在前往统一登录入口', step: -1 },
} satisfies typeof startupPhases;

export default function StartupScene(props: StartupSceneProps) {
  const resource = typeof window !== 'undefined' && /^#\/resource(?:\/|$)/.test(window.location.hash);
  const failure = props.state === 'error' ? startupFailure(props.error) : null;
  // Authentication failures still follow the existing framework redirect, not a service-error screen.
  const loading = props.state === 'loading' || failure?.kind === 'authentication';
  const phase = props.state === 'loading' ? props.phase ?? 'session' : 'redirect';
  const progress = resource ? resourceStartupPhases[phase] : startupPhases[phase];
  const titleId = useId();
  const descriptionId = useId();
  return <section className={`wx-startup-scene${resource ? ' is-resource' : ''}${props.contained ? ' is-contained' : ''}${loading ? ' is-loading' : ' is-error'}`} aria-labelledby={titleId} aria-describedby={descriptionId} aria-busy={loading} data-state={loading ? 'loading' : 'error'} data-failure-kind={loading ? undefined : failure?.kind}>
    {!props.contained && <header className="wx-startup-brand">{resource ? <PanshiLogo/> : <BrandMark/>}<div><b>{resource ? '数据资源中心' : '数据治理中心'}</b><span>{resource ? '统筹规划 · 有序建设' : '统一连接 · 可信治理'}</span></div></header>}
    <div className="wx-startup-main">
      <div className="wx-startup-panel">
        <div className="wx-startup-visual">{resource ? <ResourceConnectionArt interrupted={!loading}/> : <GovernanceConnectionArt interrupted={!loading}/>}</div>
        <div className="wx-startup-copy">
          <div className="wx-startup-state-label"><span className="wx-startup-state-dot"/>{loading ? resource ? '资源工作空间' : '治理工作空间' : '服务连接异常'}</div>
          <div role={loading ? undefined : 'alert'}>
            <h1 id={titleId}>{loading ? progress.title : resource ? failure?.title.replace('治理工作空间','资源工作空间') : failure?.title}</h1>
            <p id={descriptionId} className="wx-startup-description">{loading ? progress.description : resource ? failure?.description.replace('治理工作空间','资源工作空间').replace('元数据、数据标准与质量治理','分层、模型与资源目录') : failure?.description}</p>
          </div>
          {loading ? <div className="wx-startup-loading-status" role="status" aria-live="polite">
            <div className="wx-startup-loading-track" aria-hidden="true"><span/></div>
            <div className="wx-startup-activity"><span className="wx-startup-activity-pulse"/>{progress.activity}<span className="wx-startup-ellipsis" aria-hidden="true">···</span></div>
            <p>无需重复刷新，连接完成后将自动进入。</p>
          </div> : <>
            <div className="wx-startup-diagnostic"><span className="wx-startup-diagnostic-icon" aria-hidden="true">!</span><div><b>{failure?.reason}</b><p>{failure?.advice}</p></div></div>
            <div className="wx-startup-actions"><Button type="primary" size="large" icon={<ReloadOutlined/>} onClick={props.state === 'error' ? props.onRetry : undefined}>重新连接</Button><details className="wx-startup-details"><summary>查看连接详情<ArrowRightOutlined aria-hidden="true"/></summary><p>{failure?.detail}</p></details></div>
          </>}
        </div>
      </div>
      {loading && phase !== 'redirect' ? <ol className="wx-startup-steps" aria-label="工作空间初始化阶段">
        {['连接服务', '读取身份', '准备工作空间'].map((step, i) => <li key={step} className={i === progress.step ? 'is-current' : i < progress.step ? 'is-complete' : ''} aria-current={i === progress.step ? 'step' : undefined}><span className="wx-startup-step-number">0{i + 1}</span><div><b>{step}</b><span>{(resource ? ['建立统一服务连接', '验证账户与租户信息', '加载资源配置与目录'] : ['建立统一服务连接', '验证账户与租户信息', '加载治理配置与视图'])[i]}</span></div><span className="wx-startup-step-line"/></li>)}
      </ol> : <footer className="wx-startup-footer"><span>{resource ? '数仓规划' : '元数据管理'}</span><i/><span>{resource ? '数据模型' : '数据标准'}</span><i/><span>{resource ? '资源目录' : '数据质量'}</span><small>{phase === 'redirect' && loading ? `完成统一登录后，即可继续${resource ? '资源建设' : '治理工作'}` : `连接恢复后，即可继续${resource ? '资源建设' : '治理工作'}`}</small></footer>}
    </div>
    <span className="wx-startup-bottom-note">{resource ? 'RESOURCE CENTER · 让每一份资源有序可用' : 'DATA GOVERNANCE · 让每一项治理结果清晰可见'}</span>
  </section>;
}
