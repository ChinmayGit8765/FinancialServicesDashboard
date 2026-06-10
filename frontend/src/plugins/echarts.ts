// src/plugins/echarts.ts — ECharts tree-shaking registration + custom theme
// Source: echarts.apache.org/handbook/en/basics/import/ + RESEARCH.md Pattern 1
// IMPORTANT: import from 'echarts/core' NOT 'echarts' (avoids full ~1MB bundle)

import * as echarts from 'echarts/core'
import { LineChart, PieChart, TreemapChart, HeatmapChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  DataZoomComponent,
  VisualMapComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { quantlensDarkTheme } from './echarts-theme'

// Register modules — called once at app startup via side-effect import in main.ts
echarts.use([
  LineChart,
  PieChart,
  TreemapChart,
  // HeatmapChart + VisualMapComponent: required by CorrelationHeatmap.vue. Without them the
  // category axes still draw (GridComponent) but the heatmap series + colour scale are silently
  // dropped — the cells render blank. (Fix: correlation heatmap showed empty cells.)
  HeatmapChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  DataZoomComponent,
  VisualMapComponent,
  CanvasRenderer,
])

// Register custom theme by name — applied via provide(THEME_KEY, 'quantlens-dark') in App.vue
echarts.registerTheme('quantlens-dark', quantlensDarkTheme)
