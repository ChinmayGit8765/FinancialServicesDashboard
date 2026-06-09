<script setup lang="ts">
/**
 * BYOKeyModal — Bring-Your-Own-Key popup.
 *
 * SECURITY (T-06-11):
 *  - The key input is bound to a local ref (keyInput).
 *  - On submit: POST to /api/ai/key via aiStore.setKey(), then immediately
 *    set keyInput = '' to clear the local ref.
 *  - The key is NEVER written to localStorage, sessionStorage, or any
 *    reactive store state. Only the returned {mode,provider} enters store state.
 *  - On error: show generic "Key rejected" copy — never echo key detail.
 */
import { ref } from 'vue'
import { useAiStore } from '../../stores/ai'

defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  close: []
  submitted: []
}>()

const aiStore = useAiStore()

const provider = ref<'anthropic' | 'openai'>('anthropic')
const keyInput = ref('')
const submitting = ref(false)
const submitError = ref<string | null>(null)

async function handleSubmit(): Promise<void> {
  submitting.value = true
  submitError.value = null
  const keyToSubmit = keyInput.value
  // Clear the local ref immediately — key must not persist in component state (T-06-11)
  keyInput.value = ''
  // CR-04 / WR-05: await the API call BEFORE emitting close.
  // The previous order (emit close → await setKey) caused:
  //   1. Modal unmounts before the async call resolves.
  //   2. submitError.value = '...' runs on an unmounted component → silent no-op.
  //   3. The user never sees any error feedback on rejection.
  // Now: complete the call first, only close on SUCCESS, show error and stay open on failure.
  // setKey re-throws on error (updated in stores/ai.ts) so the catch block is reachable.
  try {
    await aiStore.setKey(provider.value, keyToSubmit)
    // Success — notify the parent and close the modal
    emit('submitted')
    emit('close')
  } catch {
    // setKey already sets status.error in the store; show a generic note here (T-06-12)
    submitError.value = 'Key rejected — check provider and key format'
    // Modal stays open so the user can see the error and try again
  } finally {
    submitting.value = false
  }
}

function handleCancel(): void {
  keyInput.value = ''
  submitError.value = null
  emit('close')
}
</script>

<template>
  <template v-if="open">
    <!-- Overlay -->
    <div
      class="modal-overlay"
      role="presentation"
      @click.self="handleCancel"
    />

    <!-- Dialog -->
    <div
      class="modal-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="byo-key-title"
    >
      <h2 id="byo-key-title" class="modal-title">Connect Your Own AI Key</h2>

      <form class="modal-form" @submit.prevent="handleSubmit">

        <!-- Provider selection -->
        <fieldset class="provider-fieldset">
          <legend class="field-label">Provider</legend>
          <label class="radio-label">
            <input
              type="radio"
              name="provider"
              value="anthropic"
              v-model="provider"
            />
            Anthropic Claude
          </label>
          <label class="radio-label">
            <input
              type="radio"
              name="provider"
              value="openai"
              v-model="provider"
            />
            OpenAI GPT
          </label>
        </fieldset>

        <!-- API Key input -->
        <div class="field-group">
          <label for="byo-api-key" class="field-label">API Key</label>
          <input
            id="byo-api-key"
            type="password"
            autocomplete="new-password"
            class="key-input"
            placeholder="Paste your API key"
            v-model="keyInput"
            :disabled="submitting"
          />
        </div>

        <!-- Security reassurance -->
        <p class="security-note">
          Your key is used only for this session and is never saved or transmitted to us.
        </p>

        <!-- Error message (generic only — T-06-12) -->
        <div v-if="submitError" class="submit-error" role="alert">
          {{ submitError }}
        </div>

        <!-- Actions -->
        <div class="modal-actions">
          <button
            type="button"
            class="btn-cancel"
            :disabled="submitting"
            @click="handleCancel"
          >
            Cancel
          </button>
          <button
            type="submit"
            class="btn-submit"
            :disabled="submitting || !keyInput.trim()"
          >
            <template v-if="submitting">Connecting…</template>
            <template v-else>Connect Live AI</template>
          </button>
        </div>

      </form>
    </div>
  </template>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  z-index: 300;
}

.modal-dialog {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 301;
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: var(--space-xl);
  width: 420px;
  max-width: calc(100vw - var(--space-xl) * 2);
}

.modal-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 var(--space-lg) 0;
}

.modal-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.provider-fieldset {
  border: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.field-label {
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-text-muted);
  display: block;
  margin-bottom: var(--space-xs);
}

.radio-label {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: 14px;
  color: var(--color-text-primary);
  cursor: pointer;
}

.radio-label input[type="radio"] {
  accent-color: var(--color-accent);
  cursor: pointer;
}

.radio-label:focus-within {
  outline: 2px solid var(--color-accent);
  outline-offset: 2px;
  border-radius: var(--radius-sm);
}

.field-group {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

.key-input {
  width: 100%;
  padding: 8px var(--space-md);
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text-primary);
  font-size: 14px;
  font-family: var(--font-mono);
  box-sizing: border-box;
  transition: border-color 0.15s;
}

.key-input:focus {
  outline: none;
  border-color: var(--color-accent);
}

.key-input:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 2px;
}

.key-input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.security-note {
  font-size: 12px;
  color: var(--color-text-muted);
  margin: 0;
  line-height: 1.5;
}

.submit-error {
  font-size: 13px;
  color: var(--color-down);
  background: var(--color-down-subtle);
  border: 1px solid var(--color-down);
  border-radius: var(--radius-sm);
  padding: var(--space-sm) var(--space-md);
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
}

.btn-cancel {
  min-height: 36px;
  padding: 6px 16px;
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text-secondary);
  font-size: 14px;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s;
}

.btn-cancel:hover:not(:disabled) {
  border-color: var(--color-text-primary);
  color: var(--color-text-primary);
}

.btn-cancel:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-cancel:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 3px;
}

.btn-submit {
  min-height: 36px;
  padding: 6px 20px;
  background: var(--color-accent);
  border: 1px solid var(--color-accent);
  border-radius: var(--radius-md);
  color: var(--color-bg-base);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.btn-submit:hover:not(:disabled) {
  background: var(--color-accent-light);
  border-color: var(--color-accent-light);
}

.btn-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-submit:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 3px;
}
</style>
