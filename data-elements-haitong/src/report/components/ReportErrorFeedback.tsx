import {useState} from 'react';
import type {ReactNode} from 'react';
import {Alert,Button} from 'antd';
import {CopyOutlined} from '@ant-design/icons';
import {diagnosticText,platformErrorInfo,type PlatformErrorInfo} from '../../api/platformError';
import LegacyDialog from './LegacyDialog';
import './ReportErrorFeedback.css';

export function ReportErrorDetails({info,onClose}:{info?:PlatformErrorInfo;onClose:()=>void}) {
  const [copyState,setCopyState]=useState('');
  async function copy(){
    if(!info)return;
    try{
      const text=diagnosticText(info);
      if(navigator.clipboard&&window.isSecureContext)await navigator.clipboard.writeText(text);
      else{
        // Production may use an intranet HTTP origin where Clipboard API is unavailable.
        const previous=document.activeElement,field=document.createElement('textarea');
        field.value=text;field.readOnly=true;field.style.cssText='position:fixed;left:-9999px;top:0';
        document.body.appendChild(field);
        try{field.select();if(!document.execCommand('copy'))throw new Error('Copy rejected');}
        finally{field.remove();if(previous instanceof HTMLElement)previous.focus();}
      }
      setCopyState('已复制');
    }
    catch{setCopyState('复制未完成，请选中下方文字复制');}
  }
  return <LegacyDialog open={!!info} onClose={onClose} title="错误详情" className="report-error-dialog"
    footer={<><span className="report-error-copy-state" role="status">{copyState}</span><Button icon={<CopyOutlined/>} onClick={()=>void copy()}>复制错误信息</Button><Button type="primary" onClick={onClose}>关闭</Button></>}>
    {info&&<><dl className="report-error-metadata">
      {([['错误提示',info.message],['具体原因',info.reason],['错误码',info.code],['跟踪号',info.traceId],['请求',[info.method,info.path].filter(Boolean).join(' ')],['异常类型',info.type]] as const)
        .filter(([,value])=>!!value).map(([label,value])=><div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}
      </dl><h3 className="report-error-detail-title">完整错误信息</h3><pre className="report-error-detail-text" tabIndex={0}>{info.detail||info.message}</pre></>}
  </LegacyDialog>;
}

export default function ReportErrorFeedback({error,title,onClose,action}:{error:unknown;title?:string;onClose?:()=>void;action?:ReactNode}) {
  const [open,setOpen]=useState(false),info=platformErrorInfo(error);
  const detailed=!!(info.code||info.traceId||info.detail&&info.detail!==info.message);
  return <><Alert className="report-error-feedback" type="error" showIcon closable={!!onClose} onClose={onClose}
    message={title||info.message} description={title?<><span>{info.reason||info.message}</span>{info.code&&<small>错误码：{info.code}</small>}</>:info.reason&&info.reason!==info.message?<span>{info.reason}</span>:undefined}
    action={<div className="report-error-actions">{detailed&&<Button type="link" onClick={()=>setOpen(true)}>查看详情</Button>}{action}</div>}/>
    <ReportErrorDetails key={open?'open':'closed'} info={open?info:undefined} onClose={()=>setOpen(false)}/></>;
}
