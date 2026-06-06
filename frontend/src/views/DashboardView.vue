<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

async function handleLogout() {
  await authStore.logout()
  await router.push('/login')
}
</script>

<template>
  <div class="dashboard-page">
    <header class="dashboard-header">
      <div class="brand">
        <h1>QuantLens</h1>
        <span class="phase-badge">Phase 1 — Walking Skeleton</span>
      </div>
      <div class="user-info">
        <span class="persona-tag">{{ authStore.persona }}</span>
        <span class="username">@{{ authStore.username }}</span>
        <button class="logout-btn" @click="handleLogout">Log out</button>
      </div>
    </header>

    <main class="dashboard-main">
      <div class="welcome-card">
        <h2>Welcome, {{ authStore.persona }}!</h2>
        <p>You are logged in as <strong>{{ authStore.username }}</strong>.</p>

        <div class="info-grid">
          <div class="info-item">
            <span class="info-label">Username</span>
            <span class="info-value">{{ authStore.username }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">Persona</span>
            <span class="info-value">{{ authStore.persona }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">Portfolio ID</span>
            <span class="info-value portfolio-id">{{ authStore.portfolioId }}</span>
          </div>
        </div>

        <div class="placeholder-notice">
          <p>
            Dashboard charts and portfolio analytics are coming in Phase 3.
            This placeholder confirms the full stack is wired end-to-end:
            Vue SPA → Spring Security session → Postgres seeded data.
          </p>
          <p class="tech-note">
            Portfolio ID <strong>{{ authStore.portfolioId }}</strong> was read live from the
            seeded Postgres database — the session cookie and CSRF token are working correctly.
            Refresh the page to confirm the session persists.
          </p>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.dashboard-page {
  min-height: 100vh;
  background: #0f172a;
  color: #e2e8f0;
  display: flex;
  flex-direction: column;
}

.dashboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 2rem;
  background: #1e293b;
  border-bottom: 1px solid #334155;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.brand {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.brand h1 {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 700;
  color: #38bdf8;
}

.phase-badge {
  font-size: 0.7rem;
  padding: 0.2em 0.6em;
  background: #1e3a5f;
  border: 1px solid #0ea5e9;
  border-radius: 9999px;
  color: #7dd3fc;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.persona-tag {
  font-size: 0.8rem;
  padding: 0.2em 0.6em;
  background: #0f172a;
  border-radius: 4px;
  color: #94a3b8;
}

.username {
  color: #94a3b8;
  font-size: 0.9rem;
}

.logout-btn {
  padding: 0.4rem 1rem;
  background: transparent;
  border: 1px solid #334155;
  border-radius: 6px;
  color: #94a3b8;
  cursor: pointer;
  font-size: 0.85rem;
  transition: border-color 0.15s, color 0.15s;
}

.logout-btn:hover {
  border-color: #f87171;
  color: #f87171;
}

.dashboard-main {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
}

.welcome-card {
  background: #1e293b;
  border-radius: 12px;
  padding: 2.5rem 2rem;
  max-width: 600px;
  width: 100%;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.4);
}

.welcome-card h2 {
  margin: 0 0 0.5rem;
  font-size: 1.6rem;
  color: #f1f5f9;
}

.welcome-card > p {
  color: #94a3b8;
  margin-bottom: 1.5rem;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.info-item {
  background: #0f172a;
  border: 1px solid #334155;
  border-radius: 8px;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.info-label {
  font-size: 0.7rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #64748b;
}

.info-value {
  font-size: 1.1rem;
  font-weight: 600;
  color: #e2e8f0;
}

.portfolio-id {
  color: #38bdf8;
  font-size: 1.4rem;
  font-variant-numeric: tabular-nums;
}

.placeholder-notice {
  border-top: 1px solid #334155;
  padding-top: 1.25rem;
  font-size: 0.85rem;
  color: #64748b;
  line-height: 1.6;
}

.placeholder-notice p + p {
  margin-top: 0.5rem;
}

.tech-note {
  color: #475569;
  font-size: 0.8rem;
}

.tech-note strong {
  color: #38bdf8;
}
</style>
