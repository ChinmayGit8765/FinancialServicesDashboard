package com.quantlens.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 metadata for the QuantLens API (Phase 10, DOCS-01).
 * <p>
 * springdoc auto-discovers every {@code @RestController} bean (portfolio, analytics, ai, auth)
 * and renders the spec at {@code /v3/api-docs} with Swagger UI at {@code /swagger-ui.html}.
 * The product MCP server ({@code PortfolioMcpTools}, {@code @McpTool} on a {@code @Component} —
 * NOT a {@code @RestController}) is invisible to springdoc, so {@code /mcp} is excluded
 * automatically with no {@code @Hidden} annotation needed.
 * <p>
 * This bean only supplies metadata + documents the two real auth surfaces; it never exposes
 * any key material (the BYO LLM key is session-only and never modelled as a schema field).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI quantLensOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("QuantLens API")
                        .description("AI-augmented portfolio & market intelligence dashboard")
                        .version("1.0.0"))
                .components(new Components()
                        // Browser/SPA: session cookie set by POST /api/auth/login (form login).
                        .addSecuritySchemes("session-cookie", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")
                                .description("Session cookie issued by POST /api/auth/login"))
                        // Machine client (MCP /mcp): HTTP Basic.
                        .addSecuritySchemes("http-basic-mcp", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("HTTP Basic auth for the /mcp Streamable-HTTP MCP server")))
                // Default: the browser/SPA session cookie.
                .addSecurityItem(new SecurityRequirement().addList("session-cookie"));
    }
}
