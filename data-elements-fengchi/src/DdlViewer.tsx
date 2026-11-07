import CodeMirror from '@uiw/react-codemirror';
import {MySQL, sql} from '@codemirror/lang-sql';
import {EditorView} from '@codemirror/view';
import {HighlightStyle, syntaxHighlighting} from '@codemirror/language';
import {tags} from '@lezer/highlight';

export const ddlHighlightStyle = HighlightStyle.define([
  {tag: tags.keyword, class: 'ddl-token-keyword'},
  {tag: [tags.typeName, tags.standard(tags.name)], class: 'ddl-token-type'},
  {tag: tags.string, class: 'ddl-token-string'},
  {tag: tags.number, class: 'ddl-token-number'},
  {tag: tags.comment, class: 'ddl-token-comment'},
  {tag: [tags.operator, tags.punctuation], class: 'ddl-token-punctuation'},
]);
const extensions = [sql({dialect: MySQL}), EditorView.lineWrapping, syntaxHighlighting(ddlHighlightStyle)];

export default function DdlViewer({value, theme, compact = false, label}: {value: string; theme: 'light' | 'dark'; compact?: boolean; label: string}) {
  return <CodeMirror className={`ddl-code-viewer${compact ? ' compact' : ''}`} aria-label={label} value={value} theme={theme} editable={false} readOnly extensions={extensions} basicSetup={{lineNumbers: !compact, foldGutter: !compact, highlightActiveLine: false, highlightActiveLineGutter: false, autocompletion: false, bracketMatching: true, closeBrackets: false}}/>;
}
