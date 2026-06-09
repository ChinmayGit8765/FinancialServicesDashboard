import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
// RED scaffold: ExplainDrawer.vue does not yet exist — import will fail until Plan 06-04 creates it.
// This is the intended RED state. The test structure is locked here for Plan 06-04 to turn GREEN.
import ExplainDrawer from '../../components/ai/ExplainDrawer.vue'

describe('ExplainDrawer', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('shows shimmer skeleton when loading=true', () => {
    const wrapper = mount(ExplainDrawer, {
      props: {
        open: true,
        narrative: null,
        loading: true,
        error: null,
      },
    })
    expect(wrapper.find('.skeleton').exists()).toBe(true)
  })

  it('renders narrative text when populated', () => {
    const wrapper = mount(ExplainDrawer, {
      props: {
        open: true,
        narrative: 'AAPL has demonstrated strong fundamentals with a diversified revenue base.',
        loading: false,
        error: null,
      },
    })
    expect(wrapper.text()).toContain('AAPL has demonstrated strong fundamentals')
  })

  it('does not render drawer content when open=false', () => {
    const wrapper = mount(ExplainDrawer, {
      props: {
        open: false,
        narrative: 'Some narrative',
        loading: false,
        error: null,
      },
    })
    // When closed, the drawer panel content should not be visible
    expect(wrapper.find('.skeleton').exists()).toBe(false)
    // The narrative text should not appear when drawer is closed
    expect(wrapper.text()).not.toContain('Some narrative')
  })

  it('shows error state when error is set', () => {
    const wrapper = mount(ExplainDrawer, {
      props: {
        open: true,
        narrative: null,
        loading: false,
        error: 'Failed to load explanation',
      },
    })
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
  })
})
