/** A resource-shaped placeholder while the shared resource snapshot or route loads. */
export function ResourceLoading() {
 return <section className="ps-loading-scene" aria-busy="true" aria-label="正在加载数据资源中心">
  <div className="ps-loading-heading" role="status" aria-live="polite">
   <span className="ps-loading-emblem" aria-hidden="true"><i/><i/><i/></span>
   <div><strong>正在整理资源工作台</strong><span>读取分层、分库、模型与目录信息…</span></div>
  </div>
  <div className="ps-loading-hero" aria-hidden="true">
   <div className="ps-loading-hero-copy"><span className="ps-loading-bar short"/><span className="ps-loading-bar title"/><span className="ps-loading-bar long"/><span className="ps-loading-bar medium"/></div>
   <div className="ps-loading-flow"><i/><i/><i/><span/></div>
  </div>
  <div className="ps-loading-metrics" aria-hidden="true">{[0,1,2,3].map(index => <div key={index}><span className="ps-loading-bar medium"/><span className="ps-loading-bar value"/><span className="ps-loading-bar short"/></div>)}</div>
  <div className="ps-loading-table" aria-hidden="true"><div className="ps-loading-table-toolbar"><span className="ps-loading-bar medium"/><span className="ps-loading-bar short"/></div><div className="ps-loading-table-head"/>{[0,1,2,3,4].map(index => <div className="ps-loading-table-row" key={index}><span className="ps-loading-bar medium"/><span className="ps-loading-bar short"/><span className="ps-loading-bar medium"/><span className="ps-loading-bar short"/></div>)}</div>
 </section>;
}
