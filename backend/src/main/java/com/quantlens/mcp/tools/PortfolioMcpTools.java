package com.quantlens.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlens.analytics.api.RiskScorecardDto;
import com.quantlens.analytics.api.VarResultDto;
import com.quantlens.analytics.service.RiskCalculator;
import com.quantlens.portfolio.api.AllocationSliceDto;
import com.quantlens.portfolio.api.HoldingDto;
import com.quantlens.portfolio.api.PortfolioPnlDto;
import com.quantlens.portfolio.domain.PortfolioRepository;
import com.quantlens.portfolio.service.PortfolioService;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Spring AI MCP tool bean exposing portfolio analytics as MCP protocol tools.
 * <p>
 * This class is a thin protocol adapter — all business logic and computation
 * delegate to {@link PortfolioService} and {@link RiskCalculator}. No values
 * are recomputed here.
 * <p>
 * <strong>Principal resolution:</strong> The portfolio identity is derived ONLY
 * from {@link SecurityContextHolder} — never from tool parameters (IDOR prevention T-09-02).
 * The {@code ticker} parameter in {@code get_position_detail} identifies a security, not a user.
 * <p>
 * <strong>Error hygiene:</strong> Every tool body is wrapped in try/catch. Exceptions
 * are logged internally with full stack trace; only a static safe message is returned
 * to the MCP client (T-09-03). Never {@code e.getMessage()} or class names in the response.
 * <p>
 * <strong>Module boundary:</strong> Declared in {@code com.quantlens.mcp} module;
 * accesses {@code portfolio::service}, {@code portfolio::api}, {@code portfolio::domain},
 * {@code analytics::service}, {@code analytics::api} via named interfaces.
 * <p>
 * {@code @Component} is required — the annotation scanner only discovers Spring beans (Pitfall 4).
 */
@Component
public class PortfolioMcpTools {

    private static final Logger log = LoggerFactory.getLogger(PortfolioMcpTools.class);

    private final PortfolioService portfolioService;
    private final RiskCalculator riskCalculator;
    private final PortfolioRepository portfolioRepository;
    private final ObjectMapper objectMapper;

    public PortfolioMcpTools(PortfolioService portfolioService,
                             RiskCalculator riskCalculator,
                             PortfolioRepository portfolioRepository,
                             ObjectMapper objectMapper) {
        this.portfolioService = portfolioService;
        this.riskCalculator = riskCalculator;
        this.portfolioRepository = portfolioRepository;
        this.objectMapper = objectMapper;
    }

    // =========================================================================
    // MCP Tools
    // =========================================================================

