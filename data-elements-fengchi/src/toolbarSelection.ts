/** The toolbar reflects the visible workspace, not the last action button pressed. */
export function activeToolbarSection({modelingOpen,tab,objectType}: {
 modelingOpen:boolean;
 tab?:{kind:'table'|'query'|'objects'|'functions';toolbarSection?:'视图'};
 objectType?:string;
}):'表'|'视图'|'模型'|'函数'|'查询'|undefined {
 if(modelingOpen)return '模型';
 if(!tab)return undefined;
 if(tab.kind==='functions')return '函数';
 if(tab.kind==='query')return tab.toolbarSection||'查询';
 if(tab.kind==='table'&&objectType==='VIEW')return '视图';
 return '表';
}
