<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  label: string
  primary: string
  secondary?: string
  delta?: number
  loading: boolean
}>()

// A delta that rounds to 0.00 (|delta| < 0.005) is treated as flat for both
// color and display — prevents a tiny negative like -0.003 from showing a red
// border while the text reads "0.00%" (sign-color contradiction, CR-02).
const effectiveDelta = computed<number | null>(() => {
  if (props.delta === undefined || props.delta === null) return null
  return Math.abs(props.delta) < 0.005 ? 0 : props.delta
})

// Pre-formatted delta text: "+2.34%", "-1.12%", or "0.00%"
const deltaFormatted = computed<string | null>(() => {
  const d = effectiveDelta.value
  if (d === null) return null
  const abs = Math.abs(d).toFixed(2)
  if (d > 0) return `+${abs}%`
  if (d < 0) return `-${abs}%`
  return `0.00%`
})

const deltaColorClass = computed(() => {
  const d = effectiveDelta.value
  if (d === null) return 'kpi-flat'
  if (d > 0) return 'kpi-up'
  if (d < 0) return 'kpi-down'
  return 'kpi-flat'
})

const borderClass = computed(() => {
  const d = effectiveDelta.value
  if (d === null) return 'border-flat'
  if (d > 0) return 'border-up'
  if (d < 0) return 'border-down'
  return 'border-flat'
})
</script>

<template>
  <div
    v-if="loading"
    class="skeleton"
    :aria-busy="true"
    :aria-label="`Loading ${label}`"
  />
  <div
    v-else
    class="group flex min-h-[92px] flex-col gap-1 rounded-xl border border-edge bg-surface p-4 transition hover:-translate-y-0.5 hover:border-brand/50 hover:shadow-card"
    :class="borderClass"
  >
    <span class="text-[11px] font-semibold uppercase tracking-[0.06em] text-ink-muted">{{ label }}</span>
    <span class="font-mono text-[22px] font-semibold leading-tight tabular-nums text-ink">{{ primary }}</span>
    <span v-if="secondary" class="font-mono text-sm tabular-nums text-ink-soft">{{ secondary }}</span>
    <span
      v-if="delta !== undefined && delta !== null"
      class="kpi-delta font-mono text-sm tabular-nums"
      :class="deltaColorClass"
    >
      {{ deltaFormatted }}
    </span>
  </div>
</template>

<style scoped>
/* Semantic accent classes — kept as stable hooks (KpiCard.test.ts asserts these) and to carry
   the left-border + delta colours that Tailwind utilities alone wouldn't express semantically. */
.border-up   { border-left: 3px solid var(--color-up); }
.border-down { border-left: 3px solid var(--color-down); }
.border-flat { border-left: 3px solid var(--color-border); }

.kpi-up   { color: var(--color-up); }
.kpi-down { color: var(--color-down); }
.kpi-flat { color: var(--color-warn); }

@keyframes shimmer {
  0%   { background-position: -200% center; }
  100% { background-position:  200% center; }
}

.skeleton {
  background: linear-gradient(
    90deg,
    var(--color-bg-surface) 25%,
    var(--color-bg-overlay) 50%,
    var(--color-bg-surface) 75%
  );
  background-size: 200% auto;
  animation: shimmer 1.4s linear infinite;
  border-radius: var(--radius-lg);
  height: 92px;
  border: 1px solid var(--color-border);
}
</style>
