import fs from 'node:fs/promises';
import {Workbook,SpreadsheetFile} from '@oai/artifact-tool';
const dir='D:/Sourcecode/data-elements/outputs/01a1b99c-9472-7461-bc08-07f1932cd997';
const data=JSON.parse(await fs.readFile('C:/Users/Administrator/.codex/private/data-elements/server-datasource-validation/handoff-inventory-with-credentials.private.json','utf8'));
const wb=Workbook.create(),sheet=wb.worksheets.add('服务器清单');
const headers=['序号','原责任 / 分类','原服务器名称','服务器IP','原账号','原密码','原操作系统','CPU(核)','内存(GB)','硬盘(GB)','原服务器部署及用途','原对应产品','本次连接结果','实测SSH账号','实测SSH密码','数据库 / 中间件与版本','本次开放入口','已登记库 / 数据源','数据源账号密码及验证状态','测试用途 / 问题','原表来源','复核时间（北京）'];
const widths=[6,18,24,20,18,25,19,13,15,15,32,26,30,18,25,48,38,54,48,50,30,24];
const start=6,last=start+data.records.length;
sheet.showGridLines=false;
const all=sheet.getRange(`A1:V${last}`);all.format.font={name:'Arial',size:10,color:'#151515'};all.format.fill='#FFFFFF';all.format.verticalAlignment='top';all.format.wrapText=true;
sheet.getRange('A2:V2').merge();sheet.getRange('A2').values=[['G区服务器管理表 · 数据源盘点与接入测试']];sheet.getRange('A2:V2').format.font={name:'Arial',size:15,bold:true,color:'#111111'};sheet.getRange('A2:V2').format.rowHeight=28;
sheet.getRange('A3:V3').merge();sheet.getRange('A3').values=[[`基于原服务器管理表及本次复核整理：可用入口 ${data.usable} 台；ping通但登录未验证 ${data.gray} 台（灰色）；未响应 ping ${data.excludedNoPing} 台已移除。复核时间 ${data.checkedAt.slice(0,19).replace('T',' ')}（北京时间）`]];sheet.getRange('A3:V3').format.rowHeight=24;
sheet.getRange('A4:V4').merge();sheet.getRange('A4').values=[['原账号 / 原密码为最早表格所载；实测SSH账号 / 密码为本次登录成功使用的凭据。数据源账号密码列同时标明已验证或本次未单独复核；空白表示未找到。']];sheet.getRange('A4:V4').format.rowHeight=24;
sheet.getRange('A5:V5').merge();sheet.getRange('A5').values=[['灰色：能ping通，既有账号尚未验证到可用登录入口。候选端口仅表示开放；未响应ping也可能是禁ping，不能据此删除现有系统数据源。']];sheet.getRange('A5:V5').format.rowHeight=24;
sheet.getRange(`A${start}:V${start}`).values=[headers];
sheet.getRange(`A${start+1}:V${last}`).values=data.records.map(r=>r.values.map((v,c)=>c===21?new Date(v.replace(' ','T')+'Z'):typeof v==='string'&&v.startsWith('=')?"'"+v:v));
const table=sheet.tables.add(`A${start}:V${last}`,true,'ServerList');table.style='TableStyleLight1';table.showBandedColumns=false;table.showFilterButton=true;
sheet.getRange(`A${start}:V${start}`).format={fill:'#FFFFFF',font:{name:'Arial',size:10,bold:true,color:'#111111'},verticalAlignment:'center',horizontalAlignment:'center',wrapText:true,rowHeight:36,borders:{bottom:{style:'medium',color:'#737373'}}};
const weighted=s=>[...String(s)].reduce((n,c)=>n+(c.charCodeAt(0)>255?2:1),0);
for(let i=0;i<data.records.length;i++){
 const rec=data.records[i],r=start+1+i;let lines=1;
 rec.values.forEach((v,c)=>{const n=String(v).split('\n').reduce((sum,line)=>sum+Math.max(1,Math.ceil(weighted(line)/(widths[c]*1.05))),0);lines=Math.max(lines,n)});
 sheet.getRange(`A${r}:V${r}`).format.rowHeight=Math.max(58,Math.min(408,lines*14+10));
 sheet.getRange(`A${r}:V${r}`).format.fill=rec.usable?'#FFFFFF':'#D9D9D9';
 sheet.getRange(`A${r}`).format.horizontalAlignment='center';
}
widths.forEach((w,i)=>sheet.getRangeByIndexes(0,i,last,1).format.columnWidth=w);
sheet.getRange(`V${start+1}:V${last}`).setNumberFormat('yyyy-mm-dd hh:mm');sheet.freezePanes.freezeRows(start);sheet.freezePanes.freezeColumns(4);
wb.recalculate();
const inspection=await wb.inspect({kind:'sheet',include:'id,name',maxChars:500});console.log(inspection.ndjson);
const errors=await wb.inspect({kind:'match',searchTerm:'#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A|#NUM!|#NULL!',options:{useRegex:true,maxResults:10},maxChars:300});console.log(errors.ndjson);
// Render only non-credential columns; do not create password-bearing previews.
const grey=start+1+data.usable;
for(const [name,range] of [['revised-left-preview','A1:D9'],['revised-gray-preview',`P${grey-1}:R${grey+2}`]]){
 const img=await wb.render({sheetName:'服务器清单',range,scale:1,format:'png'});await fs.writeFile(`${dir}/${name}.png`,new Uint8Array(await img.arrayBuffer()));
}
const output=await SpreadsheetFile.exportXlsx(wb);await output.save('D:/svn/08_实施部署/系统部署环境/G区服务器管理2025(新)_整理中.xlsx');
console.log('staged rows',data.records.length,'white',data.usable,'gray',data.gray);
