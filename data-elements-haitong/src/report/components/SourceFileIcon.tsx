export default function SourceFileIcon({name,small=false}:{name:string;small?:boolean}){
  const extension=name.split('.').pop()?.toLowerCase()||'',text=['csv','txt','json'].includes(extension);
  return <span className={`document-icon ${text?'csv':'excel'}${small?' is-small':''}`} aria-hidden="true"><span>{text?extension.toUpperCase():'X'}</span></span>;
}
export const businessClass=(name='')=>({'业务表':'business','日志表':'log','字典表':'dictionary','过程表':'process','备份表':'backup'}[name]||'business');
