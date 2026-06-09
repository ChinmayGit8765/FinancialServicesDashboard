<script setup lang="ts">
/**
 * ExplainDrawer — slide-in panel showing AI narrative for a holding.
 *
 * Fixed-position panel on the right side, z-index 200 (above TopBar at 100).
 * Three states: loading (shimmer skeleton), error (static copy + retry), populated (narrative).
 * Hidden entirely when open=false.
 */
const props = defineProps<{
  open: boolean
  narrative: string | null
  loading: boolean
  error: string | null
}>()

const emit = defineEmits<{
  close: []
}>()
</script>

<template>
  <template v-if="props.open">
    <!-- Overlay (below drawer, above dashboard) -->
    <div
      class="drawer-overlay"
      role="presentation"
      @click="emit('close')"
    />

    <!-- Slide-in panel -->
    <aside
      class="explain-drawer"
      role="complementary"
      aria-label="Position explanation"
    >
      <!-- Header -->
      <div class="drawer-header">
        <h3 class="drawer-title">Explain This Position</h3>
        <button
          class="drawer-close"
          aria-label="Close explanation"
          @click="emit('close')"
        >
          ✕
        </button>
      </div>

      <!-- Content -->
      <div class="drawer-body">

        <!-- Loading: shimmer skeleton -->
        <template v-if="props.loading">
          <div class="skeleton" style="height: 24px; width: 60%; margin-bottom: 12px" aria-hidden="true" />
          <div class="skeleton" style="height: 16px; margin-bottom: 8px" aria-hidden="true" />
          <div class="skeleton" style="height: 16px; margin-bottom: 8px" aria-hidden="true" />
          <div class="skeleton" style="height: 16px; width: 80%" aria-hidden="true" />
        </template>

        <!-- Error: static copy only (T-06-12) -->
        <div
          v-else-if="props.error"
          class="drawer-error"
          role="alert"
        >
          <span>Unable to load explanation. Please try again.</span>
          <button class="retry-btn" @click="emit('close')">Close</button>
        </div>

        <!-- Populated: narrative -->
        <template v-else-if="props.narrative">
          <p class="narrative">{{ props.narrative }}</p>
        </template>

        <!-- Empty: no data yet (drawer just opened, fetch not started) -->
        <div v-else class="drawer-empty">
          Select a holding to see its AI explanation.
        </div>

      </div>
    </aside>
  </template>
</template>

<style scoped>
.drawer-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  z-index: 199;
}

.explain-drawer {
  position: fixed;
  top: 0;
  right: 0;
  height: 100vh;
  width: 400px;
  max-width: 90vw;
  background: var(--color-bg-elevated);
  border-left: 1px solid var(--color-border);
  box-shadow: var(--shadow-card);
  z-index: 200;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-lg);
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}

.drawer-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0;
}

.drawer-close {
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  color: var(--color-text-secondary);
  font-size: 14px;
  cursor: pointer;
  padding: 4px 10px;
  min-height: 32px;
  transition: border-color 0.15s, color 0.15s;
}

.drawer-close:hover {
  border-color: var(--color-text-primary);
  color: var(--color-text-primary);
}

.drawer-close:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 3px;
}

.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-lg);
}

.narrative {
  font-size: 15px;
  line-height: 1.7;
  color: var(--color-text-secondary);
  margin: 0;
  white-space: pre-wrap;
}

.drawer-error {
  display: flex;
  flex-direction: column;
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
  align-self: flex-start;
}

.retry-btn:hover {
  background: var(--color-accent-subtle);
}

.retry-btn:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 3px;
}

.drawer-empty {
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
