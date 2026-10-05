import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Button, Result } from 'antd';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export default class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('[ErrorBoundary] 组件渲染异常:', error, errorInfo);
  }

  handleReload = () => {
    this.setState({ hasError: false, error: null });
  };

  render() {
    if (this.state.hasError) {
      return (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          height: '100vh',
          background: '#f5f7fa',
        }}>
          <Result
            status="error"
            title="页面渲染异常"
            subTitle={
              <div style={{ maxWidth: 480, wordBreak: 'break-word' }}>
                <p>应用遇到了一个渲染错误，部分内容无法正常显示。</p>
                <details style={{ textAlign: 'left', marginTop: 12, fontSize: 12, color: '#8c8c8c' }}>
                  <summary style={{ cursor: 'pointer', marginBottom: 4 }}>查看错误详情</summary>
                  <pre style={{
                    background: '#fafafa',
                    padding: 12,
                    borderRadius: 6,
                    overflow: 'auto',
                    maxHeight: 200,
                    fontSize: 11,
                  }}>
                    {this.state.error?.message ?? '未知错误'}
                    {this.state.error?.stack && `\n\n${this.state.error.stack}`}
                  </pre>
                </details>
              </div>
            }
            extra={[
              <Button key="retry" type="primary" onClick={this.handleReload}>
                重试
              </Button>,
              <Button key="reload" onClick={() => window.location.reload()}>
                刷新页面
              </Button>,
            ]}
          />
        </div>
      );
    }

    return this.props.children;
  }
}
