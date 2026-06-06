import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import DashboardView from '../views/DashboardView.vue'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView
    },
    {
      path: '/',
      name: 'dashboard',
      component: DashboardView,
      meta: { requiresAuth: true }
    }
  ]
})

// Navigation guard — check session on first load, redirect to /login if unauthenticated
router.beforeEach(async (to) => {
  if (!to.meta.requiresAuth) return true

  const authStore = useAuthStore()

  // On first navigation, attempt to restore the session from the server
  if (!authStore.initialized) {
    await authStore.refresh()
  }

  if (!authStore.isAuthenticated) {
    return { name: 'login' }
  }

  return true
})

export default router
