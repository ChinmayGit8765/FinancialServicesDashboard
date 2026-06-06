package com.quantlens.security;

import com.quantlens.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Spring Security form login authentication (AUTH-01, AUTH-02).
 * <p>
 * RED scaffold — turns green in Plan 03 when SecurityConfig, UserDetailsService,
 * and /api/auth/* endpoints are implemented.
 * <p>
 * Tests:
 * <ul>
 *   <li>loginSuccess: POST /api/auth/login with valid credentials returns 200 JSON
 *       {"authenticated":true,...}</li>
 *   <li>loginFailure: POST /api/auth/login with wrong password returns 401 JSON</li>
 *   <li>sessionPersists: session cookie from login lets GET /api/auth/me return 200</li>
 * </ul>
 */
@Disabled("RED — turns green in Plan 03 when SecurityConfig and auth endpoints are implemented")
class AuthIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void loginSuccess() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("username", "alice");
        body.add("password", "demo1234");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"authenticated\":true");
    }

    @Test
    void loginFailure() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("username", "alice");
        body.add("password", "wrongpassword");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("\"authenticated\":false");
    }

    @Test
    void sessionPersists() {
        // Step 1: Login to obtain session cookie
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> loginBody = new LinkedMultiValueMap<>();
        loginBody.add("username", "alice");
        loginBody.add("password", "demo1234");

        ResponseEntity<String> loginResponse = restTemplate.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(loginBody, loginHeaders),
                String.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Extract JSESSIONID cookie
        String setCookie = loginResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).as("Login response should set a session cookie").isNotNull();

        // Step 2: Use session cookie to call /api/auth/me
        HttpHeaders meHeaders = new HttpHeaders();
        meHeaders.add(HttpHeaders.COOKIE, setCookie);

        ResponseEntity<String> meResponse = restTemplate.exchange(
                "/api/auth/me",
                HttpMethod.GET,
                new HttpEntity<>(meHeaders),
                String.class);

        assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
