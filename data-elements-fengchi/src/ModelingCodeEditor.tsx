import {useMemo} from 'react';
import CodeMirror from '@uiw/react-codemirror';
import {EditorState} from '@codemirror/state';
import {EditorView} from '@codemirror/view';
import {modelingLanguage,modelingCodeColors} from './modelingSyntax';
export type ModelingCodeEditorProps={value:string;language:'json'|'sql';label:string;readOnly?:boolean;height?:string;onChange?:(value:string)=>void};
/** Local text editor only: no SQL runner, API, or viewport-padding extension. */
export default function ModelingCodeEditor({value,language,label,readOnly=false,height='100%',onChange}:ModelingCodeEditorProps){
 const extensions=useMemo(()=>[modelingLanguage(language),modelingCodeColors,EditorView.lineWrapping,EditorView.contentAttributes.of({'aria-label':label}),EditorState.transactionFilter.of(tr=>!readOnly&&tr.newDoc.length>10000?[]:tr)], [language,label,readOnly]);
 return <div className="modeling-code-editor"><CodeMirror value={value} theme="none" height={height} extensions={extensions} readOnly={readOnly} editable={!readOnly} onChange={onChange} basicSetup={{lineNumbers:true,foldGutter:true,highlightActiveLine:!readOnly,highlightActiveLineGutter:!readOnly,autocompletion:false}}/></div>;
}
