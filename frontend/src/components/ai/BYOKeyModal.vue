<script setup lang="ts">
/**
 * BYOKeyModal — Bring-Your-Own-Key popup component.
 *
 * Stub component created in Plan 06-01 so test scaffolds resolve.
 * Full implementation in Plan 06-04.
 *
 * Props:
 *   open: boolean — whether the modal is visible
 *
 * Emits:
 *   close      — when modal is dismissed (cancel or ESC)
 *   submitted  — after a key is successfully submitted
 */
defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  close: []
  submitted: []
}>()
</script>

<template>
  <!-- TODO 06-04: implement full BYOKeyModal UI -->
  <!-- Stub: renders minimal structure so tests resolve imports -->
  <div v-if="open" class="byo-key-modal" role="dialog" aria-modal="true">
    <form @submit.prevent="emit('submitted')">
      <fieldset>
        <legend>Provider</legend>
        <label>
          <input type="radio" name="provider" value="anthropic" />
          Anthropic Claude
        </label>
        <label>
          <input type="radio" name="provider" value="openai" />
          OpenAI GPT
        </label>
      </fieldset>
      <label>
        API Key
        <input type="password" autocomplete="new-password" placeholder="Paste your API key" />
      </label>
      <button type="button" @click="emit('close')">Cancel</button>
      <button type="submit">Connect Live AI</button>
    </form>
  </div>
</template>
