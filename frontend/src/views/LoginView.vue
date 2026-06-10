<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { personas } from '../api/auth'

// PersonaInfo extended locally with optional description (backend currently omits it;
// the v-if below degrades gracefully when description is absent).
interface PersonaInfo {
  username: string
  persona: string
  passwordHint: string
  description?: string
}

const router = useRouter()
const authStore = useAuthStore()

const username = ref('')
const password = ref('')
const errorMessage = ref('')
const loading = ref(false)
const personaList = ref<PersonaInfo[]>([])

onMounted(async () => {
  // Fetch persona list for the one-click switcher
  try {
    personaList.value = await personas()
  } catch {
    // Non-fatal — the manual form still works
  }
})

async function handleLogin() {
  if (!username.value || !password.value) {
    errorMessage.value = 'Please enter username and password.'
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const ok = await authStore.loginAs(username.value, password.value)
    if (ok) {
      await router.push('/')
    } else {
      errorMessage.value = 'Invalid credentials. Try one of the demo personas below.'
    }
  } finally {
    loading.value = false
  }
}

async function loginAsPersona(p: PersonaInfo) {
  loading.value = true
  errorMessage.value = ''
  try {
    const ok = await authStore.loginAs(p.username, p.passwordHint)
    if (ok) {
      await router.push('/')
    } else {
      errorMessage.value = `Could not log in as ${p.username}. Is the backend running?`
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div
    class="relative flex min-h-screen items-center justify-center overflow-hidden bg-base px-4 py-10 text-ink"
  >
    <!-- ambient brand glow -->
    <div class="pointer-events-none absolute -top-40 left-1/2 h-[480px] w-[480px] -translate-x-1/2 rounded-full bg-brand/20 blur-[140px]"></div>
    <div class="pointer-events-none absolute bottom-0 right-0 h-[360px] w-[360px] translate-x-1/3 translate-y-1/3 rounded-full bg-brand-light/10 blur-[120px]"></div>

    <div
      class="relative z-10 w-full max-w-md rounded-2xl border border-edge/70 bg-surface/80 p-8 shadow-float backdrop-blur-xl"
    >
      <!-- brand -->
      <div class="mb-8 flex flex-col items-center text-center">
        <div class="mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br from-brand to-brand-light shadow-glow">
          <svg viewBox="0 0 24 24" class="h-6 w-6 text-ink-inverse" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
            <path d="M3 17l5-6 4 4 6-8" />
            <path d="M3 21h18" />
          </svg>
        </div>
        <h1 class="bg-gradient-to-r from-brand-light to-brand bg-clip-text text-2xl font-extrabold tracking-tight text-transparent">
          QuantLens
        </h1>
        <p class="mt-1 text-sm text-ink-soft">AI Portfolio &amp; Market Intelligence</p>
      </div>

      <!-- One-click persona switcher -->
      <section v-if="personaList.length > 0" class="mb-6">
        <div class="mb-3 flex items-center justify-between">
          <h2 class="text-[11px] font-semibold uppercase tracking-[0.12em] text-ink-soft">Quick login</h2>
          <span class="text-xs text-ink-muted">
            pass <code class="rounded bg-elevated px-1.5 py-0.5 font-mono text-brand">{{ personaList[0]?.passwordHint }}</code>
          </span>
        </div>
        <div class="grid grid-cols-3 gap-2">
          <button
            v-for="p in personaList"
            :key="p.username"
            type="button"
            class="group flex flex-col items-center rounded-xl border border-edge bg-elevated px-2 py-3 transition hover:-translate-y-0.5 hover:border-brand hover:bg-brand-deep/40 hover:shadow-glow disabled:cursor-not-allowed disabled:opacity-50"
            :disabled="loading"
            @click="loginAsPersona(p)"
          >
            <span class="text-sm font-semibold text-ink group-hover:text-brand-light">{{ p.persona }}</span>
            <span class="mt-0.5 text-[11px] text-ink-muted">@{{ p.username }}</span>
          </button>
        </div>
      </section>

      <!-- divider -->
      <div class="my-5 flex items-center gap-3 text-[11px] uppercase tracking-wider text-ink-muted">
        <span class="h-px flex-1 bg-edge"></span>
        or log in manually
        <span class="h-px flex-1 bg-edge"></span>
      </div>

      <!-- Manual login form -->
      <form class="flex flex-col gap-3" @submit.prevent="handleLogin">
        <div class="flex flex-col gap-1.5">
          <label for="username" class="text-xs text-ink-soft">Username</label>
          <input
            id="username"
            v-model="username"
            type="text"
            placeholder="alice"
            autocomplete="username"
            :disabled="loading"
            class="rounded-lg border border-edge bg-elevated px-3 py-2.5 text-sm text-ink placeholder:text-ink-muted transition focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/40 disabled:opacity-50"
          />
        </div>
        <div class="flex flex-col gap-1.5">
          <label for="password" class="text-xs text-ink-soft">Password</label>
          <input
            id="password"
            v-model="password"
            type="password"
            placeholder="demo1234"
            autocomplete="current-password"
            :disabled="loading"
            class="rounded-lg border border-edge bg-elevated px-3 py-2.5 text-sm text-ink placeholder:text-ink-muted transition focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/40 disabled:opacity-50"
          />
        </div>
        <p v-if="errorMessage" class="text-sm text-down" role="alert">{{ errorMessage }}</p>
        <button
          type="submit"
          :disabled="loading"
          class="mt-1 min-h-11 rounded-lg bg-gradient-to-r from-brand to-brand-light px-4 py-2.5 text-sm font-semibold text-ink-inverse shadow-glow transition hover:brightness-110 focus:outline-none focus:ring-2 focus:ring-brand/50 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {{ loading ? 'Logging in…' : 'Log In' }}
        </button>
      </form>
    </div>
  </div>
</template>
