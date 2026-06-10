// src/plugins/echarts-theme.ts — quantlens-dark ECharts theme
// Source: Phase 3 UI-SPEC § ECharts Theming

export const quantlensDarkTheme = {
  backgroundColor: 'transparent',   // chart bg is always transparent; card bg shows through

  textStyle: {
    fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
    color: '#a7b4c4',                // --color-text-secondary
    fontSize: 12,
  },

  title: {
    textStyle: { color: '#e8edf4', fontSize: 14, fontWeight: 600 },
    subtextStyle: { color: '#75828f', fontSize: 11 },
  },

  legend: {
    textStyle: { color: '#a7b4c4', fontSize: 12 },
    icon: 'circle',
    itemWidth: 8,
    itemHeight: 8,
  },

  tooltip: {
    backgroundColor: '#1a2230',      // --color-bg-surface
    borderColor: '#2a3546',          // --color-border
    borderWidth: 1,
    textStyle: { color: '#e8edf4', fontSize: 13 },
    extraCssText: 'border-radius: 10px; box-shadow: 0 12px 40px rgba(0,0,0,0.45)',
  },

  grid: {
    containLabel: true,
    left: 16,
    right: 16,
    top: 32,
    bottom: 24,
  },

  xAxis: {
    axisLine:  { lineStyle: { color: '#2a3546' } },
    axisTick:  { lineStyle: { color: '#2a3546' } },
    axisLabel: { color: '#75828f', fontSize: 11 },
    splitLine: { show: false },
  },

  yAxis: {
    axisLine:  { show: false },
    axisTick:  { show: false },
    axisLabel: { color: '#75828f', fontSize: 11 },
    splitLine: { lineStyle: { color: '#1b232f', type: 'dashed' } },  // --color-border-subtle
  },

  // Default color cycle (allocation donut sectors, etc.) — muted, natural tones
  color: ['#4f9fe0', '#9b8cf0', '#d7a657', '#4cc08c', '#df85b4', '#46b5b0', '#dd9a63', '#7e8ce8'],

  line: {
    smooth: true,
    symbolSize: 0,     // no dots on line by default; show on hover via tooltip
    lineStyle: { width: 2 },
  },

  // Heatmap (correlation): muted coral → neutral → azure
  visualMap: {
    color: ['#e87b73', '#e9eef4', '#4f9fe0'],   // --color-down, near-white, --color-accent
    textStyle: { color: '#a7b4c4' },
  },
}
