function decimalIdentity(text:string):string {
  const [mantissa,exponent='0']=text.trim().toLowerCase().split('e');
  const sign=mantissa.startsWith('-')?'-':'';
  const unsigned=mantissa.replace(/^[+-]/,'');
  const fraction=unsigned.split('.')[1]?.length??0;
  let digits=unsigned.replace('.','').replace(/^0+/,'');
  if(!digits)return '0';
  const trailing=digits.match(/0+$/)?.[0].length??0;
  if(trailing)digits=digits.slice(0,-trailing);
  return sign+digits+'e'+(Number(exponent)-fraction+trailing);
}
export function parseNumericConstant(text:string):number {
  if(!/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(text.trim()))throw new Error('请输入有效数字');
  const value=Number(text),digits=text.replace(/[eE].*$/,'').replace(/[^\d]/g,'').replace(/^0+/,'');
  if(!Number.isFinite(value)||digits.length>15||Number.isInteger(value)&&!Number.isSafeInteger(value)||decimalIdentity(text)!==decimalIdentity(String(value)))throw new Error('该数值超出浏览器精确范围，请用文本常量并设置精确小数目标类型');
  return value;
}
function* numericTokens(text:string):Generator<{index:number;token:string}> {
  for(let index=0;index<text.length;index++){
    if(text[index]==='"'){
      for(index++;index<text.length;index++){if(text[index]==='\\'){index++;continue;}if(text[index]==='"')break;}
      continue;
    }
    if(text[index]==='-'||/[0-9]/.test(text[index])){
      const token=text.slice(index).match(/^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?/);
      if(token){yield {index,token:token[0]};index+=token[0].length-1;}
    }
  }
}
export function assertExactJsonNumbers(text:string):void { for(const {token} of numericTokens(text))parseNumericConstant(token); }
/** Keep exact server decimal/integer tokens as text when JavaScript cannot preserve them. */
export function parsePreciseJson(text:string):unknown {
  const pieces:string[]=[];let last=0;
  for(const {index,token} of numericTokens(text)){
    try{parseNumericConstant(token);}catch{pieces.push(text.slice(last,index),JSON.stringify(token));last=index+token.length;}
  }
  pieces.push(text.slice(last));return JSON.parse(pieces.join(''));
}
export function preciseJsonResponse(data:unknown):unknown {
  if(typeof data!=='string')return data;
  try{return parsePreciseJson(data);}catch{return data;}
}
