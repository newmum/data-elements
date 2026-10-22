import { ConfigProvider, App as AntdApp } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import enUS from 'antd/locale/en_US';
import { QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router-dom';
import { queryClient } from './queryClient';
import { router } from './router';
import { lightTheme } from '@/theme/tokens';
import { useUiStore } from '@/store/uiStore';
import EmbeddedSession from './EmbeddedSession';

export default function App() {
  const locale = useUiStore((s) => s.locale);

  return (
    <ConfigProvider
      theme={lightTheme}
      locale={locale === 'zh' ? zhCN : enUS}
      componentSize="middle"
    >
      <AntdApp style={{ height: '100%' }}>
        <QueryClientProvider client={queryClient}>
          <EmbeddedSession><RouterProvider router={router} /></EmbeddedSession>
        </QueryClientProvider>
      </AntdApp>
    </ConfigProvider>
  );
}
