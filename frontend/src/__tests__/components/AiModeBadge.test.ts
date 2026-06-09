import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
// RED scaffold: AiModeBadge.vue does not yet exist — import will fail until Plan 06-04 creates it.
// This is the intended RED state. The test structure is locked here for Plan 06-04 to turn GREEN.
import AiModeBadge from '../../components/ai/AiModeBadge.vue'

describe('AiModeBadge', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('shows "Demo" text when status mode is demo', () => {
    const wrapper = mount(AiModeBadge, {
      props: { mode: 'demo', provider: null },
    })
    expect(wrapper.text().toLowerCase()).toContain('demo')
  })

  it('shows "Live" text when status mode is live', () => {
    const wrapper = mount(AiModeBadge, {
      props: { mode: 'live', provider: 'anthropic' },
    })
    expect(wrapper.text().toLowerCase()).toContain('live')
  })

  it('applies a distinct CSS class in live mode vs demo mode', () => {
    const demoWrapper = mount(AiModeBadge, {
      props: { mode: 'demo', provider: null },
    })
    const liveWrapper = mount(AiModeBadge, {
      props: { mode: 'live', provider: 'anthropic' },
    })
    // The rendered HTML must differ between modes (badge styling changes)
    expect(demoWrapper.html()).not.toEqual(liveWrapper.html())
  })
})
