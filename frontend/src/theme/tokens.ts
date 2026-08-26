export const designTokens = {
  colors: {
    primary: '#0d9488',
    primaryHover: '#0f766e',
    primaryPressed: '#115e59',
    primarySuppl: '#14b8a6',
    text: '#1f2937',
    textSecondary: '#4b5563',
    textMuted: '#6b7280',
    border: '#eef0ee',
    borderStrong: '#e2e6e3',
    page: '#f6f8f7',
    surface: '#ffffff',
    surfaceMuted: '#fafbf9',
    surfaceSoft: '#fcfdfb',
    tableHead: '#f3faf8',
    success: '#10b981',
    warning: '#f59e0b',
    error: '#ef4444',
    info: '#0ea5e9'
  },
  typography: {
    sans: '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", Inter, Roboto, sans-serif',
    mono: '"JetBrains Mono", ui-monospace, Consolas, "Liberation Mono", Menlo, monospace',
    sizeXs: '12px',
    sizeSm: '13px',
    sizeBody: '14px',
    sizeLg: '16px',
    sizeTitle: '20px',
    lineBody: '1.55'
  },
  radius: {
    small: '8px',
    control: '10px',
    card: '14px',
    pill: '999px'
  },
  controlHeight: {
    small: '34px',
    medium: '36px',
    large: '42px'
  }
} as const

export const chartPalette = [
  designTokens.colors.primary,
  '#c2410c',
  '#4338ca',
  '#db2777',
  '#a16207',
  '#6d28d9'
] as const

export const chartSingleBarColor = designTokens.colors.primary
export const chartStatusSuccess = designTokens.colors.success
export const chartStatusWarning = designTokens.colors.warning
export const chartStatusError = designTokens.colors.error
export const chartStatusInfo = designTokens.colors.info
export const chartAxisColor = '#6b7280'
export const chartAxisLineColor = designTokens.colors.borderStrong
export const chartSplitLineColor = designTokens.colors.border
export const chartTooltipBorderColor = designTokens.colors.border
