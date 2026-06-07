// src/utils/format.ts — Intl.NumberFormat helpers
// Pure functions only — no imports from vue or axios

// Currency: $1,234,567.89
export function formatCurrency(value: number): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)
}

// Compact currency above 1M: $1.23M
export function formatCurrencyCompact(value: number): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    notation: 'compact',
    maximumFractionDigits: 2,
  }).format(value)
}

// Percent: 12.34%
// value is 0–1 fraction (multiply by 100 happens inside Intl)
export function formatPercent(value: number, decimals = 2): string {
  return new Intl.NumberFormat('en-US', {
    style: 'percent',
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(value)
}

// Signed delta absolute: "+$4,567.00" / "-$1,234.00"
export function formatSignedCurrency(value: number): string {
  const formatted = formatCurrency(Math.abs(value))
  return value >= 0 ? `+${formatted}` : `-${formatted}`
}

// Signed delta percent: "+2.34%" / "-1.12%"
// pctValue is already in percent units (e.g. 2.34 means 2.34%), not 0-1 fraction
export function formatSignedPercent(pctValue: number): string {
  const abs = new Intl.NumberFormat('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Math.abs(pctValue))
  return pctValue > 0 ? `+${abs}%` : pctValue < 0 ? `-${abs}%` : `0.00%`
}

// Determine CSS class for signed values
export function deltaClass(value: number): 'val-up' | 'val-down' | 'val-flat' {
  if (value > 0) return 'val-up'
  if (value < 0) return 'val-down'
  return 'val-flat'
}

// Date: "12 Sep 2022" from ISO string "2022-09-12"
// Append T00:00:00 to force local midnight — avoids UTC off-by-one in UTC− timezones
// Use en-US locale for month 'short' to get 3-char abbreviations ("Sep" not "Sept")
// then reformat to DD MMM YYYY order expected by the UI-SPEC
const MONTH_ABBR = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec']
export function formatDate(isoStr: string): string {
  const d = new Date(isoStr + 'T00:00:00')
  const day = String(d.getDate()).padStart(2, '0')
  const month = MONTH_ABBR[d.getMonth()]
  const year = d.getFullYear()
  return `${day} ${month} ${year}`
}
