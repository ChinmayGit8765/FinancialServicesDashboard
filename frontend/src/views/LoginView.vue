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
  <div class="login-page">
    <div class="login-card">
      <h1 class="app-title">QuantLens</h1>
      <p class="app-subtitle">AI Portfolio &amp; Market Intelligence Dashboard</p>

      <!-- One-click persona switcher -->
      <section v-if="personaList.length > 0" class="persona-switcher">
        <h2>Quick Login</h2>
        <p class="hint">Demo password: <code>{{ personaList[0]?.passwordHint }}</code></p>
        <div class="persona-buttons">
          <button
            v-for="p in personaList"
            :key="p.username"
            class="persona-btn"
            :disabled="loading"
            @click="loginAsPersona(p)"
          >
            <span class="persona-name">Log in as {{ p.persona }}</span>
            <span class="persona-username">@{{ p.username }}</span>
            <span v-if="p.description" class="persona-desc">{{ p.description }}</span>
          </button>
        </div>
      </section>

      <div class="divider">or log in manually</div>

      <!-- Manual login form -->
      <form class="login-form" @submit.prevent="handleLogin">
        <div class="field">
          <label for="username">Username</label>
          <input
            id="username"
            v-model="username"
            type="text"
            placeholder="alice"
            autocomplete="username"
            :disabled="loading"
          />
        </div>
        <div class="field">
          <label for="password">Password</label>
          <input
            id="password"
            v-model="password"
            type="password"
            placeholder="demo1234"
            autocomplete="current-password"
            :disabled="loading"
          />
        </div>
        <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
        <button type="submit" class="submit-btn" :disabled="loading">
          {{ loading ? 'Logging in…' : 'Log In' }}
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-base);
  padding: var(--space-md);
}

.login-card {
  background: var(--color-bg-surface);
  border-radius: var(--radius-lg);
  padding: 2.5rem 2rem;
  width: 100%;
  max-width: 420px;
  color: var(--color-text-primary);
  box-shadow: var(--shadow-modal);
}

.app-title {
  margin: 0 0 var(--space-xs);
  font-size: 24px;
  font-weight: 700;
  color: var(--color-accent);
  text-align: center;
}

.app-subtitle {
  margin: 0 0 var(--space-xl);
  font-size: 0.85rem;
  color: var(--color-text-secondary);
  text-align: center;
}

.persona-switcher h2 {
  margin: 0 0 var(--space-xs);
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.hint {
  margin: 0 0 0.75rem;
  font-size: 0.8rem;
  color: var(--color-text-muted);
}

.hint code {
  background: var(--color-bg-elevated);
  padding: 0.1em 0.4em;
  border-radius: var(--radius-sm);
  color: var(--color-accent);
}

.persona-buttons {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  margin-bottom: 1rem;
}

.persona-btn {
  flex: 1;
  min-width: 110px;
  min-height: 44px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0.75rem 0.5rem;
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  color: var(--color-text-primary);
  transition: border-color 0.15s, background 0.15s;
}

.persona-btn:hover:not(:disabled) {
  border-color: var(--color-accent-light);
  background: var(--color-accent-subtle);
}

.persona-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.persona-btn:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 3px;
}

.persona-name {
  font-weight: 600;
  font-size: 0.95rem;
}

.persona-username {
  font-size: 0.75rem;
  color: var(--color-text-muted);
  margin-top: 0.15rem;
}

.persona-desc {
  font-size: 11px;
  color: var(--color-text-muted);
  margin-top: 0.1rem;
}

.divider {
  text-align: center;
  color: var(--color-text-muted);
  font-size: 0.8rem;
  margin: 1.25rem 0;
  position: relative;
}

.divider::before,
.divider::after {
  content: '';
  position: absolute;
  top: 50%;
  width: 35%;
  height: 1px;
  background: var(--color-border);
}

.divider::before { left: 0; }
.divider::after { right: 0; }

.login-form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

.field label {
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.field input {
  padding: 0.6rem 0.75rem;
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text-primary);
  font-size: 0.95rem;
  transition: border-color 0.15s;
}

.field input:focus {
  border-color: var(--color-accent);
}

.field input:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 2px;
}

.error {
  color: var(--color-destructive);
  font-size: 0.85rem;
  margin: 0;
}

.submit-btn {
  padding: 0.7rem;
  background: var(--color-accent);
  border: none;
  border-radius: var(--radius-md);
  color: var(--color-text-inverse);
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
  min-height: 44px;
}

.submit-btn:hover:not(:disabled) {
  background: var(--color-accent-light);
}

.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
