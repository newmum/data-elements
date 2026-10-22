/// <reference lib="webworker" />
import { parseSource } from './import';
self.onmessage = async (e: MessageEvent<{
    file: File;
    encoding: string;
    delimiter: string;
}>) => { try {
    self.postMessage({ ok: true, book: await parseSource(e.data.file, e.data.encoding, e.data.delimiter) });
}
catch (error) {
    self.postMessage({ ok: false, error: error instanceof Error ? error.message : '文件读取失败' });
} };
