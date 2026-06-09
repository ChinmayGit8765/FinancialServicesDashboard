<script setup lang="ts">
/**
 * ChatPanel — AI Q&A chat panel for the dashboard.
 *
 * Three states: shimmer skeleton (loading), error alert (error),
 * populated (message list with citation chips) + empty state.
 *
 * Props-driven (no direct store access) — DashboardView wires aiStore.*
 * Analog: CommentaryCard.vue (three-state, skeleton shimmer, dark-theme CSS vars)
 *
 * Security (T-07-XSS): answer/citation text rendered via {{ }} only — never v-html.
 */
import type { ChatMessage } from '../../api/ai'
import { ref, nextTick, watch } from 'vue'

const props = defineProps<{
  messages: ChatMessage[]
  loading: boolean
  error: string | null
}>()

const emit = defineEmits<{
  send: [message: string]
}>()

const inputValue = ref('')
const messageList = ref<HTMLElement | null>(null)

// Scroll to bottom whenever a new message is added
watch(() => props.messages.length, async () => {
  await nextTick()
  if (messageList.value) {
    messageList.value.scrollTop = messageList.value.scrollHeight
  }
})

function submitMessage() {
  if (!inputValue.value.trim() || props.loading) return
  emit('send', inputValue.value.trim())
  inputValue.value = ''
}
</script>

<template>
  <div class="chat-panel">

    <!-- Header -->
    <div class="chat-header">
      <span class="chat-label">AI Q&amp;A</span>
    </div>

    <!-- Message list -->
    <div class="message-list" ref="messageList">

      <!-- Empty state -->
      <div v-if="props.messages.length === 0 && !props.loading" class="chat-empty">
        Ask a question about your portfolio or the 10-K filings…
      </div>

      <!-- Message turns -->
      <div
        v-for="(msg, i) in props.messages"
        :key="i"
        :class="['message', msg.role === 'user' ? 'message--user' : 'message--assistant']"
      >
        <p class="message-content">{{ msg.content }}</p>
        <!-- Citation chips — T-07-XSS: text interpolation only, never v-html -->
        <div v-if="msg.citations && msg.citations.length > 0" class="citation-list">
          <span
            v-for="(c, ci) in msg.citations"
            :key="ci"
            class="citation-chip"
            :title="c.excerpt"
          >{{ c.ticker }} · {{ c.section }}</span>
        </div>
      </div>

      <!-- Loading shimmer (verbatim from CommentaryCard.vue skeleton pattern) -->
      <template v-if="props.loading">
        <div class="skeleton" style="height: 14px; width: 75%; margin-bottom: 8px" aria-hidden="true" />
        <div class="skeleton" style="height: 14px; width: 55%;" aria-hidden="true" />
      </template>

    </div>

    <!-- Error (T-07-SESSION: 401 shown as 'Session expired') -->
    <div v-if="props.error" class="chat-error" role="alert">
      <span>{{ props.error }}</span>
    </div>

    <!-- Input row -->
    <div class="chat-input-row">
      <input
        v-model="inputValue"
        class="chat-input"
        placeholder="Ask about your portfolio or 10-K filings…"
        :disabled="props.loading"
        @keydown.enter.prevent="submitMessage"
        aria-label="Chat input"
      />
      <button
        class="send-btn"
        :disabled="props.loading || !inputValue.trim()"
        @click="submitMessage"
      >Send</button>
    </div>

  </div>
</template>

<style scoped>
/* Card wrapper — mirrors CommentaryCard.vue .commentary-card */
.chat-panel {
  background: var(--color-bg-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  border: 1px solid var(--color-border);
  padding: var(--space-lg);
  min-height: 240px;
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

/* Header label — mirrors .commentary-label */
.chat-header {
  margin-bottom: var(--space-xs);
}

.chat-label {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-accent);
}

/* Scrollable message list */
.message-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  max-height: 320px;
}

/* Empty state */
.chat-empty {
  font-size: 14px;
  color: var(--color-text-muted);
}

/* Message bubbles */
.message {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

.message--user {
  align-items: flex-end;
}

.message--assistant {
  align-items: flex-start;
}

.message-content {
  font-size: 14px;
  line-height: 1.6;
  margin: 0;
  padding: var(--space-sm) var(--space-md);
  border-radius: var(--radius-md);
  max-width: 90%;
}

.message--user .message-content {
  background: var(--color-accent);
  color: var(--color-bg-base);
}

.message--assistant .message-content {
  background: var(--color-bg-overlay);
  color: var(--color-text-primary);
}

/* Citation chips — accent micro-badge */
.citation-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-xs);
  padding: 0 var(--space-md);
}

.citation-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  font-size: 11px;
  font-weight: 600;
  color: var(--color-accent);
  border: 1px solid var(--color-accent);
  background: var(--color-accent-subtle);
  cursor: default;
}

/* Error — mirrors .commentary-error */
.chat-error {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  color: var(--color-down);
  border: 1px solid var(--color-down);
  border-radius: var(--radius-md);
  padding: var(--space-md);
  font-size: 14px;
}

/* Input row */
.chat-input-row {
  display: flex;
  gap: var(--space-sm);
  align-items: center;
}

.chat-input {
  flex: 1;
  background: var(--color-bg-overlay);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text-primary);
  font-size: 14px;
  padding: var(--space-sm) var(--space-md);
  min-height: 36px;
  outline: none;
}

.chat-input:focus {
  border-color: var(--color-accent);
}

.chat-input:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.chat-input::placeholder {
  color: var(--color-text-muted);
}

.send-btn {
  background: var(--color-accent);
  color: var(--color-bg-base);
  border: none;
  border-radius: var(--radius-md);
  padding: var(--space-sm) var(--space-md);
  font-size: 13px;
  font-weight: 600;
  min-height: 36px;
  cursor: pointer;
  flex-shrink: 0;
}

.send-btn:hover:not(:disabled) {
  opacity: 0.9;
}

.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.send-btn:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 3px;
}

/* Skeleton shimmer — verbatim from CommentaryCard.vue lines 171–188 */
@keyframes shimmer {
  0%   { background-position: -200% center; }
  100% { background-position:  200% center; }
}

.skeleton {
  display: block;
  border-radius: var(--radius-sm);
  background: linear-gradient(
    90deg,
    var(--color-bg-surface) 25%,
    var(--color-bg-overlay) 50%,
    var(--color-bg-surface) 75%
  );
  background-size: 200% auto;
  animation: shimmer 1.4s linear infinite;
}
</style>
