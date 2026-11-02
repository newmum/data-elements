import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import OrchestrationState, { describeOrchestrationError } from './OrchestrationState';
import { ApiClientError } from '@/services/client';
afterEach(cleanup);
it('uses one business-specific loading surface with no artificial percentage', () => {
  render(<OrchestrationState />);
  expect(screen.getByRole('status')).toHaveAttribute('aria-busy', 'true');
  expect(screen.getByRole('heading')).toHaveTextContent('正在打开服务编排');
  expect(screen.getByRole('status')).toHaveTextContent('请求编排响应');
  expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
});
it.each([401,403,404,409,408,502,0])('provides actionable branded errors and retry/return actions for %s', code => {
  const retry=vi.fn(),back=vi.fn();
  render(<OrchestrationState error={new ApiClientError(code,'服务读取失败 token=private-fixture')} onRetry={retry} onReturn={back} />);
  expect(screen.getByRole('alert')).toHaveClass('flow-entry--error');
  expect(screen.getByRole('alert')).not.toHaveTextContent('private-fixture');
  fireEvent.click(screen.getByRole('button',{name:'重新加载'}));expect(retry).toHaveBeenCalledOnce();
  fireEvent.click(screen.getByRole('button',{name:'返回数据服务'}));expect(back).toHaveBeenCalledOnce();
  expect(describeOrchestrationError(new ApiClientError(404,'missing')).title).toContain('无法访问');
});
