import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
// RED scaffold: BYOKeyModal.vue does not yet exist — import will fail until Plan 06-04 creates it.
// This is the intended RED state. The test structure is locked here for Plan 06-04 to turn GREEN.
import BYOKeyModal from '../../components/ai/BYOKeyModal.vue'

describe('BYOKeyModal', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('renders provider radios and password input when open=true', () => {
    const wrapper = mount(BYOKeyModal, {
      props: { open: true },
    })
    // Should render at least two radio buttons (Anthropic, OpenAI)
    const radios = wrapper.findAll('input[type="radio"]')
    expect(radios.length).toBeGreaterThanOrEqual(2)
    // Should render a password input for the API key
    expect(wrapper.find('input[type="password"]').exists()).toBe(true)
  })

  it('does not render content when open=false', () => {
    const wrapper = mount(BYOKeyModal, {
      props: { open: false },
    })
    // When closed, the modal content should not be visible
    expect(wrapper.find('input[type="password"]').exists()).toBe(false)
  })

  it('keyNotInLocalStorage — after submit, localStorage must not contain the typed key', async () => {
    const wrapper = mount(BYOKeyModal, {
      props: { open: true },
    })
    const passwordInput = wrapper.find('input[type="password"]')
    const testKey = 'test-secret-api-key-12345'
    await passwordInput.setValue(testKey)

    // Submit the form
    const form = wrapper.find('form')
    if (form.exists()) {
      await form.trigger('submit')
    } else {
      const submitBtn = wrapper.find('button[type="submit"]')
      if (submitBtn.exists()) await submitBtn.trigger('click')
    }

    // The key must NEVER be stored in localStorage (security invariant — AI-02)
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i)
      if (key) {
        expect(localStorage.getItem(key)).not.toBe(testKey)
      }
    }
  })

  it('emits submitted event after successful key submission', async () => {
    const wrapper = mount(BYOKeyModal, {
      props: { open: true },
    })
    const form = wrapper.find('form')
    if (form.exists()) {
      await form.trigger('submit')
    }
    // Should emit 'submitted' or 'close' on success — exact event depends on 06-04 implementation
    const emitted = wrapper.emitted()
    expect(emitted['submitted'] || emitted['close']).toBeTruthy()
  })

  it('emits close event when cancel is triggered', async () => {
    const wrapper = mount(BYOKeyModal, {
      props: { open: true },
    })
    const cancelBtn = wrapper.find('button[type="button"]')
    if (cancelBtn.exists()) {
      await cancelBtn.trigger('click')
    }
    expect(wrapper.emitted('close')).toBeTruthy()
  })
})
