import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import HoldingsTable from '../../components/HoldingsTable.vue'
import type { HoldingDto } from '../../api/portfolio'

const makeHolding = (ticker: string): HoldingDto => ({
  ticker,
  name: `${ticker} Corp`,
  sector: 'Technology',
  quantity: 100,
  avgCostBasis: 150,
  currentPrice: 175,
  currentMarketValue: 17500,
  portfolioWeight: 0.25,
  unrealizedPnlAbs: 2500,
  unrealizedPnlPct: 0.1667,
})

describe('HoldingsTable', () => {
  it('shows empty-state message when holdings array is empty', () => {
    const wrapper = mount(HoldingsTable, {
      props: { holdings: [], loading: false, error: null },
    })
    expect(wrapper.text()).toContain('No holdings in this portfolio.')
  })

  it('renders the correct number of data rows for a populated list', () => {
    const holdings = [makeHolding('AAPL'), makeHolding('MSFT'), makeHolding('GOOGL')]
    const wrapper = mount(HoldingsTable, {
      props: { holdings, loading: false, error: null },
    })
    // 3 data rows (no skeleton, no empty row)
    const rows = wrapper.findAll('tbody tr.data-row')
    expect(rows).toHaveLength(3)
  })

  it('shows 5 skeleton rows when loading', () => {
    const wrapper = mount(HoldingsTable, {
      props: { holdings: null, loading: true, error: null },
    })
    const skeletonRows = wrapper.findAll('tbody tr.skeleton-row')
    expect(skeletonRows).toHaveLength(5)
  })

  it('renders ticker in each row', () => {
    const holdings = [makeHolding('AAPL'), makeHolding('MSFT')]
    const wrapper = mount(HoldingsTable, {
      props: { holdings, loading: false, error: null },
    })
    expect(wrapper.text()).toContain('AAPL')
    expect(wrapper.text()).toContain('MSFT')
  })

  it('shows error message when error prop is set', () => {
    const wrapper = mount(HoldingsTable, {
      props: { holdings: null, loading: false, error: 'Failed to load holdings' },
    })
    expect(wrapper.text()).toContain('Failed to load holdings.')
  })
})
