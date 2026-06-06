import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, logout as apiLogout, me as apiMe } from '../api/auth'

export const useAuthStore = defineStore('auth', () => {
  const username = ref<string | null>(null)
  const persona = ref<string | null>(null)
  const portfolioId = ref<number | null>(null)
  const authenticated = ref(false)
  // Track whether we've completed the first session check
  const initialized = ref(false)

  const isAuthenticated = computed(() => authenticated.value)

  /**
   * loginAs — called by the one-click persona switcher and the login form.
   * Uses the shared demo password "demo1234".
   */
  async function loginAs(usernameArg: string, password: string): Promise<boolean> {
    try {
      const result = await apiLogin(usernameArg, password)
      if (result.authenticated) {
        // Fetch full profile (including portfolioId) after successful login
        await refresh()
        return true
      }
      return false
    } catch {
      return false
    }
  }

  /**
   * refresh — calls /api/auth/me to restore session state after a page reload.
   * If the session is gone (401), clears state silently.
   */
  async function refresh(): Promise<void> {
    const data = await apiMe()
    if (data) {
      username.value = data.username
      persona.value = data.persona
      portfolioId.value = data.portfolioId
      authenticated.value = true
    } else {
      username.value = null
      persona.value = null
      portfolioId.value = null
      authenticated.value = false
    }
    initialized.value = true
  }

  /**
   * logout — clears server session and local state.
   */
  async function logout(): Promise<void> {
    try {
      await apiLogout()
    } catch {
      // best-effort
    }
    username.value = null
    persona.value = null
    portfolioId.value = null
    authenticated.value = false
  }

  return {
    username,
    persona,
    portfolioId,
    authenticated,
    initialized,
    isAuthenticated,
    loginAs,
    refresh,
    logout
  }
})
