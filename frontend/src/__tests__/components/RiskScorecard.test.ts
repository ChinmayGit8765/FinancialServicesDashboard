import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
// RED scaffold: RiskScorecard.vue does not yet exist — import will fail until Plan 04-04 creates it.
// This is the intended RED state. The test structure is locked here for Plan 04-04 to turn GREEN.
import RiskScorecard from '../../components/RiskScorecard.vue'

describe('RiskScorecard', () => {
  it('shows a shimmer skeleton when loading is true', () => {
    const wrapper = mount(RiskScorecard, {
      props: { risk: null, loading: true, error: null },
    })
    expect(wrapper.find('.skeleton').exists()).toBe(true)
  })

  it('shows metric values when loaded', () => {
    const wrapper = mount(RiskScorecard, {
      props: {
        risk: {
          sharpeRatio: 0.42,
          annualizedVolatility: 0.28,
          maxDrawdown: -0.15,
          beta: 1.25,
          var: [],
        },
        loading: false,
        error: null,
      },
    })
    expect(wrapper.text()).toContain('0.42')
  })

  it('shows error state with retry button when error is set', () => {
    const wrapper = mount(RiskScorecard, {
      props: { risk: null, loading: false, error: 'Failed to load risk metrics' },
    })
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(wrapper.find('.retry-btn').exists()).toBe(true)
  })
})
