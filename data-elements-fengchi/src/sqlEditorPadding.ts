import { Transaction, type EditorState, type TransactionSpec } from '@codemirror/state';
import { EditorView, ViewPlugin, type ViewUpdate } from '@codemirror/view';

// Only fill the physical viewport, never the scrolled document height.
export const MAX_VIEWPORT_ROWS = 200;

export function viewportRowCount(height: number, lineHeight: number, padding = 0): number {
  if (!Number.isFinite(height) || !Number.isFinite(lineHeight) || height <= 0 || lineHeight <= 0) return 1;
  return Math.min(MAX_VIEWPORT_ROWS, Math.max(1, Math.ceil((height - padding) / lineHeight)));
}

export function blankRowTransaction(state: EditorState, rows: number): TransactionSpec | null {
  const missing = Math.min(MAX_VIEWPORT_ROWS, Math.max(1, Math.floor(rows))) - state.doc.lines;
  if (!Number.isFinite(missing) || missing <= 0) return null;
  return {
    changes: { from: state.doc.length, insert: '\n'.repeat(missing) },
    // In particular, leave an EOF caret before the newly appended newlines.
    selection: state.selection,
    annotations: Transaction.addToHistory.of(false),
    scrollIntoView: false,
  };
}

/** Real editable rows: CodeMirror retains its normal cursor, gutter and history behavior. */
export const fillVisibleSqlRows = ViewPlugin.fromClass(class {
  private observer: ResizeObserver;
  private frame = 0;
  private destroyed = false;

  constructor(private view: EditorView) {
    this.observer = new ResizeObserver(() => this.schedule());
    this.observer.observe(view.scrollDOM);
    this.schedule();
  }

  update(update: ViewUpdate) {
    if (update.docChanged || update.geometryChanged || update.viewportChanged) this.schedule();
  }

  private schedule() {
    if (this.frame || this.destroyed) return;
    this.frame = requestAnimationFrame(() => {
      this.frame = 0;
      if (this.destroyed) return;
      this.view.requestMeasure({
        key: this,
        read: view => {
          const style = getComputedStyle(view.contentDOM);
          const padding = (parseFloat(style.paddingTop) || 0) + (parseFloat(style.paddingBottom) || 0);
          return viewportRowCount(view.scrollDOM.clientHeight, view.defaultLineHeight, padding);
        },
        write: rows => {
          // A measure write runs inside CodeMirror's update. Defer the transaction
          // until that update has finished and use the latest document/selection.
          queueMicrotask(() => {
            if (this.destroyed || this.view.composing) return;
            const transaction = blankRowTransaction(this.view.state, rows);
            if (transaction) this.view.dispatch(transaction);
          });
        },
      });
    });
  }

  destroy() {
    this.destroyed = true;
    this.observer.disconnect();
    cancelAnimationFrame(this.frame);
  }
});
