import Icon from './Icon';
import ProgressOverlay from './ProgressOverlay';
export interface UploadStage {phase:0|1|2;completed:number;total:number}
export default function UploadProgress({stage}:{stage:UploadStage}){
  const titles=['正在上传数据文件','正在识别表头与数据表','正在准备登记信息'];
  return <ProgressOverlay title={titles[stage.phase]} description={stage.phase===0?`已处理 ${stage.completed} / ${stage.total} 份文件`:'请稍候，完成后即可规范数据表'} icon="upload">
    <ol>{['上传原件','识别数据表','准备登记'].map((title,index)=><li key={title} className={index<stage.phase?'is-done':index===stage.phase?'is-current':''}><b>{index<stage.phase?<Icon name="check" size={12}/>:index+1}</b>{title}</li>)}</ol>
  </ProgressOverlay>;
}
