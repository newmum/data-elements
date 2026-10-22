// Keep the parser as a real emitted worker file.  An inline worker is a blob:
// URL, so the lazy SheetJS import inside parseSource has no asset directory
// from which to resolve `./xlsx-*.js` in a production build.
import ParserWorker from '../core/parse.worker?worker';
import { parseSource } from '../core/import';
import { LIMITS } from '../core/model';
import type { SourceBook } from '../core/model';
export function readFile(file: File, encoding: string, delimiter: string, signal: AbortSignal): Promise<SourceBook> {
    if (window.__OFFLINE_PREVIEW__)
        return parseSource(file, encoding, delimiter).then(x => { if (signal.aborted)
            throw new Error('已取消读取'); return x; });
    return new Promise((resolve, reject) => { const worker = new ParserWorker(); const clean = () => { clearTimeout(timer); worker.terminate(); signal.removeEventListener('abort', cancel); }; const cancel = () => { clean(); reject(new Error('已取消读取')); }; const timer = setTimeout(() => { clean(); reject(new Error('读取超时，请拆分文件后重试')); }, LIMITS.parseMs); signal.addEventListener('abort', cancel, { once: true }); if (signal.aborted) {
        cancel();
        return;
    } worker.onmessage = e => { clean(); e.data.ok ? resolve(e.data.book) : reject(new Error(e.data.error)); }; worker.onerror = () => { clean(); reject(new Error('文件解析进程异常，请检查文件格式')); }; worker.postMessage({ file, encoding, delimiter }); });
}
