<script setup lang="ts">
import { provide } from 'vue'
import { THEME_KEY } from 'vue-echarts'
provide(THEME_KEY, 'quantlens-dark')
</script>

<template>
  <RouterView />
</template>

<style>
/* Global resets */
*, *::before, *::after {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, sans-serif;
  background: var(--color-bg-elevated);
  color: var(--color-text-primary);
  line-height: 1.5;
}
</style>
