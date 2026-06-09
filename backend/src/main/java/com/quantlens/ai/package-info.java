/**
 * QuantLens AI module — Phase 6 demo-mode AI seam.
 *
 * <p>This module provides the full AI interaction layer:
 * <ul>
 *   <li>Demo mode: {@link com.quantlens.ai.chat.DemoModeAdvisor} short-circuits the advisor
 *       chain and returns authored seed content from the DB when no real key is present.</li>
 *   <li>Live mode: {@link com.quantlens.ai.chat.ChatClientStrategy} builds a per-request
 *       ChatClient with the session key injected via the provider Builder pattern.</li>
 *   <li>Key management: {@link com.quantlens.ai.session.LlmKeySessionHolder} holds the
 *       provider + API key in a {@code @SessionScope} bean — never persisted or logged.</li>
 * </ul>
 *
 * <p><strong>Module boundary:</strong> {@code portfolio::domain} (holdings for explain-position
 * prompts) and {@code marketdata::domain} (the {@code Security} reached via {@code Position.getSecurity()}
 * for ticker/sector context) are allowed cross-module dependencies, plus the {@code seed} module.
 * Spring AI provider beans (AnthropicChatModel, OpenAiChatModel) are direct classpath
 * dependencies resolved by the spring-ai-bom, not cross-module Modulith references.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "AI",
        allowedDependencies = {"portfolio::domain", "marketdata::domain", "seed"})
package com.quantlens.ai;
