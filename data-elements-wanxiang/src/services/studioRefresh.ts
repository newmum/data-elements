import { workspaceService } from '../features/er/services/workspaceService';
import { useStudio } from '../features/er/store';
/** Save the active view before refreshing the server-authoritative workspace. */
export async function refreshStudio() {
 await useStudio.getState().save();
 if(useStudio.getState().saveState==='error')throw new Error('当前模型未保存，请先处理保存错误');
 const data=await workspaceService.load();
 useStudio.setState({data,ready:true,saveState:'saved',loadError:null,past:[],future:[]});
 return data;
}
