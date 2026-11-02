import { getSessionRevision } from '../../nifi/api/bridgeSession';
import { loadUploadManifest,type UploadContext } from './uploadWorkbench';

/** A source selection reuses the authenticated context and reads one workspace. */
export function createReportSourceLoader(read=loadUploadManifest,revision=getSessionRevision){
  let latest=0;
  return {
    cancel(){latest++;},
    async load(id:string,sources:UploadContext['sources']){
      const source=sources.find(item=>item.id===id);
      if(!source)throw new Error('当前数据源不存在或不在可选范围内');
      const request=++latest,session=revision();
      const current=()=>request===latest&&session===revision();
      try{
        const manifest=await read(id);
        if(!current())return;
        return {source,manifest:{...manifest,failedTables:manifest.failedTables||[],receipts:manifest.receipts||[]}};
      }catch(cause){if(current())throw cause;}
    },
  };
}
