import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
// RED scaffold: CommentaryCard.vue does not yet exist — import will fail until Plan 06-04 creates it.
// This is the intended RED state. The test structure is locked here for Plan 06-04 to turn GREEN.
import CommentaryCard from '../../components/ai/CommentaryCard.vue'

describe('CommentaryCard', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('shows shimmer skeleton when loading=true', () => {
    const wrapper = mount(CommentaryCard, {
      props: {
        commentary: null,
        loading: true,
        error: null,
      },
    })
    expect(wrapper.find('.skeleton').exists()).toBe(true)
  })

  it('renders headline, body and bulletPoints when populated', () => {
    const wrapper = mount(CommentaryCard, {
      props: {
        commentary: {
          headline: 'Growth Portfolio: Tech Momentum Continues',
          body: 'Your growth-oriented portfolio maintained strong exposure to technology sector leaders.',
          bulletPoints: [
            'AAPL: 30% allocation — largest position',
            'NVDA: momentum continues post-earnings',
            'Diversification score: 0.72',
          ],
        },
        loading: false,
        error: null,
      },
    })
    expect(wrapper.text()).toContain('Growth Portfolio: Tech Momentum Continues')
    expect(wrapper.text()).toContain('maintained strong exposure')
    expect(wrapper.text()).toContain('AAPL: 30% allocation')
  })

  it('shows error state with static copy when error is set', () => {
    const wrapper = mount(CommentaryCard, {
      props: {
        commentary: null,
        loading: false,
        error: 'Failed to load commentary',
      },
    })
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
  })

  it('renders bullet points as a list', () => {
    const wrapper = mount(CommentaryCard, {
      props: {
        commentary: {
          headline: 'Test headline',
          body: 'Test body',
          bulletPoints: ['Point A', 'Point B', 'Point C'],
        },
        loading: false,
        error: null,
      },
    })
    const listItems = wrapper.findAll('li')
    expect(listItems.length).toBeGreaterThanOrEqual(3)
  })
})
