<script setup lang="ts">
import { computed } from 'vue'
import { deltaClass } from '@/utils/format'

const props = defineProps<{ value: number; formatted: string }>()
const cls = computed(() => deltaClass(props.value))
</script>

<template>
  <span :class="['signed-value', cls]">{{ formatted }}</span>
</template>

<style scoped>
.val-up   { color: var(--color-up); }
.val-down { color: var(--color-down); }
.val-flat { color: var(--color-warn); }
.signed-value {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
}
</style>
