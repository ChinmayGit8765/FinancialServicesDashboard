// ResizeObserver mock — required because vue-echarts uses it; jsdom does not implement it
// Uses globalThis for TypeScript compatibility in the jsdom/DOM environment

// eslint-disable-next-line @typescript-eslint/no-explicit-any
;(globalThis as any).ResizeObserver = class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}
