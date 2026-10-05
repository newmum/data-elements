// Format-only validation. This worker never requests a number or reads business data.
self.onmessage = event => {
 const {pattern,value}=event.data;
 try { const expression=new RegExp(pattern);const matched=expression.exec(value);self.postMessage({valid:!!matched&&matched.index===0&&matched[0].length===value.length}); }
 catch { self.postMessage({error:'格式规则不是有效的正则表达式，请在数据元中修正'}); }
};
export {};
