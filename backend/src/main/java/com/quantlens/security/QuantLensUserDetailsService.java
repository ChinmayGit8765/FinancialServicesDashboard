package com.quantlens.security;

import com.quantlens.portfolio.domain.AppUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * JPA-backed {@link UserDetailsService} that loads demo users from the {@code app_users} table.
 * <p>
 * Reads via {@link AppUserRepository#findByUsername(String)} — the same repository used by
 * the seeder.  Maps the stored BCrypt {@code password_hash} directly to Spring Security's
 * {@code UserDetails}; BCrypt verification is handled by Spring Security's
 * {@link org.springframework.security.authentication.dao.DaoAuthenticationProvider} which
 * uses the single canonical {@link org.springframework.security.crypto.password.PasswordEncoder}
 * bean declared in {@code PasswordEncoderConfig} (Plan 02).
 * <p>
 * All authenticated users receive {@code ROLE_USER}.
 */
@Service
public class QuantLensUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public QuantLensUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return appUserRepository.findByUsername(username)
                .map(user -> User.withUsername(user.getUsername())
                        .password(user.getPasswordHash())
                        .roles("USER")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}
