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
  <div v-else class="kpi-card" :class="borderClass">
    <span class="kpi-label">{{ label }}</span>
    <span class="kpi-primary">{{ primary }}</span>
    <span v-if="secondary" class="kpi-secondary">{{ secondary }}</span>
    <span v-if="delta !== undefined && delta !== null" class="kpi-delta" :class="deltaColorClass">
      {{ deltaFormatted }}
    </span>
  </div>
</template>

<style scoped>
.kpi-card {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--space-md);
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  min-height: 88px;
}

.border-up   { border-left: 3px solid var(--color-up); }
.border-down { border-left: 3px solid var(--color-down); }
.border-flat { border-left: 3px solid var(--color-border); }

.kpi-label {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-text-muted);
}

.kpi-primary {
  font-size: 20px;
  font-weight: 600;
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-primary);
  line-height: 1.2;
}

.kpi-secondary {
  font-size: 14px;
  color: var(--color-text-secondary);
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
}

.kpi-delta {
  font-size: 14px;
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
}

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
  border-radius: var(--radius-md);
  height: 88px;
  border: 1px solid var(--color-border);
}
</style>
