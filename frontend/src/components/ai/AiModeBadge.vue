<script setup lang="ts">
/**
 * AiModeBadge — small badge in the top bar indicating demo or live mode.
 *
 * Stub component created in Plan 06-01 so test scaffolds resolve.
 * Full implementation in Plan 06-04.
 *
 * Props:
 *   mode:     'demo' | 'live'
 *   provider: 'anthropic' | 'openai' | null
 */
const props = defineProps<{
  mode: 'demo' | 'live'
  provider: string | null
}>()
</script>

<template>
  <!-- TODO 06-04: implement full AiModeBadge with CSS token styling -->
  <!-- Stub: renders minimal badge so tests can assert on mode text -->
  <span
    class="ai-mode-badge"
    :class="props.mode === 'live' ? 'ai-mode-badge--live' : 'ai-mode-badge--demo'"
  >
    {{ props.mode === 'live' ? 'Live' : 'Demo' }}
  </span>
</template>
