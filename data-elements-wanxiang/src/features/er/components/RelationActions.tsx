import { Alert, App } from 'antd';
import type { Relationship } from '../types/domain';
import { useStudio } from '../store';
import { errorText } from '../core/model';
import { canConfirmRelationship } from '../core/reviewSelection';
export function useRelationActions(){
  const {modal,message}=App.useApp();
  function confirmMany(relations:Relationship[],after?:()=>void){
    const items=relations.filter(r=>r.origin!=='catalog'&&r.reviewStatus==='suggested');
    if(!items.length)return;
    const ids=[...new Set(items.map(r=>r.id))];
    const risk=items.filter(r=>r.verification?.status==='violations_found'||r.recommendation?.level==='conflict'||r.lifecycle==='broken');
    modal.confirm({title:items.length===1?'确认这条逻辑关系？':`确认 ${items.length} 条逻辑关系？`,icon:null,okText:'确认逻辑关系',cancelText:'取消',width:480,content:<div className="confirmation-body"><p>确认后将保存到共享后端并显示为实线。此操作会影响共享该关系的其他视图，但不会修改源数据库。</p>{risk.length>0&&<Alert type="warning" showIcon title={`${risk.length} 条关系存在异常或风险`} description={risk.map(r=>`${r.name}：${r.verification?.status==='violations_found'?'数据检查存在未匹配项':'需要复核'}`).join('；')}/>}<p className="subtle-note">确认业务含义不会将“未验证”变为“验证通过”。</p></div>,onOk:async()=>{
      const latest=useStudio.getState().data?.relationships??[];
      const pending=ids.map(id=>latest.find(r=>r.id===id)).filter((r):r is Relationship=>!!r&&r.origin!=='catalog'&&r.reviewStatus==='suggested');
      let confirmed=0;
      try{
        if(pending.some(r=>!canConfirmRelationship(r)))throw new Error('存在字段失效关系，请先修复后再确认。');
        for(const r of pending){await useStudio.getState().decide(r.id,'confirmed');confirmed++;}
        message.success(confirmed?`已确认 ${confirmed} 条逻辑关系。`:'所选关系已处理，无需重复确认。');after?.();
      }catch(e){message.error(confirmed?`已确认 ${confirmed} 条，其余未完成：${errorText(e)}`:errorText(e));throw e;}
    }});
  }
  function archive(r:Relationship){modal.confirm({title:'归档这条逻辑关系？',icon:null,content:'此操作会从当前工作区所有视图移除该逻辑关系，但不删除数据表。历史记录会保留，可在关系列表的“已归档”中恢复。',okText:'归档关系',okButtonProps:{danger:true},cancelText:'取消',onOk:async()=>{await useStudio.getState().decide(r.id,'archived');message.success('关系已归档。');}});}
  function remove(r:Relationship){modal.confirm({title:'删除这条共享逻辑关系？',icon:null,content:`将通过共享接口移除当前租户的关系台账记录，其他视图也不再显示；不删除来源表、字段或业务记录。${r.physicalForeignKey?'此关系已创建物理外键：删除逻辑关系不会撤销数据库约束。如需撤销，请先由数据库维护人员执行已提供的回退 SQL。':''}`,okText:'删除逻辑关系',okButtonProps:{danger:true},cancelText:'取消',onOk:async()=>{await useStudio.getState().removeRelationship(r.id);message.success('共享逻辑关系已删除。');}});}
  return {confirmMany,archive,remove};
}
