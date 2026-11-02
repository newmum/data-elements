import {Children,cloneElement,isValidElement,useEffect,useId,useLayoutEffect,useRef,useState,type CSSProperties,type HTMLAttributes,type ReactElement,type ReactNode} from 'react';
import {Select as AntSelect,type SelectProps as AntSelectProps} from 'antd';
import {createPortal} from 'react-dom';
import LegacyDialog from './LegacyDialog';

type Option<T>={value:T;label:ReactNode;disabled?:boolean;description?:string};
type SelectProps<T extends string|number> = Omit<AntSelectProps<T,Option<T>>,'value'|'options'|'onChange'> & {
  value?:T|null;options?:readonly Option<T>[];onChange?:(value:T)=>void;
};
const plainText=(value:ReactNode):string=>Children.toArray(value).map(item=>isValidElement<{children?:ReactNode}>(item)?plainText(item.props.children):typeof item==='string'||typeof item==='number'?String(item):'').join('');

/** Ant Design owns selection; this adapter preserves the report's value and clear contracts. */
export function Select<T extends string|number=string>({value,options=[],onChange,onClear,className='',disabled,loading,...rest}:SelectProps<T>){
  return <AntSelect<T,Option<T>> classNames={{popup:{root:'report-select-dropdown'}}} popupMatchSelectWidth={options.some(option=>option.description)?300:true}
    optionRender={option=><div className="report-standard-format-option"><span>{option.label}</span>{option.data.description&&<small>{option.data.description}</small>}</div>}
    {...rest} className={`report-select-control ${className}`} disabled={disabled||loading} loading={loading} value={value??undefined} options={[...options]}
    onChange={next=>{if(next===undefined){if(onClear){onClear();return;}}else if(!options.some(option=>option.value===next&&!option.disabled))return;onChange?.(next);}}/>;
}

/** Browser title tooltips cannot create a mask over a neighbouring control. */
export function Tooltip({title,children,...rest}:Omit<HTMLAttributes<HTMLElement>,'title'> & {title?:ReactNode;children:ReactNode}){
  const child=isValidElement(children)?children:<span>{children}</span>;
  return cloneElement(child,{...rest,title:plainText(title)||undefined} as Record<string,unknown>);
}

