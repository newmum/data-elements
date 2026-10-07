import type { ConfigProviderProps } from 'antd';

/** Shared portal styling also applies to App.useApp() modals and notifications. */
export const compactComponents: Pick<ConfigProviderProps, 'drawer' | 'modal' | 'space'> = {
    drawer: {
        classNames: { body: 'iam-drawer-body', header: 'iam-drawer-header', footer: 'iam-drawer-footer', title: 'iam-panel-title' },
        styles: {
            wrapper: { maxWidth: '100vw' },
            header: { padding: '16px 24px', minHeight: 56 },
            body: { padding: '22px 24px', minWidth: 0 },
            footer: { padding: '14px 24px' },
        },
    },
    modal: {
        classNames: { title: 'iam-panel-title' },
        styles: {
            root: { maxWidth: 'calc(100vw - 24px)', top: 32 },
            container: { padding: 22 },
            header: { marginBottom: 16 },
            body: { maxHeight: 'calc(100dvh - 176px)', overflowY: 'auto', overflowWrap: 'anywhere' },
            footer: { marginTop: 16 },
        },
    },
    space: { size: 8 },
};
