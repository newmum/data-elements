import { useLayoutEffect } from 'react';
import { ConfigProvider, theme, type ThemeConfig } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { useNavigate } from 'react-router-dom';
import ReportWorkbench from './App';
import './styles.css';

const reportTheme: ThemeConfig = {
  inherit: false,
  algorithm: theme.defaultAlgorithm,
  token: {
    colorPrimary: '#246BFD', colorSuccess: '#16794B', colorWarning: '#A96A08',
    colorError: '#C53B32', colorText: '#1F2937', colorTextSecondary: '#68768A',
    colorBorder: '#DCE3EC', borderRadius: 6, fontSize: 14, controlHeight: 36,
    fontFamily: '"Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", Arial, sans-serif',
  },
  components: {
    Table: { headerBg: '#F5F7FA', headerColor: '#5E6C80', rowSelectedBg: '#EAF2FF', cellPaddingBlock: 10 },
    Button: { primaryShadow: 'none' }, Modal: { titleFontSize: 18 },
    Tabs: { titleFontSize: 16, horizontalItemGutter: 32 },
  },
};

/** A full-page reporting route using Haitong's existing session and API client. */
export default function ReportPage() {
  const navigate = useNavigate();
  useLayoutEffect(() => {
    const previousTitle = document.title;
    document.body.classList.add('ht-report-active');
    document.title = '数据上报 · 海通数据集成中心';
    return () => {
      document.body.classList.remove('ht-report-active');
      document.title = previousTitle;
    };
  }, []);
  // A dedicated prefix also isolates body-mounted dialogs from the host's
  // existing global Ant Design dark-theme overrides.
  return <ConfigProvider prefixCls="report-ant" locale={zhCN} theme={reportTheme}>
    <ReportWorkbench onReturn={() => navigate('/development/tasks')}/>
  </ConfigProvider>;
}
