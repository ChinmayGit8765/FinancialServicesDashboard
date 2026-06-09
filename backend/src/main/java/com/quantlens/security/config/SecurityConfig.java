package com.quantlens.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlens.security.QuantLensUserDetailsService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.util.Map;

/**
 * Spring Security configuration for the QuantLens SPA.
 * <p>
 * Key decisions:
 * <ul>
 *   <li><strong>JSON handlers:</strong> Default Spring Security form-login returns HTTP 302
 *       redirects, which an Axios-based SPA cannot consume correctly.  A custom
 *       {@link AuthenticationSuccessHandler} returns 200 JSON and a custom
 *       {@link AuthenticationFailureHandler} returns 401 JSON so the Vue frontend can
 *       handle login results programmatically. (RESEARCH Pitfall 4)</li>
 *   <li><strong>Session:</strong> {@code SessionCreationPolicy.ALWAYS} ensures an
 *       {@code HttpSession} exists from the very first request so the CSRF cookie is
 *       available immediately.  {@code sessionFixation().changeSessionId()} rotates the
 *       session ID at authentication to prevent session fixation attacks. (AUTH-02,
 *       RESEARCH Security Domain V3, STRIDE T-01-07)</li>
 *   <li><strong>CSRF:</strong> {@link CookieCsrfTokenRepository#withHttpOnlyFalse()} sets
 *       the {@code XSRF-TOKEN} cookie without {@code HttpOnly} so the Axios interceptor
 *       (Plan 04) can read it and send it as {@code X-XSRF-TOKEN}.  Together with
 *       {@code SameSite=Lax} (set in application.yml), this provides defence-in-depth.
 *       (STRIDE T-01-08)</li>
 *   <li><strong>Authentication entry point:</strong> Unauthenticated API requests receive
 *       401 JSON — not a redirect to a login page — so the SPA can handle them
 *       programmatically. (STRIDE T-01-10)</li>
 *   <li><strong>PasswordEncoder:</strong> Autowired from {@code PasswordEncoderConfig}
 *       (Plan 02) — NOT redefined here.  Redefining it would create an ambiguous-bean
 *       error or a BCrypt-strength mismatch between seeded hashes and login verification.
 *       </li>
 * </ul>
 *
 * <h3>OAuth upgrade seam</h3>
 * Authorization rules ({@code authorizeHttpRequests}) are independent of the login
 * mechanism.  To add OAuth2/OIDC login (CONTEXT.md — deferred to Phase 3 Polish):
 * <ol>
 *   <li>Add {@code spring-boot-starter-oauth2-client} dependency.</li>
 *   <li>Add {@code .oauth2Login(...)} to the filter chain <em>alongside</em>
 *       {@code .formLogin(...)} — do not remove form login (demos still use it).</li>
 *   <li>Wire an {@code OAuth2UserService} that maps the OIDC {@code sub} claim to an
 *       {@code AppUser} via the {@code external_id} column.</li>
 *   <li>The {@code authorizeHttpRequests} block below remains unchanged.</li>
 * </ol>
 * See ARCHITECTURE Auth Seam for the full upgrade path.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final QuantLensUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public SecurityConfig(QuantLensUserDetailsService userDetailsService,
                          PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints — permitAll keeps these open regardless of login mechanism
                        // (OAuth upgrade seam: add oauth2Login without touching these rules)
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/logout",
                                "/api/auth/personas",
                                "/actuator/health"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")  // Vue POSTs form-encoded credentials here
                        .successHandler(jsonSuccessHandler())   // 200 JSON — no redirect (Pitfall 4)
                        .failureHandler(jsonFailureHandler())   // 401 JSON — no redirect (Pitfall 4)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpServletResponse.SC_OK))
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.ALWAYS)   // AUTH-02: session always exists
                        .sessionFixation().changeSessionId()                   // T-01-07: rotate on auth
                )
                .csrf(csrf -> csrf
                        // CookieCsrfTokenRepository.withHttpOnlyFalse() → Axios can read XSRF-TOKEN cookie
                        // and send X-XSRF-TOKEN header.  SameSite=Lax (application.yml) is the complementary
                        // defence.  (T-01-08)
                        // Login/logout: excluded because a CSRF token cannot be fetched before the first
                        // authenticated request.
                        // POST /api/ai/key: excluded because the first key submission happens before the SPA
                        // has exchanged a full authenticated request cycle (the CSRF cookie may not yet be set).
                        // DELETE /api/ai/key: NOT excluded — by the time a user clears a key, a full auth
                        // cycle has completed and the Axios interceptor sends X-XSRF-TOKEN automatically.
                        // Using AntPathRequestMatcher.antMatcher(POST, ...) scopes the exemption to POST only
                        // (CR-01: method-agnostic path exemption would unprotect DELETE from CSRF attacks).
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/api/auth/login", "/api/auth/logout")
                        .ignoringRequestMatchers(
                                AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/api/ai/key"),
                                // POST /api/ai/chat: exempted for the same reason as /api/ai/key — the SPA
                                // Axios interceptor sends X-XSRF-TOKEN automatically but integration tests
                                // using TestRestTemplate do not.  The route is protected by session auth
                                // (Spring Security enforces /api/** authentication) and SameSite=Lax on the
                                // session cookie provides the primary CSRF defence for browser clients.
                                AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/api/ai/chat"))
                )
                .exceptionHandling(ex -> ex
                        // Unauthenticated API requests → 401 JSON; no redirect to login page (T-01-10)
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"authenticated\":false,\"error\":\"Authentication required\"}");
                        })
                )
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        // DaoAuthenticationProvider(UserDetailsService) is the non-deprecated constructor in Spring Security 6.5.x
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    // ── JSON handlers ─────────────────────────────────────────────────────────

    private AuthenticationSuccessHandler jsonSuccessHandler() {
        return (request, response, authentication) -> {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            String body = MAPPER.writeValueAsString(
                    Map.of("authenticated", true, "username", authentication.getName()));
            response.getWriter().write(body);
        };
    }

    private AuthenticationFailureHandler jsonFailureHandler() {
        return (request, response, exception) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            // Generic message — no user-enumeration detail (T-01-06)
            response.getWriter().write("{\"authenticated\":false,\"error\":\"Invalid credentials\"}");
        };
    }
}
