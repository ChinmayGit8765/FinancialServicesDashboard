import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
// RED scaffold: PairsTable.vue does not yet exist — import will fail until Plan 04-04 creates it.
// This is the intended RED state. The test structure is locked here for Plan 04-04 to turn GREEN.
import PairsTable from '../../components/PairsTable.vue'

describe('PairsTable', () => {
  it('shows a shimmer skeleton when loading is true', () => {
    const wrapper = mount(PairsTable, {
      props: { pairs: null, loading: true, error: null },
    })
    expect(wrapper.find('.skeleton').exists()).toBe(true)
  })

  it('shows empty state when pairs array is empty', () => {
    const wrapper = mount(PairsTable, {
      props: { pairs: [], loading: false, error: null },
    })
    expect(wrapper.find('.chart-empty').exists()).toBe(true)
  })

  it('renders table rows when pairs are provided', () => {
    const wrapper = mount(PairsTable, {
      props: {
        pairs: [
          {
            tickerY: 'AAPL',
            tickerX: 'MSFT',
            hedgeRatio: 1.23,
            adfStatistic: -3.45,
            pValue: 0.028,
            spreadZScore: 2.1,
            signal: 'SHORT_Y_LONG_X' as const,
          },
        ],
        loading: false,
        error: null,
      },
    })
    expect(wrapper.find('table').exists()).toBe(true)
    expect(wrapper.text()).toContain('AAPL')
    expect(wrapper.text()).toContain('MSFT')
  })

  it('shows error state with retry button when error is set', () => {
    const wrapper = mount(PairsTable, {
      props: { pairs: null, loading: false, error: 'Failed to load pairs data' },
    })
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(wrapper.find('.retry-btn').exists()).toBe(true)
  })
})
