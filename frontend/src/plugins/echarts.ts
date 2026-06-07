// src/plugins/echarts.ts — ECharts tree-shaking registration + custom theme
// Source: echarts.apache.org/handbook/en/basics/import/ + RESEARCH.md Pattern 1
// IMPORTANT: import from 'echarts/core' NOT 'echarts' (avoids full ~1MB bundle)

import * as echarts from 'echarts/core'
import { use } from 'echarts/core'
import { LineChart, PieChart, TreemapChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  DataZoomComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { quantlensDarkTheme } from './echarts-theme'

// Register modules — called once at app startup via side-effect import in main.ts
use([
  LineChart,
  PieChart,
  TreemapChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  DataZoomComponent,
  CanvasRenderer,
])

// Register custom theme by name — applied via provide(THEME_KEY, 'quantlens-dark') in App.vue
echarts.registerTheme('quantlens-dark', quantlensDarkTheme)
