import { useEffect } from 'react';
import EditorPage from './pages/EditorPage';

interface AppProps {
  onReady?: () => void;
}

export default function App({ onReady }: AppProps) {
  useEffect(() => {
    onReady?.();
  }, [onReady]);

  return (
    <div style={{ height: '100vh', background: 'var(--canvas-bg)' }}>
      <EditorPage />
    </div>
  );
}
