package com.quantlens.ai.api;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Global exception handler for AI controller layer — defense-in-depth backstop.
 *
 * <h3>Motivation (CR-03 / WR-01 / CR-05)</h3>
 * <ul>
 *   <li><strong>CR-03:</strong> Any exception that bypasses the service-level try/catch
 *       (e.g. a {@code RuntimeException} during session-proxy resolution, or before the
 *       {@code try} block in {@code ExplainPositionService} / {@code CommentaryService})
 *       would previously flow through Spring Boot's default
 *       {@code BasicErrorController}. Depending on the {@code server.error.include-message}
 *       setting, this could expose provider URLs, key prefixes, or Anthropic/OpenAI
 *       HTTP 401 bodies to the caller. This handler ensures no such detail escapes.</li>
 *   <li><strong>WR-01:</strong> {@code ChatClientStrategy.buildModel} throws
 *       {@code IllegalArgumentException("Unknown provider: " + keyHolder.getProvider())}
 *       for an unrecognised provider. That exception is only caught by
 *       {@code AiKeyController.handleIllegalArgument}, which is scoped to
 *       {@code AiKeyController}. Calls from {@code AiController} (explain / commentary)
 *       would use Spring Boot's default handler. This advice catches it globally and
 *       returns a generic 400 with no exception detail.</li>
 *   <li><strong>CR-05:</strong> {@code @Validated} + {@code @Pattern} on a
 *       {@code @PathVariable} throws {@code ConstraintViolationException} (not
 *       {@code MethodArgumentNotValidException}). This handler catches it and returns
 *       a generic 400 so malformed ticker symbols are cleanly rejected.</li>
 * </ul>
 *
 * <h3>What is NOT handled here</h3>
 * {@link ResponseStatusException} is explicitly re-thrown so that existing 400/401/404/502
 * semantics from service and controller code are preserved. The generic fallback only fires
 * for truly unhandled exceptions.
 *
 * <h3>Key-safety invariant</h3>
 * The exception message is NEVER included in the response body. The full exception is
 * logged internally (with stack trace) for diagnostics, but the client receives only
 * {@code {"error":"Internal server error"}}, {@code {"error":"Invalid request"}}, or
 * {@code {"error":"Invalid configuration"}}.
 */
@RestControllerAdvice(basePackages = "com.quantlens.ai.api")
public class GlobalAiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalAiExceptionHandler.class);

    /**
     * Handles {@link ConstraintViolationException} from {@code @Validated} path variable checks.
     *
     * <p>When {@code @Validated} is applied to a class and a {@code @Pattern} (or other constraint)
     * on a {@code @PathVariable} is violated, Spring throws {@code ConstraintViolationException}
     * (not {@code MethodArgumentNotValidException}). This handler returns a generic 400 with no
     * constraint detail forwarded to the client (CR-05).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<String> handleConstraintViolation(ConstraintViolationException ex) {
        // Log at debug level — constraint violations on path variables are expected for bad input
        log.debug("ConstraintViolationException on path variable (not forwarded to client): {}", ex.getMessage());
        return ResponseEntity.badRequest().body("{\"error\":\"Invalid request\"}");
    }

    /**
     * Handles {@link IllegalArgumentException} globally for all AI controllers.
     *
     * <p>Catches the {@code "Unknown provider: ..."} exception thrown by
     * {@code ChatClientStrategy.buildModel} when invoked from {@code AiController}
     * (not just from {@code AiKeyController} where the controller-scoped handler would catch it).
     * Returns a generic 400 — exception message (which contains attacker-influenced provider
     * string) is never forwarded (WR-01 / Pitfall 5).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
        // Log internally but never forward exception detail (WR-01)
        log.warn("IllegalArgumentException in AI controller layer (not forwarded to client)", ex);
        return ResponseEntity.badRequest().body("{\"error\":\"Invalid configuration\"}");
    }

    /**
     * Backstop for all unhandled exceptions — prevents provider detail from leaking.
     *
     * <p>Re-throws {@link ResponseStatusException} so that existing HTTP semantics (404,
     * 401, 502) are preserved. Any other exception is logged and returned as a generic 500.
     * Exception message and stack trace are NEVER included in the response body (CR-03).
     *
     * @param ex any unhandled exception reaching the controller layer
     * @return 500 with a generic error body
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleAll(Exception ex) {
        if (ex instanceof ResponseStatusException) {
            // Let ResponseStatusException propagate — it carries deliberate HTTP semantics
            // (404 NOT_FOUND, 502 BAD_GATEWAY, etc.) set by the service layer.
            throw (ResponseStatusException) ex;
        }
        // Log internally with full stack trace — NEVER forward to client (CR-03, T-06-09)
        log.error("Unhandled exception in AI controller layer (not forwarded to client)", ex);
        return ResponseEntity.internalServerError()
                             .body("{\"error\":\"Internal server error\"}");
    }
}
