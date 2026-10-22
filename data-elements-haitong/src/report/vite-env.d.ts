/// <reference types="vite/client" />
declare global {
    interface Window {
        __OFFLINE_PREVIEW__?: boolean;
        __nativeReadWorkbook?: (data: ArrayBuffer) => Promise<import('./core/model').SourceSheet[]>;
    }
}
export {};
