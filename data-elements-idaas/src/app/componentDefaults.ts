import type { ConfigProviderProps } from 'antd';

/** Shared portal styling also applies to App.useApp() modals and notifications. */
export const compactComponents: Pick<ConfigProviderProps, 'drawer' | 'modal' | 'space'> = {
    drawer: {
        classNames: { body: 'iam-drawer-body', header: 'iam-drawer-header', footer: 'iam-drawer-footer', title: 'iam-panel-title' },
        styles: {
            wrapper: { maxWidth: '100vw' },
            header: { padding: '12px 16px', minHeight: 48 },
            body: { padding: 16, minWidth: 0 },
            footer: { padding: '12px 16px' },
        },
    },
    modal: {
        classNames: { title: 'iam-panel-title' },
        styles: {
            root: { maxWidth: 'calc(100vw - 24px)', top: 32 },
            container: { padding: 16 },
            header: { marginBottom: 12 },
            body: { maxHeight: 'calc(100dvh - 176px)', overflowY: 'auto', overflowWrap: 'anywhere' },
            footer: { marginTop: 12 },
        },
    },
    space: { size: 8 },
};
