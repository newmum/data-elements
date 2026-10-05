import { create } from 'zustand';

interface ErrorPanelState {
  visible: boolean;
  open: () => void;
  close: () => void;
  toggle: () => void;
}

export const useErrorPanelStore = create<ErrorPanelState>((set) => ({
  visible: false,
  open: () => set({ visible: true }),
  close: () => set({ visible: false }),
  toggle: () => set((s) => ({ visible: !s.visible })),
}));
