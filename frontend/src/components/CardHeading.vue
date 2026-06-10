<script setup lang="ts">
/**
 * CardHeading — consistent panel title + one-line "what this shows / how to read it" description.
 * Dropped at the top of every dashboard card so each panel is self-explanatory.
 */
defineProps<{
  title: string
  subtitle?: string
}>()
</script>

<template>
  <header class="mb-4 flex flex-col gap-0.5">
    <h2 class="text-sm font-semibold tracking-tight text-ink">{{ title }}</h2>
    <p v-if="subtitle" class="text-xs leading-snug text-ink-muted">{{ subtitle }}</p>
  </header>
</template>
