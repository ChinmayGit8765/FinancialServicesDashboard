<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { usePortfolioStore } from '../stores/portfolio'
import { personas, type PersonaInfo } from '../api/auth'

const router = useRouter()
const authStore = useAuthStore()
const portfolioStore = usePortfolioStore()

const personaList = ref<PersonaInfo[]>([])
const switching = ref(false)
const switchingTo = ref<string | null>(null)

onMounted(async () => {
  try {
    personaList.value = await personas()
  } catch {
    // non-fatal — persona pills will be empty but the bar renders
  }
})

/**
 * switchPersona — debounced with a `switching` flag (Pattern 5, T-03-12).
 * loginAs() already awaits refresh() internally, so portfolioId is updated
 * before refreshAll() hits the new session. Rapid second click is ignored
 * while the first switch is in-flight.
 */
async function switchPersona(username: string): Promise<void> {
  if (switching.value) return
  if (authStore.username === username) return
  switching.value = true
  switchingTo.value = username
  try {
    const ok = await authStore.loginAs(username, 'demo1234')
    if (ok) {
      await portfolioStore.refreshAll()
    }
  } finally {
    switching.value = false
    switchingTo.value = null
  }
}

async function handleLogout(): Promise<void> {
  await authStore.logout()
  await router.push('/login')
}
</script>

<template>
  <header class="top-bar" role="banner">
    <!-- Left: brand wordmark -->
    <div class="brand">
      <span class="brand-name">QuantLens</span>
      <span class="brand-subtitle">AI Portfolio Intelligence</span>
    </div>

    <!-- Center: persona switcher -->
    <nav aria-label="Persona switcher" class="persona-nav">
      <button
        v-for="p in personaList"
        :key="p.username"
        class="persona-pill"
        :class="{ active: authStore.username === p.username }"
        :aria-pressed="authStore.username === p.username"
        :disabled="switching"
        @click="switchPersona(p.username)"
      >
        <template v-if="switching && switchingTo === p.username">
          Switching to {{ p.persona }}&hellip;
        </template>
        <template v-else>
          {{ p.persona }}
        </template>
      </button>
    </nav>

    <!-- Right: username + logout -->
    <div class="user-area">
      <span class="username-display">@{{ authStore.username }}</span>
      <button class="logout-btn" @click="handleLogout">Log out</button>
    </div>
  </header>
</template>

<style scoped>
.top-bar {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 48px;
  z-index: 100;
  background: var(--color-bg-elevated);
  border-bottom: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-lg);
  gap: var(--space-md);
  box-sizing: border-box;
}

/* --- Brand --- */
.brand {
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
  flex-shrink: 0;
}

.brand-name {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-accent);
  line-height: 1;
  white-space: nowrap;
}

.brand-subtitle {
  font-size: 11px;
  color: var(--color-text-muted);
  white-space: nowrap;
}

/* --- Persona switcher --- */
.persona-nav {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.persona-pill {
  min-height: 44px;
  padding: 6px 16px;
  border-radius: var(--radius-pill);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-secondary);
  transition: border-color 0.15s, background 0.15s, color 0.15s;
  white-space: nowrap;
}

.persona-pill:hover:not(:disabled):not(.active) {
  border-color: var(--color-accent-light);
  color: var(--color-text-primary);
}

.persona-pill.active {
  border-color: var(--color-accent);
  background: var(--color-accent-subtle);
  color: var(--color-text-primary);
}

.persona-pill:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.persona-pill:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 3px;
}

/* --- User area --- */
.user-area {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-shrink: 0;
}

.username-display {
  font-size: 14px;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.logout-btn {
  min-height: 44px;
  padding: 6px 14px;
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s;
  white-space: nowrap;
}

.logout-btn:hover {
  border-color: var(--color-destructive);
  color: var(--color-destructive);
}

.logout-btn:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 3px;
}
</style>
