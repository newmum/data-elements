import type { ReactNode } from 'react';
import Icon from './Icon';

/** Shared frosted loading surface; operation text always reflects real work. */
export default function ProgressOverlay({title,description,icon='table',children,keepVisible=false}:{
  title:string;description:string;icon?:string;children?:ReactNode;keepVisible?:boolean;
}){
  return <div className={`report-upload-progress${keepVisible?' report-progress-keep-visible':''}`} role="status" aria-live="polite" aria-label={title}>
    <div className="report-progress-content">
      <div className="upload-progress-symbol" aria-hidden="true"><i/><i/><span><Icon name={icon} size={30}/></span></div>
      <div className="upload-progress-copy"><strong>{title}</strong><span>{description}</span>{children}</div>
    </div>
  </div>;
}
