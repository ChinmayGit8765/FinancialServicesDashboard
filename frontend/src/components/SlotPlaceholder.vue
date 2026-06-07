<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ label: string; minHeight?: string }>()
const height = computed(() => props.minHeight ?? '240px')
</script>

<template>
  <div class="slot-placeholder" :style="{ minHeight: height }">
    <span class="slot-label">{{ label }}</span>
  </div>
</template>

<style scoped>
.slot-placeholder {
  background: var(--color-bg-surface);
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
}
.slot-label {
  font-size: 13px;
  color: var(--color-text-muted);
}
</style>
