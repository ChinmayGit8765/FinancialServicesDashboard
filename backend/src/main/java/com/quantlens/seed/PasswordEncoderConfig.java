package com.quantlens.seed;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Declares the single canonical {@link PasswordEncoder} bean for the whole application.
 * <p>
 * This is the ONLY place in the codebase that creates a {@code BCryptPasswordEncoder}.
 * Plan 03's {@code SecurityConfig} MUST autowire this bean — it must NOT redefine
 * {@code @Bean PasswordEncoder} independently, which would cause an ambiguous-bean error
 * or a strength-mismatch between seeded hashes and login verification.
 * <p>
 * The seeder ({@code SeedRunner}) also constructor-injects this bean so that all BCrypt
 * hashes written to the database use the same strength parameter as login verification.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
