<script setup lang="ts">
/**
 * ExplainDrawer — slide-out panel showing AI explanation for a holding.
 *
 * Stub component created in Plan 06-01 so test scaffolds resolve.
 * Full implementation in Plan 06-04.
 *
 * Props:
 *   open:      boolean — whether the drawer is open
 *   narrative: string | null — the explanation text
 *   loading:   boolean
 *   error:     string | null
 */
const props = defineProps<{
  open: boolean
  narrative: string | null
  loading: boolean
  error: string | null
}>()
</script>

<template>
  <!-- TODO 06-04: implement full ExplainDrawer with slide-in panel + shimmer skeleton -->
  <!-- Stub: renders minimal structure so tests can assert on states -->
  <div v-if="props.open" class="explain-drawer">
    <template v-if="props.loading">
      <div class="skeleton" style="height: 120px" aria-hidden="true" />
    </template>
    <template v-else-if="props.error">
      <div class="chart-error" role="alert">
        <span>Failed to load explanation. Please try again.</span>
      </div>
    </template>
    <template v-else>
      <p class="narrative">{{ props.narrative }}</p>
    </template>
  </div>
</template>
