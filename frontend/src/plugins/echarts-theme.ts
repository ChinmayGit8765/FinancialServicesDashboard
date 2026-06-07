// src/plugins/echarts-theme.ts — quantlens-dark ECharts theme
// Source: Phase 3 UI-SPEC § ECharts Theming

export const quantlensDarkTheme = {
  backgroundColor: 'transparent',   // chart bg is always transparent; card bg shows through

  textStyle: {
    fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
    color: '#94a3b8',                // --color-text-secondary
    fontSize: 12,
  },

  title: {
    textStyle: { color: '#e2e8f0', fontSize: 14, fontWeight: 600 },
    subtextStyle: { color: '#64748b', fontSize: 11 },
  },

  legend: {
    textStyle: { color: '#94a3b8', fontSize: 12 },
    icon: 'circle',
    itemWidth: 8,
    itemHeight: 8,
  },

  tooltip: {
    backgroundColor: '#1e293b',      // --color-bg-surface
    borderColor: '#334155',          // --color-border
    borderWidth: 1,
    textStyle: { color: '#e2e8f0', fontSize: 13 },
    extraCssText: 'border-radius: 8px; box-shadow: 0 8px 32px rgba(0,0,0,0.55)',
  },

  grid: {
    containLabel: true,
    left: 16,
    right: 16,
    top: 32,
    bottom: 24,
  },

  xAxis: {
    axisLine:  { lineStyle: { color: '#334155' } },
    axisTick:  { lineStyle: { color: '#334155' } },
    axisLabel: { color: '#64748b', fontSize: 11 },
    splitLine: { show: false },
  },

  yAxis: {
    axisLine:  { show: false },
    axisTick:  { show: false },
    axisLabel: { color: '#64748b', fontSize: 11 },
    splitLine: { lineStyle: { color: '#1e293b', type: 'dashed' } },  // --color-border-subtle
  },

  // Default color cycle (allocation donut sectors, etc.)
  color: ['#0ea5e9', '#8b5cf6', '#f59e0b', '#22c55e', '#ec4899', '#14b8a6', '#f97316', '#6366f1'],

  line: {
    smooth: true,
    symbolSize: 0,     // no dots on line by default; show on hover via tooltip
    lineStyle: { width: 2 },
  },

  // Heatmap (Phase 4 correlation): color scale blue → white → red
  // Registered now so Phase 4 can use it without touching the theme:
  visualMap: {
    color: ['#ef4444', '#f8fafc', '#3b82f6'],   // --color-down, near-white, blue
    textStyle: { color: '#94a3b8' },
  },
}
