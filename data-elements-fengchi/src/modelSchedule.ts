export type ModelSchedule={frequency:'interval'|'daily'|'weekly'|'once';time:string;weekday:number;timezone:'UTC'|'Asia/Shanghai';startDate:string;endDate:string};
export const defaultModelSchedule:ModelSchedule={frequency:'interval',time:'09:00',weekday:1,timezone:'Asia/Shanghai',startDate:'',endDate:''};
export function scheduleConfig(value?:Partial<ModelSchedule>):ModelSchedule{return {...defaultModelSchedule,...value}}
function dateValid(s:string){return /^\d{4}-\d{2}-\d{2}$/.test(s)&&Number.isFinite(Date.parse(s+'T00:00:00Z'))&&new Date(s+'T00:00:00Z').toISOString().slice(0,10)===s}
export function scheduleError(p:ModelSchedule,interval:number):string{
 if(!['interval','daily','weekly','once'].includes(p.frequency))return '请选择有效调度频率';
 if(!['UTC','Asia/Shanghai'].includes(p.timezone))return '请选择有效时区';
 if(!/^([01]\d|2[0-3]):[0-5]\d$/.test(p.time))return '请填写有效执行时间';
 if(!Number.isInteger(p.weekday)||p.weekday<0||p.weekday>6)return '请选择有效星期';
 if(!Number.isInteger(interval)||interval<1||interval>10080)return '间隔须为 1–10080 分钟';
 if(p.startDate&&!dateValid(p.startDate)||p.endDate&&!dateValid(p.endDate))return '请填写有效日期';
 if(p.frequency==='once'&&!p.startDate)return '单次执行须指定日期';
 if(p.startDate&&p.endDate&&p.endDate<p.startDate)return '结束日期不能早于开始日期';return '';
}
/** Pure preview only. No timer, network request, or persisted runtime state. */
export function nextScheduleTimes(p:ModelSchedule,interval:number,now=new Date(),count=3):string[]{
 if(scheduleError(p,interval))return [];const offset=p.timezone==='Asia/Shanghai'?8*3600000:0;
 const floor=p.startDate?Date.parse(p.startDate+'T00:00:00Z')-offset:now.getTime();const ceiling=p.endDate?Date.parse(p.endDate+'T00:00:00Z')-offset+86400000:Infinity;
 const out:string[]=[];
 if(p.frequency==='interval'){let t=Math.max(now.getTime()+interval*60000,floor);for(let i=0;i<count&&t<ceiling;i++,t+=interval*60000)out.push(new Date(t).toISOString());return out}
 const [h,m]=p.time.split(':').map(Number);const base=new Date(Math.max(now.getTime(),floor)+offset);base.setUTCHours(0,0,0,0);
 for(let i=0;i<3700&&out.length<count;i++){const day=new Date(base.getTime()+i*86400000);const date=day.toISOString().slice(0,10);const t=day.getTime()+h*3600000+m*60000-offset;if(t>=ceiling)break;if(t<=now.getTime()||t<floor)continue;if(p.frequency==='weekly'&&day.getUTCDay()!==p.weekday)continue;if(p.frequency==='once'&&date!==p.startDate)continue;out.push(new Date(t).toISOString());if(p.frequency==='once')break}return out;
}
