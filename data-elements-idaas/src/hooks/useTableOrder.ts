import { useState } from 'react';
import type { SortOrder } from 'antd/es/table/interface';

/** Controlled table state; changing order always requests page one from the server. */
export function useTableOrder() {
    const [page, setPage] = useState(1), [size, setSize] = useState(20);
    const [sort, setSort] = useState<{sortField?: string; sortDir?: 'asc' | 'desc'}>({});
    const params = new URLSearchParams({paged:'true',page:String(page),size:String(size)});
    if (sort.sortField) { params.set('sortField',sort.sortField); params.set('sortDir',sort.sortDir!); }
    return {
        query:params.toString(),page,size,reset:()=>setPage(1),
        column:(key:string)=>({key,sorter:true,sortOrder:(sort.sortField===key?(sort.sortDir==='asc'?'ascend':'descend'):null) as SortOrder}),
        onChange:(_pagination:unknown,_filters:unknown,sorter:any,extra:{action:string})=>{
            if(extra.action!=='sort')return;
            const selected=Array.isArray(sorter)?sorter[0]:sorter;
            setSort(selected.order?{sortField:String(selected.columnKey??selected.field),sortDir:selected.order==='ascend'?'asc':'desc'}:{});
            setPage(1);
        },
        pagination:(total:number)=>({current:page,pageSize:size,total,showSizeChanger:true,pageSizeOptions:[20,50,100],onChange:(next:number,amount:number)=>{setPage(amount===size?next:1);setSize(amount);}}),
    };
}
