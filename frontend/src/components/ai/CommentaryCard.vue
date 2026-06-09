<script setup lang="ts">
/**
 * CommentaryCard — dashboard card showing AI daily portfolio commentary.
 *
 * Stub component created in Plan 06-01 so test scaffolds resolve.
 * Full implementation in Plan 06-04.
 *
 * Props:
 *   commentary: { headline, body, bulletPoints } | null
 *   loading:    boolean
 *   error:      string | null
 */
interface CommentaryData {
  headline: string
  body: string
  bulletPoints: string[]
}

const props = defineProps<{
  commentary: CommentaryData | null
  loading: boolean
  error: string | null
}>()
</script>

<template>
  <!-- TODO 06-04: implement full CommentaryCard with CSS token styling and full skeleton -->
  <!-- Stub: renders minimal structure so tests can assert on states -->
  <div class="commentary-card">
    <template v-if="props.loading">
      <div class="skeleton" style="height: 120px" aria-hidden="true" />
    </template>
    <template v-else-if="props.error">
      <div class="chart-error" role="alert">
        <span>Failed to load commentary. Please try again.</span>
      </div>
    </template>
    <template v-else-if="props.commentary">
      <h3 class="commentary-headline">{{ props.commentary.headline }}</h3>
      <p class="commentary-body">{{ props.commentary.body }}</p>
      <ul class="commentary-bullets">
        <li v-for="(point, index) in props.commentary.bulletPoints" :key="index">
          {{ point }}
        </li>
      </ul>
    </template>
  </div>
</template>
