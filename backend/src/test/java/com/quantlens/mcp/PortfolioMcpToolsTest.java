package com.quantlens.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlens.AbstractPostgresIntegrationTest;
import com.quantlens.analytics.service.RiskCalculator;
import com.quantlens.mcp.tools.PortfolioMcpTools;
import com.quantlens.mcp.tools.PositionDetailResult;
import com.quantlens.mcp.tools.PortfolioSummaryResult;
import com.quantlens.mcp.tools.RiskMetricsResult;
import com.quantlens.portfolio.api.PortfolioPnlDto;
import com.quantlens.portfolio.domain.AppUser;
import com.quantlens.portfolio.domain.AppUserRepository;
import com.quantlens.portfolio.domain.Portfolio;
import com.quantlens.portfolio.domain.PortfolioRepository;
import com.quantlens.portfolio.service.PortfolioService;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tool-correctness + error-hygiene tests for {@link PortfolioMcpTools}.
 * <p>
 * Tools are invoked DIRECTLY as Spring beans (no MCP transport) with a mocked
 * {@link SecurityContextHolder} authentication for {@code alice}. Golden values are
 * reused verbatim from {@code RiskCalculatorTest} (seed=42, alice Growth Portfolio) —
 * proving the @McpTool layer delegates to {@link RiskCalculator}/{@link PortfolioService}
 * without recomputation (no duplicate math).
 * <p>
 * Error hygiene: a tool variant whose delegate throws must return an MCP error result
 * carrying a STATIC safe message — never a stack trace, class name, or the secret payload.
 */
class PortfolioMcpToolsTest extends AbstractPostgresIntegrationTest {

    // Golden constants — verbatim from RiskCalculatorTest (seed=42, alice Growth Portfolio)
    private static final double GOLDEN_SHARPE          = 0.36442669;
    private static final double GOLDEN_ANNUAL_VOL      = 0.34621361;
    private static final double GOLDEN_MAX_DRAWDOWN    = -0.33891522;
    private static final double GOLDEN_BETA            = 1.94917921;
    private static final double GOLDEN_HIST_VAR_AMOUNT = 1464.52;

    @Autowired
    private PortfolioMcpTools tools;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private RiskCalculator riskCalculator;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Long alicePortfolioId;

    @BeforeEach
    @Transactional
    void authenticateAsAlice() {
        AppUser alice = appUserRepository.findByUsername("alice")
                .orElseThrow(() -> new IllegalStateException("alice not found in seed data"));
        List<Portfolio> portfolios = portfolioRepository.findByUserId(alice.getId());
        assertThat(portfolios).as("alice must have a portfolio").isNotEmpty();
        alicePortfolioId = portfolios.get(0).getId();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null, List.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getPortfolioSummary_returnsCorrectTotalValue() throws Exception {
        McpSchema.CallToolResult result = tools.getPortfolioSummary();
        assertThat(result.isError()).as("summary must not be an error for alice").isFalse();

        PortfolioSummaryResult summary =
                objectMapper.readValue(extractText(result), PortfolioSummaryResult.class);
        PortfolioPnlDto expected = portfolioService.getPortfolioPnl(alicePortfolioId);

        assertThat(summary.totalMarketValue())
                .as("total market value must equal PortfolioService (no recompute)")
                .isEqualByComparingTo(expected.totalMarketValue());
        assertThat(summary.totalUnrealizedGainAbs())
                .isEqualByComparingTo(expected.totalUnrealizedGainAbs());
        assertThat(summary.allocation()).as("allocation list present").isNotEmpty();
    }

    @Test
    void getRiskMetrics_matchesGoldenValues() throws Exception {
        McpSchema.CallToolResult result = tools.getRiskMetrics();
        assertThat(result.isError()).as("risk metrics must not be an error for alice").isFalse();

        RiskMetricsResult metrics =
                objectMapper.readValue(extractText(result), RiskMetricsResult.class);

        assertThat(metrics.sharpeRatio()).isCloseTo(GOLDEN_SHARPE, within(0.001));
        assertThat(metrics.annualizedVolatility()).isCloseTo(GOLDEN_ANNUAL_VOL, within(0.0001));
        assertThat(metrics.maxDrawdown()).isCloseTo(GOLDEN_MAX_DRAWDOWN, within(0.0001));
        assertThat(metrics.beta()).isCloseTo(GOLDEN_BETA, within(0.001));
        assertThat(metrics.historicalVar95().doubleValue())
                .as("historical VaR sourced from VarResultDto method==HISTORICAL")
                .isCloseTo(GOLDEN_HIST_VAR_AMOUNT, within(0.01));
    }

    @Test
    void getPositionDetail_aapl_returnsCorrectHolding() throws Exception {
        McpSchema.CallToolResult result = tools.getPositionDetail("AAPL");
        assertThat(result.isError()).as("AAPL is a seeded holding for alice").isFalse();

        PositionDetailResult detail =
                objectMapper.readValue(extractText(result), PositionDetailResult.class);
        assertThat(detail.ticker()).isEqualTo("AAPL");
        assertThat(detail.name()).isNotBlank();
        assertThat(detail.quantity()).isNotNull();
        assertThat(detail.currentMarketValue()).isNotNull();
    }

    @Test
    void getPositionDetail_lowercaseTicker_isSanitizedAndResolves() throws Exception {
        McpSchema.CallToolResult result = tools.getPositionDetail("aapl");
        assertThat(result.isError()).as("ticker must be uppercased before lookup").isFalse();
        PositionDetailResult detail =
                objectMapper.readValue(extractText(result), PositionDetailResult.class);
        assertThat(detail.ticker()).isEqualTo("AAPL");
    }

    @Test
    void toolException_doesNotLeakStackTrace() {
        // A tool variant whose delegate throws — proves the try/catch returns a static safe message.
        String secret = "INTERNAL_DB_ERROR_secret_xyz";
        PortfolioService throwingService = mock(PortfolioService.class);
        when(throwingService.getPortfolioPnl(org.mockito.ArgumentMatchers.anyLong()))
                .thenThrow(new RuntimeException(secret));

        PortfolioMcpTools failingTools =
                new PortfolioMcpTools(throwingService, riskCalculator, portfolioRepository, objectMapper);

        McpSchema.CallToolResult result = failingTools.getPortfolioSummary();
        assertThat(result.isError()).as("forced delegate failure must surface as an MCP error").isTrue();

        String text = extractText(result);
        assertThat(text)
                .as("error content must NOT leak the secret, exception class, or package path")
                .doesNotContain(secret)
                .doesNotContain("RuntimeException")
                .doesNotContain("com.quantlens")
                .doesNotContain("java.lang");
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private String extractText(McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(c -> c instanceof McpSchema.TextContent)
                .map(c -> ((McpSchema.TextContent) c).text())
                .findFirst()
                .orElse("");
    }
}