/** An explicitly positioned panel; no rc-trigger measurements or scale animation. */
export function Popover({children,content,title,placement='bottomLeft',open:controlled,onOpenChange,overlayClassName='',trigger:_trigger}: {
  children:ReactElement;content:ReactNode;title?:ReactNode;placement?:string;open?:boolean;onOpenChange?:(open:boolean)=>void;overlayClassName?:string;trigger?:string;
}){
  const [ownOpen,setOwnOpen]=useState(false),open=controlled??ownOpen,id=useId();
  const anchor=useRef<HTMLSpanElement>(null),panel=useRef<HTMLDivElement>(null);
  const [position,setPosition]=useState<CSSProperties>({visibility:'hidden'});
  const toggle=(next:boolean)=>{if(controlled===false&&next&&!onOpenChange)return;setOwnOpen(next);onOpenChange?.(next);};
  useEffect(()=>{if(controlled===false)setOwnOpen(false);},[controlled]);
  useLayoutEffect(()=>{
    if(!open)return;
    const place=()=>{if(!anchor.current||!panel.current)return;const a=anchor.current.getBoundingClientRect(),p=panel.current.getBoundingClientRect(),margin=10;
      const left=Math.max(margin,Math.min(placement.includes('Right')?a.right-p.width:a.left,window.innerWidth-p.width-margin));
      const top=a.bottom+6+p.height<=window.innerHeight-margin?a.bottom+6:Math.max(margin,a.top-p.height-6);
      setPosition({left,top,visibility:'visible'});
    };
    place();panel.current?.querySelector<HTMLElement>('button:not(:disabled),input:not(:disabled),select:not(:disabled)')?.focus();
    window.addEventListener('resize',place);document.addEventListener('scroll',place,true);
    const resize=new ResizeObserver(place);if(panel.current)resize.observe(panel.current);
    return()=>{resize.disconnect();window.removeEventListener('resize',place);document.removeEventListener('scroll',place,true);};
  },[open,placement]);
  useEffect(()=>{
    if(!open)return;
    const outside=(event:MouseEvent)=>{
      const target=event.target as Element;
      const dropdown=target.closest?.('.report-ant-select-dropdown');
      // A Select inside this panel portals its options to body to escape clipping.
      if(dropdown&&Array.from(panel.current?.querySelectorAll('[role="combobox"][aria-controls]')||[]).some(input=>document.getElementById(input.getAttribute('aria-controls')||'')?.closest('.report-ant-select-dropdown')===dropdown))return;
      if(!anchor.current?.contains(target)&&!panel.current?.contains(target))toggle(false);
    };
    document.addEventListener('mousedown',outside);return()=>document.removeEventListener('mousedown',outside);
  },[open]);
  return <><span ref={anchor} className="report-popup-anchor">{cloneElement(children,{'aria-expanded':open,'aria-controls':open?id:undefined,onClick:(event:React.MouseEvent)=>{
    (children.props as {onClick?:(event:React.MouseEvent)=>void}).onClick?.(event);if(!(children.props as {disabled?:boolean}).disabled)toggle(!open);
  }} as Record<string,unknown>)}</span>{open&&createPortal(<div id={id} ref={panel} className={`report-native-popup ${overlayClassName}`} style={position} role="dialog" aria-label={plainText(title)||'选项'} data-report-popup="true"
    onMouseDown={event=>event.stopPropagation()} onClick={event=>event.stopPropagation()} onKeyDown={event=>{
      if(event.key==='Escape'){event.preventDefault();event.stopPropagation();toggle(false);anchor.current?.querySelector<HTMLElement>('button')?.focus();}
      if(event.key==='Tab'){
        const targets=Array.from(panel.current?.querySelectorAll<HTMLElement>('button:not(:disabled),input:not(:disabled),select:not(:disabled)')||[]);
        const first=targets[0],last=targets[targets.length-1];
        if(event.shiftKey&&document.activeElement===first){event.preventDefault();last?.focus();}
        else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first?.focus();}
      }
    }}>
    {title&&<strong className="report-native-popup-title">{title}</strong>}{content}
  </div>,document.body)}</>;
}

export function Segmented<T extends string>({value,options,onChange,'aria-label':label}:{value:T;options:{value:T;label:ReactNode}[];onChange:(value:T)=>void;'aria-label'?:string}){
  return <div className="report-native-segmented" role="group" aria-label={label}>{options.map(option=><button key={option.value} type="button" aria-pressed={value===option.value} className={value===option.value?'is-active':''} onClick={()=>onChange(option.value)}>{option.label}</button>)}</div>;
}

export function Popconfirm({children,title,description,onConfirm,okText='确认',cancelText='取消',okButtonProps}:{children:ReactElement;title:ReactNode;description?:ReactNode;onConfirm:()=>void;okText?:string;cancelText?:string;okButtonProps?:{danger?:boolean}}){
  const [open,setOpen]=useState(false);
  return <>{cloneElement(children,{onClick:()=>{if(!(children.props as {disabled?:boolean}).disabled)setOpen(true);}} as Record<string,unknown>)}<LegacyDialog open={open} onClose={()=>setOpen(false)} title={title} className="report-dialog--narrow" footer={<><button type="button" className="report-native-button" onClick={()=>setOpen(false)}>{cancelText}</button><button type="button" className={`report-native-button${okButtonProps?.danger?' is-danger':''}`} onClick={()=>{setOpen(false);onConfirm();}}>{okText}</button></>}>{description}</LegacyDialog></>;
}

// Older report entry points use the same modal stack instead of library drawers.
export function Modal({open,onCancel,title,children,footer,width,extra}: {open:boolean;onCancel:()=>void;title:ReactNode;children:ReactNode;footer?:ReactNode;width?:number;extra?:ReactNode;destroyOnClose?:boolean}){
  return <LegacyDialog open={open} onClose={onCancel} title={title} className={width&&width>900?'report-dialog--wide':'report-dialog--medium'} footer={footer}>{extra}{children}</LegacyDialog>;
}
export function Drawer({onClose,...props}:Omit<Parameters<typeof Modal>[0],'onCancel'>&{onClose:()=>void}){return <Modal {...props} onCancel={onClose}/>;}
