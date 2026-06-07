import { describe, it, expect } from 'vitest'
import {
  formatCurrency,
  formatCurrencyCompact,
  formatPercent,
  formatSignedCurrency,
  formatSignedPercent,
  deltaClass,
  formatDate,
} from '@/utils/format'

describe('formatCurrency', () => {
  it('formats 1234567.89 as $1,234,567.89', () => {
    expect(formatCurrency(1234567.89)).toBe('$1,234,567.89')
  })
  it('formats 0 as $0.00', () => {
    expect(formatCurrency(0)).toBe('$0.00')
  })
})

describe('formatCurrencyCompact', () => {
  it('formats 1230000 as $1.23M', () => {
    expect(formatCurrencyCompact(1230000)).toBe('$1.23M')
  })
})

describe('formatPercent', () => {
  it('formats 0.1234 fraction as 12.34%', () => {
    expect(formatPercent(0.1234)).toBe('12.34%')
  })
})

describe('formatSignedCurrency', () => {
  it('formats positive 4567 as +$4,567.00', () => {
    expect(formatSignedCurrency(4567)).toBe('+$4,567.00')
  })
  it('formats negative -1234 as -$1,234.00', () => {
    expect(formatSignedCurrency(-1234)).toBe('-$1,234.00')
  })
})

describe('formatSignedPercent', () => {
  it('positive shows + prefix', () => {
    expect(formatSignedPercent(2.34)).toBe('+2.34%')
  })
  it('negative shows - prefix', () => {
    expect(formatSignedPercent(-1.12)).toBe('-1.12%')
  })
  it('zero shows 0.00% (no sign)', () => {
    expect(formatSignedPercent(0)).toBe('0.00%')
  })
})

describe('deltaClass', () => {
  it('positive → val-up', () => expect(deltaClass(1)).toBe('val-up'))
  it('negative → val-down', () => expect(deltaClass(-1)).toBe('val-down'))
  it('zero → val-flat', () => expect(deltaClass(0)).toBe('val-flat'))
})

describe('formatDate', () => {
  it('formats ISO string without UTC off-by-one', () => {
    expect(formatDate('2022-09-12')).toBe('12 Sep 2022')
  })
})
