import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export type Locale = 'zh' | 'en';

interface UiState {
  locale: Locale;
  /** left node palette collapsed */
  leftCollapsed: boolean;
  /** right config panel width (px) */
  rightWidth: number;
  /** right config panel collapsed */
  rightCollapsed: boolean;
  /** bottom run panel height (px) */
  bottomHeight: number;
  /** bottom run panel open */
  bottomOpen: boolean;
  /** active bottom panel tab */
  bottomActiveKey: 'result' | 'timeline' | 'problems';

  setLocale: (l: Locale) => void;
  setLeftCollapsed: (v: boolean) => void;
  setRightWidth: (w: number) => void;
  setRightCollapsed: (v: boolean) => void;
  setBottomHeight: (h: number) => void;
  setBottomOpen: (v: boolean) => void;
  setBottomActiveKey: (key: 'result' | 'timeline' | 'problems') => void;
}

export const useUiStore = create<UiState>()(
  persist(
    (set) => ({
      locale: 'zh',
      leftCollapsed: false,
      rightWidth: 360,
      rightCollapsed: false,
      bottomHeight: 280,
      bottomOpen: false,
      bottomActiveKey: 'problems',

      setLocale: (locale) => set({ locale }),
      setLeftCollapsed: (leftCollapsed) => set({ leftCollapsed }),
      setRightWidth: (rightWidth) => set({ rightWidth }),
      setRightCollapsed: (rightCollapsed) => set({ rightCollapsed }),
      setBottomHeight: (bottomHeight) => {
        const viewportLimit = typeof window === 'undefined' ? 760 : Math.floor(window.innerHeight * 0.8);
        set({ bottomHeight: Math.max(96, Math.min(viewportLimit, bottomHeight)) });
      },
      setBottomOpen: (bottomOpen) => set({ bottomOpen }),
      setBottomActiveKey: (bottomActiveKey) => set({ bottomActiveKey }),
    }),
    { name: 'yunti-ui' },
  ),
);
