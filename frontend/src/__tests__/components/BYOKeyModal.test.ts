import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import BYOKeyModal from '../../components/ai/BYOKeyModal.vue'
import { useAiStore } from '../../stores/ai'

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
    // Mock setKey to resolve immediately (success path)
    const aiStore = useAiStore()
    vi.spyOn(aiStore, 'setKey').mockResolvedValue(undefined)

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
    await flushPromises()

    // The key must NEVER be stored in localStorage (security invariant — AI-02)
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i)
      if (key) {
        expect(localStorage.getItem(key)).not.toBe(testKey)
      }
    }
  })

  /**
   * CR-04: emits 'submitted' and 'close' ONLY after the async setKey call resolves
   * successfully. The previous implementation emitted close synchronously before the
   * await, so the modal was hidden before error feedback could render (vacuous pass).
   */
  it('emits submitted and close events after successful key submission', async () => {
    // Mock setKey to resolve successfully
    const aiStore = useAiStore()
    vi.spyOn(aiStore, 'setKey').mockResolvedValue(undefined)

    const wrapper = mount(BYOKeyModal, {
      props: { open: true },
    })

    // Type a key so the submit button is enabled
    await wrapper.find('input[type="password"]').setValue('sk-test-key-123')

    const form = wrapper.find('form')
    if (form.exists()) {
      await form.trigger('submit')
    }
    // Flush all pending promises — required because setKey is now awaited before emitting
    await flushPromises()

    // Should emit 'submitted' and 'close' on success
    expect(wrapper.emitted('submitted')).toBeTruthy()
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  /**
   * CR-04 / WR-05: on setKey failure, modal must stay open and show submitError.
   *
   * Previously the modal always closed before the async call resolved, so the error
   * message was never visible. Now the modal stays open and shows the error message.
   */
  it('stays open and shows error when setKey rejects', async () => {
    // Mock setKey to reject (simulates a bad key / network error)
    const aiStore = useAiStore()
    vi.spyOn(aiStore, 'setKey').mockRejectedValue(new Error('401 Unauthorized'))

    const wrapper = mount(BYOKeyModal, {
      props: { open: true },
    })

    await wrapper.find('input[type="password"]').setValue('bad-key')

    const form = wrapper.find('form')
    if (form.exists()) {
      await form.trigger('submit')
    }
    await flushPromises()

    // Modal must NOT emit close — it should stay open with an error visible
    expect(wrapper.emitted('close')).toBeFalsy()
    expect(wrapper.emitted('submitted')).toBeFalsy()

    // Error message must be visible in the DOM
    const errorEl = wrapper.find('[role="alert"]')
    expect(errorEl.exists()).toBe(true)
    expect(errorEl.text()).toContain('rejected')
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