    /**
     * Returns the authenticated user's portfolio summary: total market value,
     * total unrealized P&amp;L, daily change, and sector allocation weights.
     * <p>
     * SDK coordinates: io.modelcontextprotocol.spec.McpSchema.CallToolResult (Open Q1 resolved).
     */
    @McpTool(
            name = "get_portfolio_summary",
            description = "Returns the authenticated user's portfolio: total market value, " +
                          "total cost basis, total unrealized P&L (absolute and percentage), " +
                          "daily change (absolute and percentage), and sector allocation weights."
    )
    public McpSchema.CallToolResult getPortfolioSummary() {
        try {
            Long portfolioId = resolvePortfolioId();
            PortfolioPnlDto pnl = portfolioService.getPortfolioPnl(portfolioId);
            List<AllocationSliceDto> alloc = portfolioService.getAllocation(portfolioId);

            List<PortfolioSummaryResult.AllocationEntry> allocEntries = alloc.stream()
                    .map(s -> new PortfolioSummaryResult.AllocationEntry(
                            s.label(), s.weight(), s.marketValue()))
                    .toList();

            PortfolioSummaryResult result = new PortfolioSummaryResult(
                    pnl.totalMarketValue(),
                    pnl.totalCostBasis(),
                    pnl.totalUnrealizedGainAbs(),
                    pnl.totalUnrealizedGainPct(),
                    pnl.dailyChangeAbs(),
                    pnl.dailyChangePct(),
                    allocEntries
            );
            String json = objectMapper.writeValueAsString(result);
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(json)))
                    .isError(false)
                    .build();
        } catch (ResponseStatusException e) {
            // Auth/not-found — safe to return message (static HTTP status phrase)
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent("Portfolio not found for authenticated user")))
                    .isError(true)
                    .build();
        } catch (Exception e) {
            log.error("get_portfolio_summary failed (not forwarded to client)", e);
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent("Portfolio summary unavailable")))
                    .isError(true)
                    .build();
        }
    }

    /**
     * Returns risk metrics for the authenticated user's portfolio:
     * annualized Sharpe ratio, annualized volatility, max drawdown,
     * beta vs SPX500, historical VaR (95%, 1-day), parametric VaR (95%, 1-day).
     */
    @McpTool(
            name = "get_risk_metrics",
            description = "Returns risk metrics for the authenticated user's portfolio: " +
                          "annualized Sharpe ratio, annualized volatility, max drawdown, " +
                          "beta vs SPX500, historical VaR (95%, 1-day), parametric VaR (95%, 1-day)."
    )
    public McpSchema.CallToolResult getRiskMetrics() {
        try {
            Long portfolioId = resolvePortfolioId();
            RiskScorecardDto scorecard = riskCalculator.computeRiskScorecard(portfolioId);

            // Extract VaR amounts by method name (T-09-02: no recompute)
            BigDecimalRef histVar = new BigDecimalRef();
            BigDecimalRef paramVar = new BigDecimalRef();
            for (VarResultDto var : scorecard.var()) {
                if ("HISTORICAL".equals(var.method())) {
                    histVar.value = var.amount();
                } else if ("PARAMETRIC".equals(var.method())) {
                    paramVar.value = var.amount();
                }
            }

            // WR-02: never serialize null VaR to the client/LLM — if either method is absent
            // (empty/unexpected VaR list), surface a static error rather than {"...Var95":null}.
            if (histVar.value == null || paramVar.value == null) {
                log.error("get_risk_metrics: VaR list missing HISTORICAL or PARAMETRIC entry "
                        + "(methods present: {})",
                        scorecard.var().stream().map(VarResultDto::method).toList());
                return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent("Risk metrics unavailable")))
                        .isError(true)
                        .build();
            }

            RiskMetricsResult result = new RiskMetricsResult(
                    scorecard.sharpeRatio(),
                    scorecard.annualizedVolatility(),
                    scorecard.maxDrawdown(),
                    scorecard.beta(),
                    histVar.value,
                    paramVar.value
            );
            String json = objectMapper.writeValueAsString(result);
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(json)))
                    .isError(false)
                    .build();
        } catch (ResponseStatusException e) {
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent("Portfolio not found for authenticated user")))
                    .isError(true)
                    .build();
        } catch (Exception e) {
            log.error("get_risk_metrics failed (not forwarded to client)", e);
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent("Risk metrics unavailable")))
                    .isError(true)
                    .build();
        }
    }

    /**
     * Returns a single holding's detail by ticker symbol for the authenticated user.
     * <p>
     * Ticker is sanitized: uppercased, stripped to alphanumeric + '.', capped at 10 chars (T-09-05).
     */
    @McpTool(
            name = "get_position_detail",
            description = "Returns a single holding's detail by ticker symbol for the authenticated user. " +
                          "Provide the ticker symbol (e.g. AAPL, MSFT, BRK.B)."
    )
    public McpSchema.CallToolResult getPositionDetail(
            @McpToolParam(description = "Ticker symbol, e.g. AAPL", required = true)
            String ticker
    ) {
        try {
            // T-09-05: Sanitize ticker — uppercase, alphanumeric + '.', max 10 chars
            String sanitized = sanitizeTicker(ticker);

            Long portfolioId = resolvePortfolioId();
            List<HoldingDto> holdings = portfolioService.getHoldings(portfolioId);

            HoldingDto holding = holdings.stream()
                    .filter(h -> sanitized.equals(h.ticker()))
                    .findFirst()
                    .orElse(null);

            if (holding == null) {
                // IN-02: static message — do not echo even sanitized input (no ticker enumeration).
                return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent("Position not found for the requested ticker")))
                        .isError(true)
                        .build();
            }

            PositionDetailResult result = new PositionDetailResult(
                    holding.ticker(),
                    holding.name(),
                    holding.sector(),
                    holding.quantity(),
                    holding.avgCostBasis(),
                    holding.currentPrice(),
                    holding.currentMarketValue(),
                    holding.portfolioWeight(),
                    holding.unrealizedPnlAbs(),
                    holding.unrealizedPnlPct()
            );
            String json = objectMapper.writeValueAsString(result);
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(json)))
                    .isError(false)
                    .build();
        } catch (ResponseStatusException e) {
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent("Portfolio not found for authenticated user")))
                    .isError(true)
                    .build();
        } catch (Exception e) {
            log.error("get_position_detail failed (not forwarded to client)", e);
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent("Position detail unavailable")))
                    .isError(true)
                    .build();
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Derives the authenticated user's portfolio ID from the Spring Security context.
     * <p>
     * Equivalent to {@code AnalyticsController#resolvePortfolioId(Authentication)} but
     * pulls from {@link SecurityContextHolder} (no parameter injection available in @McpTool).
     * Both "user not found" and "user has no portfolio" return 401 — IDOR prevention T-09-02.
     *
     * @return the resolved portfolio ID
     * @throws ResponseStatusException 401 if unauthenticated, user not found, or no portfolio
     */
    private Long resolvePortfolioId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // AnonymousAuthenticationToken.isAuthenticated() returns true by design, so the
        // isAuthenticated() check alone would let "anonymousUser" through (CR-02). Reject it
        // explicitly — defence-in-depth behind the /mcp .authenticated() + httpBasic gate.
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        String username = auth.getName();
        return portfolioRepository.findPortfolioIdByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    /**
     * Sanitizes the ticker parameter for {@code get_position_detail}.
     * <p>
     * Uppercase, strip to [A-Z0-9.] only, cap at 10 characters (T-09-05).
     * Prevents injection of unexpected characters before passing to JPA query.
     */
    private String sanitizeTicker(String ticker) {
        if (ticker == null) {
            return "";
        }
        // IN-01: compute the cleaned form once, then cap length.
        String cleaned = ticker.toUpperCase().replaceAll("[^A-Z0-9.]", "");
        return cleaned.length() > 10 ? cleaned.substring(0, 10) : cleaned;
    }

    /**
     * Simple mutable holder for extracting VaR amounts from a stream without var capture issues.
     * Not exposed outside this class.
     */
    private static class BigDecimalRef {
        java.math.BigDecimal value;
    }
}
