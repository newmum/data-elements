/** Display counters are independent from persistent query identity. */
export function nextQueryNumber(queries:readonly {name:string}[]):number {
 return Math.max(0,...queries.map(q=>Number(/^未命名查询 (\d+)$/.exec(q.name)?.[1]||0)))+1;
}
export function createQueryKey():string{return `query-${crypto.randomUUID()}`}
export function upsertSavedQuery<T extends {key:string}>(queries:readonly T[],query:T):T[]{
 const index=queries.findIndex(q=>q.key===query.key);
 return index<0?[...queries,query]:queries.map((q,i)=>i===index?query:q);
}
