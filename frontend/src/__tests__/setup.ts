// ResizeObserver mock — required because vue-echarts uses it; jsdom does not implement it
// Uses globalThis for TypeScript compatibility in the jsdom/DOM environment

// eslint-disable-next-line @typescript-eslint/no-explicit-any
;(globalThis as any).ResizeObserver = class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

// ECharts renderer registration — required for any test that mounts a v-chart with data.
// In production this runs as a side-effect import from main.ts, but tests bypass main.ts.
// The CanvasRenderer import here prevents "Renderer 'undefined' is not imported" errors.
import '../plugins/echarts'

// Global vue-test-utils stub for VChart.
// vue-echarts exports its component with internal name 'Echarts'. In production it
// renders on <canvas>; jsdom does not support canvas. We stub it so:
//   1. Canvas render errors are eliminated
//   2. findComponent({ name: 'VChart' }) works — the stub has name 'VChart'
//
// Note: vue-test-utils matches stubs by the key name against the *registered* component
// name in the parent's template context. In <script setup>, VChart is locally bound
// under the variable name 'VChart', so the stub key 'VChart' matches the local binding.
// We also stub by 'Echarts' (the component's own .name) for belt-and-suspenders.
import { config } from '@vue/test-utils'
import { defineComponent } from 'vue'

const VChartStub = defineComponent({
  name: 'VChart',
  props: { option: Object, autoresize: Boolean },
  template: '<div class="v-chart-stub" />',
})

config.global.stubs = {
  ...config.global.stubs,
  VChart: VChartStub,
  Echarts: VChartStub,
}
