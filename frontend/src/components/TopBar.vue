<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { usePortfolioStore } from '../stores/portfolio'
import { useAiStore } from '../stores/ai'
import { personas, type PersonaInfo } from '../api/auth'
import AiModeBadge from './ai/AiModeBadge.vue'

const router = useRouter()
const authStore = useAuthStore()
const portfolioStore = usePortfolioStore()
const aiStore = useAiStore()

// Derive mode + provider from ai store status for the badge
const aiMode = computed<'demo' | 'live'>(() => aiStore.status.data?.mode ?? 'demo')
const aiProvider = computed<string | null>(() => aiStore.status.data?.provider ?? null)

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
  <header
    role="banner"
    class="fixed inset-x-0 top-0 z-50 flex h-14 items-center justify-between gap-4 border-b border-edge/70 bg-elevated/85 px-4 backdrop-blur-xl sm:px-6"
  >
    <!-- Left: brand -->
    <div class="flex shrink-0 items-center gap-2.5">
      <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-brand to-brand-light shadow-glow">
        <svg viewBox="0 0 24 24" class="h-4 w-4 text-ink-inverse" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round">
          <path d="M3 17l5-6 4 4 6-8" />
          <path d="M3 21h18" />
        </svg>
      </div>
      <div class="flex flex-col leading-tight">
        <span class="text-[15px] font-bold tracking-tight text-ink">QuantLens</span>
        <span class="hidden text-[10px] text-ink-muted sm:block">AI Portfolio Intelligence</span>
      </div>
    </div>

    <!-- Center: persona switcher (segmented control) -->
    <nav
      aria-label="Persona switcher"
      class="hidden items-center gap-1 rounded-full border border-edge/70 bg-base/60 p-1 md:flex"
    >
      <button
        v-for="p in personaList"
        :key="p.username"
        :aria-pressed="authStore.username === p.username"
        :disabled="switching"
        class="rounded-full px-4 py-1.5 text-[13px] font-semibold transition disabled:cursor-not-allowed disabled:opacity-60"
        :class="authStore.username === p.username
          ? 'bg-brand text-ink-inverse shadow-glow'
          : 'text-ink-soft hover:text-ink'"
        @click="switchPersona(p.username)"
      >
        <template v-if="switching && switchingTo === p.username">{{ p.persona }}&hellip;</template>
        <template v-else>{{ p.persona }}</template>
      </button>
    </nav>

    <!-- Right: AI mode badge + username + logout -->
    <div class="flex shrink-0 items-center gap-3">
      <AiModeBadge :mode="aiMode" :provider="aiProvider" />
      <span class="hidden text-sm text-ink-soft sm:inline">@{{ authStore.username }}</span>
      <button
        class="rounded-lg border border-edge px-3 py-1.5 text-[13px] text-ink-soft transition hover:border-down hover:text-down focus:outline-none focus:ring-2 focus:ring-brand/40"
        @click="handleLogout"
      >
        Log out
      </button>
    </div>
  </header>
</template>
