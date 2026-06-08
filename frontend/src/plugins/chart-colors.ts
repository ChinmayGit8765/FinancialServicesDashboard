/**
 * Chart color constants derived from CSS design tokens.
 *
 * ECharts series configs cannot reference CSS custom properties directly
 * (they live in a canvas context). This module reads token values once from
 * the computed style of :root at module initialisation time so that all chart
 * series colours track the design-token system rather than hardcoding hex.
 *
 * If the token is not found (e.g. SSR / test env where there is no DOM),
 * the fallback literal matches the token definition in style.css so the
 * rendered output is always correct.
 */

function cssVar(name: string, fallback: string): string {
  if (typeof window === 'undefined') return fallback
  const val = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return val || fallback
}

export const CHART_COLORS = {
  accent:        cssVar('--color-accent',         '#0ea5e9'),
  border:        cssVar('--color-border',          '#334155'),
  textSecondary: cssVar('--color-text-secondary',  '#94a3b8'),
  bgBase:        cssVar('--color-bg-base',         '#0b0f1a'),
} as const

/**
 * Fan-chart color constants resolved from CSS design tokens.
 *
 * ECharts renders on <canvas> and CANNOT resolve CSS custom properties at
 * paint time — getComputedStyle must be called at module-init time.
 * These tokens are defined in style.css lines 65-68.
 *
 * median    = --color-fan-p50    (#0ea5e9)         — median line
 * bandInner = --color-fan-band-1 (rgba 14,165,233,0.25) — IQR p25-p75
 * bandOuter = --color-fan-band-2 (rgba 14,165,233,0.12) — outer p5-p25, p75-p95
 */
export const FAN_COLORS = {
  median:    cssVar('--color-fan-p50',    '#0ea5e9'),
  bandInner: cssVar('--color-fan-band-1', 'rgba(14,165,233,0.25)'),
  bandOuter: cssVar('--color-fan-band-2', 'rgba(14,165,233,0.12)'),
} as const
