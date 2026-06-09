<script setup lang="ts">
/**
 * AiModeBadge — read-only pill in the TopBar showing AI mode (Demo / Live).
 *
 * Props-driven: receives mode + provider from parent (DashboardView wires
 * useAiStore().status.data). The store reads /api/ai/status on mount.
 *
 * Renders:
 *   Demo mode → muted pill ("Demo")
 *   Live mode  → accent/up-color pill ("Live · <provider name>")
 */
const props = defineProps<{
  mode: 'demo' | 'live'
  provider: string | null
}>()

function providerLabel(p: string | null): string {
  if (!p) return ''
  if (p === 'anthropic') return 'Claude'
  if (p === 'openai')    return 'GPT'
  return p
}
</script>

<template>
  <span
    class="ai-mode-badge"
    :class="props.mode === 'live' ? 'ai-mode-badge--live' : 'ai-mode-badge--demo'"
    :aria-label="`AI mode: ${props.mode}`"
  >
    <template v-if="props.mode === 'live'">
      Live<template v-if="props.provider"> · {{ providerLabel(props.provider) }}</template>
    </template>
    <template v-else>
      Demo
    </template>
  </span>
</template>

<style scoped>
.ai-mode-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: var(--radius-pill);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  white-space: nowrap;
  border: 1px solid transparent;
  line-height: 20px;
}

.ai-mode-badge--demo {
  color: var(--color-text-muted);
  border-color: var(--color-border);
  background: transparent;
}

.ai-mode-badge--live {
  color: var(--color-up);
  border-color: var(--color-up);
  background: var(--color-up-subtle);
}
</style>
