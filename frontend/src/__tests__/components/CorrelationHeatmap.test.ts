import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
// RED scaffold: CorrelationHeatmap.vue does not yet exist — import will fail until Plan 04-04 creates it.
// This is the intended RED state. The test structure is locked here for Plan 04-04 to turn GREEN.
import CorrelationHeatmap from '../../components/CorrelationHeatmap.vue'

describe('CorrelationHeatmap', () => {
  it('shows a shimmer skeleton when loading is true', () => {
    const wrapper = mount(CorrelationHeatmap, {
      props: { correlation: null, loading: true, error: null },
    })
    expect(wrapper.find('.skeleton').exists()).toBe(true)
  })

  it('shows empty state when correlation data has no tickers', () => {
    const wrapper = mount(CorrelationHeatmap, {
      props: {
        correlation: { tickers: [], matrix: [] },
        loading: false,
        error: null,
      },
    })
    expect(wrapper.find('.chart-empty').exists()).toBe(true)
  })

  it('shows error state with retry button when error is set', () => {
    const wrapper = mount(CorrelationHeatmap, {
      props: { correlation: null, loading: false, error: 'Failed to load correlation data' },
    })
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(wrapper.find('.retry-btn').exists()).toBe(true)
  })
})
