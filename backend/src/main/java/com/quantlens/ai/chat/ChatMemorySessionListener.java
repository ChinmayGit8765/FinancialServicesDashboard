package com.quantlens.ai.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.event.EventListener;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Component;

/**
 * Evicts the {@link ChatMemory} conversation entry when an HTTP session expires or is
 * invalidated.
 *
 * <h2>WR-02: Unbounded MessageWindowChatMemory growth</h2>
 * {@link MessageWindowChatMemory} is a singleton bean backed by a
 * {@code ConcurrentHashMap<String, List<Message>>}. The {@code maxMessages(20)} window
 * limits per-call context injection but does NOT evict the map entry when the HTTP session
 * expires — the entry grows forever. This listener removes the entry on session expiry,
 * preventing unbounded heap growth in long-running or high-traffic deployments.
 *
 * <h2>Security note (CR-04 complement)</h2>
 * With CR-04 fixing the IDOR by always using {@code session.getId()} as the conversationId,
 * this listener is the natural cleanup counterpart: when the session is destroyed, its
 * conversation history is also removed. This prevents stale memory from persisting in the
 * heap after the session's authentication context is gone.
 */
@Component
public class ChatMemorySessionListener {

    private static final Logger log = LoggerFactory.getLogger(ChatMemorySessionListener.class);

    private final ChatMemory chatMemory;

    public ChatMemorySessionListener(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    /**
     * Clears the conversation history for the destroyed session's ID.
     *
     * <p>Called by Spring's event system when {@code HttpSession.invalidate()} is invoked
     * (e.g., on logout, session timeout, or explicit invalidation). The event is
     * {@link HttpSessionDestroyedEvent} from {@code spring-security-web}.
     *
     * @param event the session-destroyed event carrying the expired/invalidated session
     */
    @EventListener
    public void onSessionDestroyed(HttpSessionDestroyedEvent event) {
        String sessionId = event.getSession().getId();
        try {
            chatMemory.clear(sessionId);
            log.debug("ChatMemorySessionListener: cleared conversation memory for expired session {}", sessionId);
        } catch (Exception e) {
            // Non-fatal: if clear() fails (e.g., memory already evicted), log and continue
            log.warn("ChatMemorySessionListener: failed to clear conversation memory for session {}", sessionId, e);
        }
    }
}
