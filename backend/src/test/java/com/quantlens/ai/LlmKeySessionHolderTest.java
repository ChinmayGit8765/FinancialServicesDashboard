package com.quantlens.ai;

import com.quantlens.ai.session.LlmKeySessionHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LlmKeySessionHolder}.
 *
 * <p>No Spring context — pure POJO test. Verifies the key/demo mode state machine.
 * These tests PASS now (holder is fully implemented in Task 1).
 *
 * <p>T-06-01 verification: apiKey is never exposed via @ToString or serialized (verified
 * structurally — no @ToString annotation; no @JsonInclude; no @JsonSerialize on the class).
 */
class LlmKeySessionHolderTest {

    private LlmKeySessionHolder holder;

    @BeforeEach
    void setUp() {
        holder = new LlmKeySessionHolder();
    }

    @Test
    void freshInstance_startsInDemoMode() {
        assertThat(holder.hasKey()).isFalse();
        assertThat(holder.getProvider()).isNull();
        assertThat(holder.getApiKey()).isNull();
    }

    @Test
    void setKey_switchesToLiveMode() {
        holder.setKey("anthropic", "sk-ant-test-key-123");

        assertThat(holder.hasKey()).isTrue();
        assertThat(holder.getProvider()).isEqualTo("anthropic");
        assertThat(holder.getApiKey()).isEqualTo("sk-ant-test-key-123");
    }

    @Test
    void setKey_openai_storesCorrectProvider() {
        holder.setKey("openai", "sk-openai-test-key-456");

        assertThat(holder.hasKey()).isTrue();
        assertThat(holder.getProvider()).isEqualTo("openai");
        assertThat(holder.getApiKey()).isEqualTo("sk-openai-test-key-456");
    }

    @Test
    void clear_resetsToDemo_afterKeyWasSet() {
        holder.setKey("anthropic", "sk-ant-test-key-123");
        assertThat(holder.hasKey()).isTrue();

        holder.clear();

        assertThat(holder.hasKey()).isFalse();
        assertThat(holder.getProvider()).isNull();
        assertThat(holder.getApiKey()).isNull();
    }

    @Test
    void hasKey_returnsFalse_forBlankKey() {
        holder.setKey("anthropic", "  ");

        assertThat(holder.hasKey()).isFalse();
    }

    @Test
    void hasKey_returnsFalse_forNullKey() {
        holder.setKey("anthropic", null);

        assertThat(holder.hasKey()).isFalse();
    }

    @Test
    void noToString_keyNotExposedByDefault() {
        // Verify the class has no @ToString annotation that could leak the key in logs.
        // Structural check: the default Object.toString() should not contain the key value.
        holder.setKey("anthropic", "SUPER-SECRET-KEY");

        String toStringOutput = holder.toString();

        // Default Object.toString() outputs ClassName@hashCode — does NOT include field values.
        // If a @ToString annotation or override were added, this assertion would catch it.
        assertThat(toStringOutput).doesNotContain("SUPER-SECRET-KEY");
    }
}
