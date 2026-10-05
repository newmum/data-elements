import { MockError } from './types';
function values(text:string,min:number,max:number):Set<number>{
  const set=new Set<number>();for(const part of text.split(',')){const [range,stepText]=part.split('/');const step=stepText===undefined?1:Number(stepText);if(!Number.isInteger(step)||step<1)throw new MockError(422,'CRON_INVALID','Cron 步长必须为正整数');let lo:number,hi:number;
    if(range==='*'||range==='?'){lo=min;hi=max;}else if(range.includes('-')){[lo,hi]=range.split('-').map(Number);}else{lo=Number(range);hi=stepText===undefined?lo:max;}
    if(!Number.isInteger(lo)||!Number.isInteger(hi)||lo<min||hi>max||hi<lo)throw new MockError(422,'CRON_INVALID','Cron 字段超出允许范围');for(let n=lo;n<=hi;n+=step)set.add(n);
  }return set;
}
/** Local schedule, minute-wise search with configured seconds; never runs while the browser is closed. */
export function nextTimes(cron:string,timezone:string,from:number,count=5):string[]{
  const parts=cron.trim().split(/\s+/);if(parts.length!==6)throw new MockError(422,'CRON_INVALID','请输入六字段 Cron：秒 分 时 日 月 周');
  const ranges=[[0,59],[0,59],[0,23],[1,31],[1,12],[0,7]];const [sec,min,hour,day,month,week]=parts.map((s,i)=>values(s,...ranges[i] as [number,number]));if(week.has(7))week.add(0);
  let format:Intl.DateTimeFormat;try{format=new Intl.DateTimeFormat('en-US',{timeZone:timezone,year:'numeric',month:'numeric',day:'numeric',hour:'numeric',minute:'numeric',weekday:'short',hourCycle:'h23'});format.format(from);}catch{throw new MockError(422,'TIMEZONE_INVALID','请输入有效的 IANA 时区');}
  const weekdays=['Sun','Mon','Tue','Wed','Thu','Fri','Sat'];const out:string[]=[];let t=Math.floor(from/60000)*60000;
  for(let i=0;i<60000&&out.length<count;i++,t+=60000){const p=Object.fromEntries(format.formatToParts(t).map(x=>[x.type,x.value]));if(!min.has(Number(p.minute))||!hour.has(Number(p.hour))||!month.has(Number(p.month))||!day.has(Number(p.day))||!week.has(weekdays.indexOf(p.weekday)))continue;for(const s of [...sec].sort((a,b)=>a-b)){const ms=t+s*1000;if(ms>from){out.push(new Date(ms).toISOString());if(out.length===count)break;}}}
  if(!out.length)throw new MockError(422,'CRON_RANGE','未来约 41 天没有匹配时间；请缩短本地调度周期');return out;
}
