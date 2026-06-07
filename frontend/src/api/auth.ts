import axios from 'axios'
import router from '../router'

// All requests send cookies (JSESSIONID) — required for session auth
axios.defaults.withCredentials = true

// Read CSRF token from the XSRF-TOKEN cookie set by CookieCsrfTokenRepository
function getCsrfToken(): string | null {
  const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : null
}

// Attach X-XSRF-TOKEN header to every mutating request
axios.interceptors.request.use(config => {
  const token = getCsrfToken()
  if (token && ['post', 'put', 'delete', 'patch'].includes(config.method?.toLowerCase() ?? '')) {
    config.headers['X-XSRF-TOKEN'] = token
  }
  return config
})

// Global 401 response interceptor — redirects to /login on session expiry.
// Excludes /auth/me (used silently at startup) and /auth/login (the login endpoint
// itself) to prevent a redirect loop (T-03-04).
axios.interceptors.response.use(
  response => response,
  (error: any) => {
    if (error?.response?.status === 401) {
      const url: string = error.config?.url ?? ''
      if (!url.includes('/auth/me') && !url.includes('/auth/login')) {
        // router may be undefined if there is a circular-import edge case at build
        // time; fall back to window.location in that scenario (T-03-06 / A2)
        if (router) {
          router.push('/login')
        } else {
          window.location.href = '/login'
        }
      }
    }
    return Promise.reject(error)
  }
)

export interface PersonaInfo {
  username: string
  persona: string
  passwordHint: string
}

export interface MeResponse {
  username: string
  persona: string
  portfolioId: number
}

export interface LoginResponse {
  authenticated: boolean
  username?: string
  error?: string
}

/**
 * POST /api/auth/login — form-encoded credentials.
 * CSRF is ignored on this endpoint (ignoringRequestMatchers in SecurityConfig).
 */
export async function login(username: string, password: string): Promise<LoginResponse> {
  const params = new URLSearchParams()
  params.append('username', username)
  params.append('password', password)
  const response = await axios.post<LoginResponse>('/api/auth/login', params, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
  })
  return response.data
}

/**
 * POST /api/auth/logout — invalidates server session.
 */
export async function logout(): Promise<void> {
  await axios.post('/api/auth/logout')
}

/**
 * GET /api/auth/me — returns the authenticated user's persona + portfolioId.
 * Returns null (instead of throwing) on 401 so the auth store can detect unauthenticated state.
 */
export async function me(): Promise<MeResponse | null> {
  try {
    const response = await axios.get<MeResponse>('/api/auth/me')
    return response.data
  } catch {
    return null
  }
}

/**
 * GET /api/auth/personas — public endpoint returning all demo personas.
 */
export async function personas(): Promise<PersonaInfo[]> {
  const response = await axios.get<PersonaInfo[]>('/api/auth/personas')
  return response.data
}
