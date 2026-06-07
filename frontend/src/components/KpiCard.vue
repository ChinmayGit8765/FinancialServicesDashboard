<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  label: string
  primary: string
  secondary?: string
  delta?: number
  loading: boolean
}>()

const deltaColorClass = computed(() => {
  if (props.delta === undefined || props.delta === null) return 'kpi-flat'
  if (props.delta > 0) return 'kpi-up'
  if (props.delta < 0) return 'kpi-down'
  return 'kpi-flat'
})

const borderClass = computed(() => {
  if (props.delta === undefined || props.delta === null) return 'border-flat'
  if (props.delta > 0) return 'border-up'
  if (props.delta < 0) return 'border-down'
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
      {{ delta > 0 ? '+' : '' }}{{ delta.toFixed(2) }}%
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
  gap: 4px;
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
