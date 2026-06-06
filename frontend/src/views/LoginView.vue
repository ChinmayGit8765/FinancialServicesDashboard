<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { personas, type PersonaInfo } from '../api/auth'

const router = useRouter()
const authStore = useAuthStore()

const username = ref('')
const password = ref('')
const errorMessage = ref('')
const loading = ref(false)
const personaList = ref<PersonaInfo[]>([])

const DEMO_PASSWORD = 'demo1234'

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
    const ok = await authStore.loginAs(p.username, DEMO_PASSWORD)
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
        <p class="hint">Demo password: <code>{{ DEMO_PASSWORD }}</code></p>
        <div class="persona-buttons">
          <button
            v-for="p in personaList"
            :key="p.username"
            class="persona-btn"
            :disabled="loading"
            @click="loginAsPersona(p)"
          >
            <span class="persona-name">{{ p.persona }}</span>
            <span class="persona-username">@{{ p.username }}</span>
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
        <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
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
  background: #0f172a;
  padding: 1rem;
}

.login-card {
  background: #1e293b;
  border-radius: 12px;
  padding: 2.5rem 2rem;
  width: 100%;
  max-width: 420px;
  color: #e2e8f0;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
}

.app-title {
  margin: 0 0 0.25rem;
  font-size: 2rem;
  font-weight: 700;
  color: #38bdf8;
  text-align: center;
}

.app-subtitle {
  margin: 0 0 2rem;
  font-size: 0.85rem;
  color: #94a3b8;
  text-align: center;
}

.persona-switcher h2 {
  margin: 0 0 0.25rem;
  font-size: 0.9rem;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.hint {
  margin: 0 0 0.75rem;
  font-size: 0.8rem;
  color: #64748b;
}

.hint code {
  background: #0f172a;
  padding: 0.1em 0.4em;
  border-radius: 4px;
  color: #38bdf8;
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
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0.75rem 0.5rem;
  background: #0f172a;
  border: 1px solid #334155;
  border-radius: 8px;
  cursor: pointer;
  color: #e2e8f0;
  transition: border-color 0.15s, background 0.15s;
}

.persona-btn:hover:not(:disabled) {
  border-color: #38bdf8;
  background: #1e3a5f;
}

.persona-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.persona-name {
  font-weight: 600;
  font-size: 0.95rem;
}

.persona-username {
  font-size: 0.75rem;
  color: #64748b;
  margin-top: 0.15rem;
}

.divider {
  text-align: center;
  color: #475569;
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
  background: #334155;
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
  gap: 0.25rem;
}

.field label {
  font-size: 0.8rem;
  color: #94a3b8;
}

.field input {
  padding: 0.6rem 0.75rem;
  background: #0f172a;
  border: 1px solid #334155;
  border-radius: 6px;
  color: #e2e8f0;
  font-size: 0.95rem;
  outline: none;
  transition: border-color 0.15s;
}

.field input:focus {
  border-color: #38bdf8;
}

.error {
  color: #f87171;
  font-size: 0.85rem;
  margin: 0;
}

.submit-btn {
  padding: 0.7rem;
  background: #0ea5e9;
  border: none;
  border-radius: 6px;
  color: #fff;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
}

.submit-btn:hover:not(:disabled) {
  background: #38bdf8;
}

.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
