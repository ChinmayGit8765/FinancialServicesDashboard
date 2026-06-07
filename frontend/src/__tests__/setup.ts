// ResizeObserver mock — required because vue-echarts uses it; jsdom does not implement it
global.ResizeObserver = class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}
