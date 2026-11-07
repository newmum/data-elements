import { useMemo } from 'react';
import type { EditorView } from '@codemirror/view';
import CodeMirror from '@uiw/react-codemirror';
import { MySQL, sql } from '@codemirror/lang-sql';
import { fillVisibleSqlRows } from './sqlEditorPadding';

export interface SqlWorkspaceEditorProps {
  value: string;
  onChange: (value: string) => void;
  theme: 'light' | 'dark';
  schema: Record<string, string[]>;
  height?: string;
  onCreateEditor?: (view: EditorView) => void;
}

/** onChange includes whitespace-only viewport padding, safe for SQL execution. */
export function SqlWorkspaceEditor({ value, onChange, theme, schema, height = '100%', onCreateEditor }: SqlWorkspaceEditorProps) {
  const extensions = useMemo(() => [sql({ dialect: MySQL, schema }), fillVisibleSqlRows], [schema]);
  return <CodeMirror
    theme={theme}
    value={value}
    height={height}
    extensions={extensions}
    onChange={onChange}
    onCreateEditor={onCreateEditor}
    basicSetup={{ lineNumbers: true, highlightActiveLine: true, foldGutter: true, autocompletion: true }}
  />;
}

export default SqlWorkspaceEditor;
