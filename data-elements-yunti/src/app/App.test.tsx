import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import App from './App';

describe('App', () => {
  it('renders the product brand', () => {
    render(<App />);
    expect(screen.getByText('云梯服务编排平台')).toBeInTheDocument();
  });
});
