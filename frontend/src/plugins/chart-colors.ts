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
