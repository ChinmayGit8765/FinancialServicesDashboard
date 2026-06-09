import { mount } from '@vue/test-utils'
import { describe, it, expect } from 'vitest'
import ChatPanel from '../ChatPanel.vue'

describe('ChatPanel', () => {
  it('emits send event on button click', async () => {
    const wrapper = mount(ChatPanel, {
      props: { messages: [], loading: false, error: null }
    })
    await wrapper.find('.chat-input').setValue('What are AAPL risks?')
    await wrapper.find('.send-btn').trigger('click')
    expect(wrapper.emitted('send')?.[0]).toEqual(['What are AAPL risks?'])
  })

  it('renders user message and assistant message with citations', () => {
    const messages = [
      { role: 'user' as const, content: 'What are Apple risks?' },
      {
        role: 'assistant' as const,
        content: 'Regulatory risk is significant.',
        citations: [
          {
            ticker: 'AAPL',
            section: 'Risk Factors',
            source: 'AAPL 10-K FY2023',
            excerpt: 'App Store regulatory scrutiny...'
          }
        ]
      }
    ]
    const wrapper = mount(ChatPanel, { props: { messages, loading: false, error: null } })
    expect(wrapper.text()).toContain('What are Apple risks?')
    expect(wrapper.text()).toContain('Regulatory risk is significant.')
    expect(wrapper.find('.citation-chip').text()).toContain('AAPL')
  })

  it('disables input while loading', () => {
    const wrapper = mount(ChatPanel, {
      props: { messages: [], loading: true, error: null }
    })
    expect(wrapper.find('.chat-input').attributes('disabled')).toBeDefined()
    expect(wrapper.find('.send-btn').attributes('disabled')).toBeDefined()
  })
})
