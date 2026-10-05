/** The only product-level scale. Compact means less padding, never global zoom. */
export const typeScale = { micro: 12, body: 14, section: 16, page: 24, metric: 28 } as const;
export const spacing = { xs: 4, sm: 8, md: 12, lg: 16, xl: 20, xxl: 24 } as const;
export const palette = {
    light: { primary: '#7039E5', primaryBg: '#F2ECFE', background: '#F5F6FC', surface: '#FFFFFF', elevated: '#FFFFFF', text: '#20243F', secondary: '#626B84', border: '#E5E7F2', header: '#F8F8FD' },
    dark: { primary: '#B497FF', primaryBg: '#302643', background: '#11131F', surface: '#1B1F30', elevated: '#252A3D', text: '#F1F2FA', secondary: '#ACB3CD', border: '#34394F', header: '#23283B' },
} as const;
export type Appearance = keyof typeof palette;
