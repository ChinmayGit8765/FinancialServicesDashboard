<script setup lang="ts">
/**
 * CommentaryCard — full-width card showing AI daily portfolio commentary.
 *
 * Three states: shimmer skeleton (loading), static error copy + retry (error),
 * populated (headline + body + bullet points).
 *
 * Replaces the "AI Daily Commentary — Phase 6" SlotPlaceholder in DashboardView.
 */
import type { CommentaryDto } from '../../api/ai'

const props = defineProps<{
  commentary: CommentaryDto | null
  loading: boolean
  error: string | null
}>()

const emit = defineEmits<{
  retry: []
}>()
</script>

<template>
  <div class="commentary-card">

    <!-- Loading: shimmer skeleton (~120px per SlotPlaceholder height).
         Also covers the brief null window before the first response so the card never
         flashes muted "loading…" placeholder text — it screenshots cleanly mid-load. -->
    <template v-if="props.loading || (!props.commentary && !props.error)">
      <div class="skeleton" style="height: 20px; width: 55%; margin-bottom: 12px" aria-hidden="true" />
      <div class="skeleton" style="height: 14px; margin-bottom: 8px" aria-hidden="true" />
      <div class="skeleton" style="height: 14px; margin-bottom: 8px" aria-hidden="true" />
      <div class="skeleton" style="height: 14px; width: 70%; margin-bottom: 12px" aria-hidden="true" />
      <div class="skeleton" style="height: 12px; width: 45%; margin-bottom: 6px" aria-hidden="true" />
      <div class="skeleton" style="height: 12px; width: 50%; margin-bottom: 6px" aria-hidden="true" />
      <div class="skeleton" style="height: 12px; width: 40%" aria-hidden="true" />
    </template>

    <!-- Error: static copy only (T-06-12) -->
    <div
      v-else-if="props.error"
      class="commentary-error"
      role="alert"
    >
      <span>Unable to load AI commentary. Check your connection and try again.</span>
      <button class="retry-btn" @click="emit('retry')">Retry</button>
    </div>

    <!-- Populated -->
    <template v-else-if="props.commentary">
      <div class="commentary-header">
        <span class="commentary-label">AI Commentary</span>
      </div>
      <h3 class="commentary-headline">{{ props.commentary.headline }}</h3>
      <p class="commentary-body">{{ props.commentary.body }}</p>
      <ul class="commentary-bullets">
        <li
          v-for="(point, index) in props.commentary.bulletPoints"
          :key="index"
          class="bullet-item"
        >
          {{ point }}
        </li>
      </ul>
    </template>

  </div>
</template>

<style scoped>
.commentary-card {
  background: var(--color-bg-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  border: 1px solid var(--color-border);
  padding: var(--space-xl);
  min-height: 120px;
}

.commentary-header {
  margin-bottom: var(--space-md);
}

.commentary-label {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--color-accent);
}

.commentary-headline {
  font-size: 19px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 var(--space-md) 0;
  line-height: 1.45;
  letter-spacing: -0.01em;
}

.commentary-body {
  font-size: 15px;
  line-height: 1.75;
  color: var(--color-text-secondary);
  margin: 0 0 var(--space-lg) 0;
  max-width: 76ch;
}

.commentary-bullets {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.bullet-item {
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text-secondary);
  padding-left: var(--space-md);
  position: relative;
}

.bullet-item::before {
  content: '·';
  position: absolute;
  left: var(--space-xs);
  color: var(--color-accent);
  font-weight: 700;
}

.commentary-error {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  color: var(--color-down);
  border: 1px solid var(--color-down);
  border-radius: var(--radius-md);
  padding: var(--space-md);
  font-size: 14px;
}

.retry-btn {
  background: transparent;
  border: 1px solid var(--color-accent);
  color: var(--color-accent);
  border-radius: var(--radius-sm);
  padding: 4px 12px;
  cursor: pointer;
  font-size: 13px;
  min-height: 28px;
  flex-shrink: 0;
}

.retry-btn:hover {
  background: var(--color-accent-subtle);
}

.retry-btn:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 3px;
}

.commentary-empty {
  font-size: 14px;
  color: var(--color-text-muted);
}

@keyframes shimmer {
  0%   { background-position: -200% center; }
  100% { background-position:  200% center; }
}

.skeleton {
  display: block;
  border-radius: var(--radius-sm);
  background: linear-gradient(
    90deg,
    var(--color-bg-surface) 25%,
    var(--color-bg-overlay) 50%,
    var(--color-bg-surface) 75%
  );
  background-size: 200% auto;
  animation: shimmer 1.4s linear infinite;
}
</style>
