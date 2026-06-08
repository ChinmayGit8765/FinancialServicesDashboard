# Phase 4: Quant Risk Engine — Pattern Map

**Mapped:** 2026-06-08
**Files analyzed:** 19 new/modified files
**Analogs found:** 19 / 19

---

## File Classification

| New / Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---------------------|------|-----------|----------------|---------------|
| `backend/.../analytics/package-info.java` | config | — | `backend/.../portfolio/package-info.java` | exact |
| `backend/.../analytics/api/AnalyticsController.java` | controller | request-response | `backend/.../portfolio/api/PortfolioController.java` | exact |
| `backend/.../analytics/api/RiskScorecardDto.java` | model | — | `backend/.../portfolio/api/DateValueDto.java` (record) | exact |
| `backend/.../analytics/api/VarResultDto.java` | model | — | same | exact |
| `backend/.../analytics/api/CorrelationMatrixDto.java` | model | — | same | exact |
| `backend/.../analytics/api/AttributionDto.java` | model | — | same | exact |
| `backend/.../analytics/api/PairResultDto.java` | model | — | same | exact |
| `backend/.../analytics/service/RiskCalculator.java` | service | CRUD | `backend/.../portfolio/service/PortfolioService.java` | role-match |
| `backend/.../analytics/service/FamaFrenchCalculator.java` | service | batch | same | role-match |
| `backend/.../analytics/service/CointegrationScanner.java` | service | batch | same | role-match |
| `backend/src/test/.../analytics/RiskCalculatorTest.java` | test | — | `backend/src/test/.../portfolio/PortfolioServiceTest.java` | exact |
| `backend/src/test/.../analytics/FamaFrenchCalculatorTest.java` | test | — | same | exact |
| `backend/src/test/.../analytics/CointegrationScannerTest.java` | test | — | same | exact |
| `backend/src/test/.../analytics/AnalyticsGoldenValuePrinterTest.java` | test | — | `backend/src/test/.../portfolio/GoldenValuePrinterTest.java` | exact |
| `backend/src/test/.../analytics/AnalyticsControllerIntegrationTest.java` | test | request-response | `backend/src/test/.../portfolio/PortfolioControllerIntegrationTest.java` | exact |
| `frontend/src/components/RiskScorecard.vue` | component | request-response | `frontend/src/components/KpiCard.vue` | exact |
| `frontend/src/components/CorrelationHeatmap.vue` | component | request-response | `frontend/src/components/AllocationChart.vue` | exact |
| `frontend/src/components/AttributionChart.vue` | component | request-response | `frontend/src/components/AllocationChart.vue` | role-match |
| `frontend/src/components/PairsTable.vue` | component | request-response | `frontend/src/components/HoldingsTable.vue` | role-match |
| `frontend/src/stores/portfolio.ts` | store | — | self (extend existing) | exact |
| `frontend/src/api/analytics.ts` | utility | request-response | `frontend/src/api/portfolio.ts` | exact |
| `frontend/src/__tests__/components/RiskScorecard.test.ts` | test | — | `frontend/src/__tests__/components/KpiCard.test.ts` | exact |

---

## Pattern Assignments

### `backend/.../analytics/package-info.java` (config, Modulith module declaration)

**Analog:** `backend/src/main/java/com/quantlens/portfolio/package-info.java`

**Exact pattern** (lines 1–4 of analog):
```java
@org.springframework.modulith.ApplicationModule(
        displayName = "Analytics",
        allowedDependencies = {"marketdata::domain", "portfolio::domain"})
package com.quantlens.analytics;
```

**Key notes:**
- `portfolio::domain` named interface is already declared at `backend/src/main/java/com/quantlens/portfolio/domain/package-info.java` (line 5: `@org.springframework.modulith.NamedInterface("domain")`). No changes needed there.
- `marketdata::domain` named interface is already declared at `backend/src/main/java/com/quantlens/marketdata/domain/package-info.java` (line 5: `@org.springframework.modulith.NamedInterface("domain")`). No changes needed.
- Do NOT add `portfolio::service` as an allowed dependency — analytics replicates the equity-curve logic locally (20 lines) rather than introducing a cross-module service dependency.

---

### `backend/.../analytics/api/AnalyticsController.java` (controller, request-response)

**Analog:** `backend/src/main/java/com/quantlens/portfolio/api/PortfolioController.java`

**Imports pattern** (lines 1–18 of analog):
```java
package com.quantlens.analytics.api;

import com.quantlens.analytics.service.RiskCalculator;
import com.quantlens.analytics.service.FamaFrenchCalculator;
import com.quantlens.analytics.service.CointegrationScanner;
import com.quantlens.portfolio.domain.PortfolioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
```

**Class + constructor pattern** (lines 48–60 of analog):
```java
@RestController
@RequestMapping("/api/portfolio")
public class AnalyticsController {

    private final PortfolioRepository portfolioRepository;
    private final RiskCalculator riskCalculator;
    private final FamaFrenchCalculator ffCalculator;
    private final CointegrationScanner cointegrationScanner;

    public AnalyticsController(PortfolioRepository portfolioRepository,
                               RiskCalculator riskCalculator,
                               FamaFrenchCalculator ffCalculator,
                               CointegrationScanner cointegrationScanner) { ... }
```

**Auth / principal resolution pattern** (lines 180–187 of analog — copy exactly):
```java
private Long resolvePortfolioId(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    String username = authentication.getName();
    return portfolioRepository.findPortfolioIdByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
}
```

**Endpoint pattern** (lines 74–79 of analog):
```java
@GetMapping("/risk")
@Transactional(readOnly = true)
public ResponseEntity<RiskScorecardDto> getRisk(Authentication authentication) {
    Long portfolioId = resolvePortfolioId(authentication);
    return ResponseEntity.ok(riskCalculator.computeRiskScorecard(portfolioId));
}
```
Apply same `@GetMapping + @Transactional(readOnly = true) + resolvePortfolioId` pattern to `/correlation`, `/attribution`, `/pairs`.

**Key notes:**
- IDOR prevention: portfolioId is NEVER a `@RequestParam` or `@PathVariable` — always derived from `authentication.getName()` via `PortfolioRepository.findPortfolioIdByUsername`.
- All four endpoints are read-only. No write operations in this controller.
- `PortfolioRepository` is in `portfolio::domain` which is an allowed dependency.

---

### DTO Records (`RiskScorecardDto`, `VarResultDto`, `CorrelationMatrixDto`, `AttributionDto`, `PairResultDto`)

**Analog:** `backend/src/main/java/com/quantlens/portfolio/api/DateValueDto.java` (Java record style)

**Record pattern** — copy this compact style for all five DTOs:
```java
package com.quantlens.analytics.api;

import java.math.BigDecimal;
import java.util.List;

// VarResultDto.java
public record VarResultDto(
    String method,       // "HISTORICAL" or "PARAMETRIC" or "CVaR_HISTORICAL"
    double confidence,   // 0.95
    int horizonDays,     // 1
    BigDecimal amount,   // positive monetary VaR (BigDecimal for money)
    double percentage    // VaR as fraction of portfolio value (double for stats)
) {}

// RiskScorecardDto.java
public record RiskScorecardDto(
    double sharpeRatio,
    double annualizedVolatility,
    double maxDrawdown,            // negative; e.g. -0.18 = 18% drawdown
    double beta,
    List<VarResultDto> var         // 2-3 entries: HISTORICAL, PARAMETRIC, optional CVaR
) {}

// CorrelationMatrixDto.java
public record CorrelationMatrixDto(
    List<String> tickers,
    List<List<Double>> matrix      // tickers.size() × tickers.size(); diagonal = 1.0
) {}

// AttributionDto.java
public record AttributionDto(
    double alphaAnnualized,        // intercept × 252
    double betaMkt,
    double betaSmb,
    double betaHml,
    double rSquared,
    double contribMktAnnualized,   // betaMkt × mean(MktRf) × 252
    double contribSmbAnnualized,
    double contribHmlAnnualized
) {}

// PairResultDto.java
public record PairResultDto(
    String tickerY,
    String tickerX,
    double hedgeRatio,
    double adfStatistic,
    double pValue,
    double spreadZScore,
    String signal                  // "LONG_Y_SHORT_X" | "SHORT_Y_LONG_X" | "NEUTRAL"
) {}
```

**Numeric convention (from CONTEXT.md, enforced throughout):**
- Statistical quantities (Sharpe, vol, beta, correlation, betas, R², p-values, Z-scores, percentages) → `double`
- Monetary values (VaR in currency) → `BigDecimal`

---

### `backend/.../analytics/service/RiskCalculator.java` (service, CRUD)

**Analog:** `backend/src/main/java/com/quantlens/portfolio/service/PortfolioService.java`

**Class declaration pattern** (lines 56–73 of analog):
```java
@Service
@Transactional(readOnly = true)
public class RiskCalculator {

    private final PositionRepository positionRepository;
    private final OhlcvBarRepository ohlcvBarRepository;
    private final SecurityRepository securityRepository;
    private final FactorReturnRepository factorReturnRepository;

    public RiskCalculator(PositionRepository positionRepository,
                          OhlcvBarRepository ohlcvBarRepository,
                          SecurityRepository securityRepository,
                          FactorReturnRepository factorReturnRepository) { ... }
```

**Equity curve replication pattern** — mirror `PortfolioService.buildEquityCurve` (lines 649–710 of analog) with comment `// REUSE: mirrors PortfolioService.buildEquityCurve`:
```java
// Key accessors used (from OhlcvBar.java, lines 87 / 75):
bar.getClosePrice()   // returns BigDecimal
bar.getBarDate()      // returns LocalDate
bar.getSecurity().getId()

// Benchmark lookup (from SecurityRepository.java, line 15):
securityRepository.findByBenchmarkTrue()  // returns List<Security>
// Security.isBenchmark() == true for SPX500

// All bars fetch (from OhlcvBarRepository.java, lines 47-52):
ohlcvBarRepository.findAllBySecurityIdsOrdered(List<Long> securityIds)
// ORDER BY security.id ASC, barDate ASC
```

**Log-return helper** (static, no Spring context needed):
```java
static double[] logReturns(List<DateValueDto> curve) {
    double[] r = new double[curve.size() - 1];
    for (int i = 1; i < curve.size(); i++) {
        double p1 = curve.get(i).value().doubleValue();
        double p0 = curve.get(i - 1).value().doubleValue();
        r[i - 1] = Math.log(p1 / p0);
    }
    return r;
}
```

**FactorReturn field accessors** (from `FactorReturn.java`, lines 63–76 — exact method names):
```java
fr.getFactorDate()   // LocalDate — for alignment assertion
fr.getMktRf()        // BigDecimal → .doubleValue()
fr.getSmb()          // BigDecimal → .doubleValue()
fr.getHml()          // BigDecimal → .doubleValue()
fr.getRf()           // BigDecimal → .doubleValue() (daily risk-free rate ≈ 0.04/252)
```

**Security accessor** (from `Security.java`, lines 60–63):
```java
security.isBenchmark()   // boolean — true for SPX500
security.getSector()     // String — for pairs scanner grouping
security.getTicker()     // String — for CorrelationMatrixDto labels
```

**Hipparchus core patterns** (all imports from `org.hipparchus.stat.*`):
```java
import org.hipparchus.stat.descriptive.DescriptiveStatistics;
import org.hipparchus.stat.correlation.Covariance;
import org.hipparchus.stat.correlation.PearsonsCorrelation;
import org.hipparchus.stat.regression.OLSMultipleLinearRegression;
import org.hipparchus.distribution.continuous.NormalDistribution;

// Sharpe:
DescriptiveStatistics stats = new DescriptiveStatistics(logReturns);
double sharpe = (meanExcess / stats.getStandardDeviation()) * Math.sqrt(252.0);

// Beta:
Covariance cov = new Covariance();
double beta = cov.covariance(portfolioReturns, benchmarkReturns) /
              new DescriptiveStatistics(benchmarkReturns).getVariance();

// Historical VaR (95%):
double varPct = -new DescriptiveStatistics(returns).getPercentile(5.0); // positive loss

// Parametric VaR (95%):
double z95 = 1.645; // documented constant
double parametricVarPct = z95 * stats.getStandardDeviation() - stats.getMean();

// FF regression (see FamaFrenchCalculator section for xMatrix layout)
```

---

### `backend/.../analytics/service/FamaFrenchCalculator.java` (service, batch)

**Analog:** `backend/src/main/java/com/quantlens/portfolio/service/PortfolioService.java`

**Class declaration** — same `@Service @Transactional(readOnly = true)` pattern.

**OLS regression pattern** (from RESEARCH.md — exact Hipparchus API):
```java
// CRITICAL index layout — factor for return[i] = factorRows.get(i + 1)
// because return[i] = ln(close[i+1]/close[i]), which aligns with factors on day i+1
double[][] xMatrix = new double[503][3];
for (int i = 0; i < 503; i++) {
    FactorReturn fr = factorRows.get(i + 1); // factor for day i+1 (NOT i)
    xMatrix[i][0] = fr.getMktRf().doubleValue();
    xMatrix[i][1] = fr.getSmb().doubleValue();
    xMatrix[i][2] = fr.getHml().doubleValue();
    excessReturns[i] = returns503[i] - fr.getRf().doubleValue();
}

OLSMultipleLinearRegression reg = new OLSMultipleLinearRegression();
reg.newSampleData(excessReturns, xMatrix); // Hipparchus adds intercept automatically

double[] params = reg.estimateRegressionParameters();
// params[0] = α (intercept — daily), params[1] = β_mkt, params[2] = β_smb, params[3] = β_hml
// ASSERT: params.length == 4

double alphaAnnualized = params[0] * 252.0;
double rSquared = reg.calculateRSquared();
double[] se = reg.estimateRegressionParametersStandardErrors();
```

**FactorReturnRepository query** — add named query for ordered fetch:
```java
// In FactorReturnRepository (currently only has JpaRepository<FactorReturn, Long>):
List<FactorReturn> findAllByOrderByFactorDateAsc();
// Returns 504 rows ordered by factorDate ASC — factor[0] = day 0, factor[503] = day 503
```

---

### `backend/.../analytics/service/CointegrationScanner.java` (service, batch)

**Analog:** `backend/src/main/java/com/quantlens/portfolio/service/PortfolioService.java`

**Class declaration** — same `@Service @Transactional(readOnly = true)` pattern.

**Step 1 — OLS hedge ratio** (uses same `OLSMultipleLinearRegression`):
```java
// logPriceY/X: double[504] = Math.log(bar.getClosePrice().doubleValue())
double[][] xCol = new double[504][1];
for (int i = 0; i < 504; i++) xCol[i][0] = logPriceX[i];
OLSMultipleLinearRegression step1 = new OLSMultipleLinearRegression();
step1.newSampleData(logPriceY, xCol);
double hedgeRatio = step1.estimateRegressionParameters()[1]; // params[0]=intercept, params[1]=β
double[] residuals = step1.estimateResiduals(); // ADF is on THIS, NOT raw prices
```

**Step 2 — ADF construction** (hand-rolled from OLS):
```java
// adfN = spread.length - 2 = 502
double[] adfY  = new double[adfN];
double[][] adfX = new double[adfN][2]; // [S_{t-1}, ΔS_{t-1}]
for (int i = 0; i < adfN; i++) {
    adfY[i]    = residuals[i + 2] - residuals[i + 1];  // ΔS[t]
    adfX[i][0] = residuals[i + 1];                      // S[t-1]
    adfX[i][1] = residuals[i + 1] - residuals[i];       // ΔS[t-1]
}
OLSMultipleLinearRegression adfReg = new OLSMultipleLinearRegression();
adfReg.newSampleData(adfY, adfX);
// params[0]=intercept, params[1]=δ, params[2]=φ_1
double delta   = adfReg.estimateRegressionParameters()[1];
double seDelta = adfReg.estimateRegressionParametersStandardErrors()[1];
double tStat   = delta / seDelta;
// WARNING: Do NOT use TDistribution.cumulativeProbability here. ADF follows the
// Dickey-Fuller distribution. Use mackinnonPValue() (MacKinnon polynomial approx).
```

**MacKinnon p-value constants** (from RESEARCH.md, verified against statsmodels):
```java
static final double ADF_CV_1PCT  = -3.430;
static final double ADF_CV_5PCT  = -2.862;
static final double ADF_CV_10PCT = -2.567;

// mackinnonPValue(tau) — see RESEARCH.md lines 639–655 for full implementation
// tau_star=-1.61, tau_min=-18.83, tau_max=2.74
// small-p: coeffs=[2.1659, 1.4412, 0.038269]
// large-p: coeffs=[1.7339, 0.93202, -0.12745, -0.010368]
```

**Candidate pair selection** (uses `Security.getSector()`):
```java
// Group portfolio securities by sector; same-sector pairs only
// Cap: max 20 candidate pairs
positions.stream()
    .map(p -> p.getSecurity())
    .collect(Collectors.groupingBy(Security::getSector))
    // → generate C(n,2) pairs per sector group
```

---

### `backend/src/test/.../analytics/AnalyticsGoldenValuePrinterTest.java` (test, @Disabled)

**Analog:** `backend/src/test/java/com/quantlens/portfolio/GoldenValuePrinterTest.java`

**Full class scaffold** (copy lines 36–38 and 56–58 of analog exactly):
```java
package com.quantlens.analytics;

import com.quantlens.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Golden-value capture test — prints alice's exact seed-derived risk constants
 * so they can be baked into assertion constants in RiskCalculatorTest and
 * AnalyticsControllerIntegrationTest.
 * <p>
 * MUST remain @Disabled in CI. Enable once manually: {@code .\mvnw.cmd test -Dtest=AnalyticsGoldenValuePrinterTest}
 */
class AnalyticsGoldenValuePrinterTest extends AbstractPostgresIntegrationTest {

    @Autowired RiskCalculator riskCalculator;
    @Autowired FamaFrenchCalculator ffCalc;
    @Autowired CointegrationScanner scanner;

    @Test
    @Disabled("Golden-value capture — enable once to print seed constants, then re-disable")
    @Transactional(readOnly = true)
    void printGoldenValues_aliceGrowthPortfolio() {
        // Resolve alice's portfolio from the seeded DB (same pattern as GoldenValuePrinterTest)
        // Call each metric computation
        // Print all values with 8 decimal places
        System.out.printf("SHARPE=%.8f%n", ...);
        System.out.printf("ANNUAL_VOL=%.8f%n", ...);
        System.out.printf("MAX_DRAWDOWN=%.8f%n", ...);
        System.out.printf("BETA=%.8f%n", ...);
        System.out.printf("HIST_VAR_PCT=%.8f%n", ...);
        System.out.printf("PARAM_VAR_PCT=%.8f%n", ...);
        System.out.printf("ALPHA_ANNUALIZED=%.8f%n", ...);
        System.out.printf("BETA_MKT=%.8f%n", ...);
        System.out.printf("R_SQUARED=%.8f%n", ...);
        // ADF known-value sanity check:
        // Assert mackinnonPValue(-3.0) ≈ 0.034 ± 0.01
    }
}
```

**Key pattern: @Disabled annotation** is mandatory. Analog uses `@Disabled("Golden-value capture — enable once to print seed constants, then re-disable")` at line 57.

---

### `backend/src/test/.../analytics/RiskCalculatorTest.java` (test, pure unit — no Spring)

**Analog:** `backend/src/test/java/com/quantlens/portfolio/PortfolioServiceTest.java`

**Class scaffold** (lines 1–30 of analog — note: NO @SpringBootTest, no @Autowired):
```java
package com.quantlens.analytics;

import com.quantlens.analytics.service.RiskCalculator;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for pure computation methods in RiskCalculator.
 * No Spring context, no Testcontainers, no mocks needed.
 * All tests call static helpers or inject hand-crafted inputs.
 */
class RiskCalculatorTest {

    private static final double GOLDEN_SHARPE       = 0.0; // FILL from printer
    private static final double GOLDEN_ANNUAL_VOL   = 0.0; // FILL from printer
    private static final double GOLDEN_MAX_DRAWDOWN = 0.0; // FILL from printer
    private static final double GOLDEN_BETA         = 0.0; // FILL from printer

    @Test
    void sharpe_alice_matchesGoldenValue() {
        assertThat(actualSharpe).isCloseTo(GOLDEN_SHARPE, within(0.001));
    }
    // etc. — tolerance table from RESEARCH.md
```

**AssertJ tolerance pattern** (from analog lines 44–56 — `isEqualByComparingTo` for BigDecimal, `isCloseTo` for double):
```java
// For double assertions (Sharpe, vol, beta, etc.):
import static org.assertj.core.api.Assertions.within;
assertThat(actual).isCloseTo(expected, within(0.001));

// For BigDecimal assertions (VaR monetary amount):
assertThat(actualAmount).isCloseTo(expectedAmount,
        org.assertj.core.data.Offset.offset(new BigDecimal("0.01")));
```

---

### `backend/src/test/.../analytics/AnalyticsControllerIntegrationTest.java` (integration test)

**Analog:** `backend/src/test/java/com/quantlens/portfolio/PortfolioControllerIntegrationTest.java`

**Class scaffold** (lines 1–37 of analog — copy `extends AbstractPostgresIntegrationTest` + `@Autowired TestRestTemplate`):
```java
package com.quantlens.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlens.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsControllerIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
```

**Auth flow helper** (lines 362–379 of analog — copy exactly as `loginAndGetSessionCookie`):
```java
private String loginAndGetSessionCookie(String username) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("username", username);
    body.add("password", "demo1234");
    ResponseEntity<String> response = restTemplate.exchange(
            "/api/auth/login", HttpMethod.POST,
            new HttpEntity<>(body, headers), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
}

private ResponseEntity<String> authenticatedGet(String url, String cookie) {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.COOKIE, cookie);
    return restTemplate.exchange(url, HttpMethod.GET,
            new HttpEntity<>(headers), String.class);
}
```

**Unauthenticated gate test** (copy pattern from lines 48–58 of analog):
```java
@Test
void getRisk_unauthenticated_returns401() {
    ResponseEntity<String> response = restTemplate.exchange(
            "/api/portfolio/risk", HttpMethod.GET, HttpEntity.EMPTY, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
}
```

---

## Frontend Pattern Assignments

### `frontend/src/api/analytics.ts` (utility, request-response)

**Analog:** `frontend/src/api/portfolio.ts`

**DTO interface pattern** (lines 11–50 of analog):
```typescript
// analytics.ts — add DTO interfaces mirroring Java records
export interface VarResultDto {
  method: string        // "HISTORICAL" | "PARAMETRIC" | "CVaR_HISTORICAL"
  confidence: number    // 0.95
  horizonDays: number   // 1
  amount: number        // positive monetary VaR (BigDecimal serialized as number)
  percentage: number    // fraction e.g. 0.018 = 1.8%
}

export interface RiskScorecardDto {
  sharpeRatio: number
  annualizedVolatility: number
  maxDrawdown: number           // negative; e.g. -0.18
  beta: number
  var: VarResultDto[]           // 2-3 entries
}

export interface CorrelationMatrixDto {
  tickers: string[]
  matrix: number[][]            // tickers.length × tickers.length
}

export interface AttributionDto {
  alphaAnnualized: number
  betaMkt: number
  betaSmb: number
  betaHml: number
  rSquared: number
  contribMktAnnualized: number
  contribSmbAnnualized: number
  contribHmlAnnualized: number
}

export interface PairResultDto {
  tickerY: string
  tickerX: string
  hedgeRatio: number
  adfStatistic: number
  pValue: number
  spreadZScore: number
  signal: 'LONG_Y_SHORT_X' | 'SHORT_Y_LONG_X' | 'NEUTRAL'
}
```

**Note:** This file declares interfaces only. The actual fetch calls live in the Pinia store (same pattern as portfolio — the `portfolio.ts` API file has no standalone fetch functions, only interfaces and type exports).

---

### `frontend/src/stores/portfolio.ts` (store, EXTEND existing)

**Analog:** self — `frontend/src/stores/portfolio.ts`

**New state declarations** (follow lines 34–38 pattern exactly):
```typescript
// Add after existing state declarations (after `benchmark`):
const risk        = asyncState<RiskScorecardDto>(null)
const correlation = asyncState<CorrelationMatrixDto>(null)
const attribution = asyncState<AttributionDto>(null)
const pairs       = asyncState<PairResultDto[]>(null)
```

**New fetch action pattern** (copy `fetchBenchmark` lines 134–147 exactly for each):
```typescript
async function fetchRisk(version?: number): Promise<void> {
  risk.loading = true
  risk.error = null
  try {
    const { data } = await axios.get<RiskScorecardDto>('/api/portfolio/risk')
    if (version !== undefined && version !== refreshVersion) return
    risk.data = data
  } catch (e: any) {
    if (version !== undefined && version !== refreshVersion) return
    risk.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to load risk metrics'
  } finally {
    risk.loading = false
  }
}
// Repeat for fetchCorrelation, fetchAttribution, fetchPairs (same structure)
```

**refreshAll extension** (add 4 new fetches to `Promise.allSettled` at lines 165–172):
```typescript
async function refreshAll(): Promise<void> {
  const myVersion = ++refreshVersion
  await Promise.allSettled([
    fetchHoldings(myVersion),
    fetchPnl(myVersion),
    fetchAllocation(myVersion),
    fetchTransactions(0, myVersion),
    fetchBenchmark(myVersion),
    fetchRisk(myVersion),          // NEW
    fetchCorrelation(myVersion),   // NEW
    fetchAttribution(myVersion),   // NEW
    fetchPairs(myVersion),         // NEW
  ])
}
```

**$reset extension** (add 4 new resources at lines 181–185):
```typescript
function $reset(): void {
  // ... existing resets ...
  risk.data = null; risk.loading = false; risk.error = null
  correlation.data = null; correlation.loading = false; correlation.error = null
  attribution.data = null; attribution.loading = false; attribution.error = null
  pairs.data = null; pairs.loading = false; pairs.error = null
}
```

**return block extension** (expose all 8 new symbols):
```typescript
return {
  // existing ...
  risk, correlation, attribution, pairs,
  fetchRisk, fetchCorrelation, fetchAttribution, fetchPairs,
}
```

---

### `frontend/src/components/RiskScorecard.vue` (component, request-response)

**Analog:** `frontend/src/components/KpiCard.vue`

**Props pattern** (lines 4–15 of KpiCard.vue — adapt to RiskScorecard):
```typescript
<script setup lang="ts">
import KpiCard from './KpiCard.vue'
import type { RiskScorecardDto } from '@/api/analytics'
import { formatPercent, formatSignedPercent } from '@/utils/format'
import { usePortfolioStore } from '@/stores/portfolio'
import { computed } from 'vue'

const store = usePortfolioStore()

// Bind directly from store — do NOT destructure (loses reactivity, Pitfall 5)
const loading = computed(() => store.risk.loading)
const error   = computed(() => store.risk.error)
const risk    = computed(() => store.risk.data)
</script>
```

**Template pattern** — compose multiple `<KpiCard>` instances (use KpiCard's props `label`, `primary`, `secondary`, `loading`):
```html
<template>
  <section class="risk-scorecard" aria-label="Risk Scorecard">
    <!-- Loading state: KpiCard handles skeleton internally when loading=true -->
    <KpiCard
      label="Sharpe Ratio"
      :primary="risk ? risk.sharpeRatio.toFixed(2) : '—'"
      :loading="loading"
    />
    <KpiCard
      label="Ann. Volatility"
      :primary="risk ? formatPercent(risk.annualizedVolatility * 100) : '—'"
      :loading="loading"
    />
    <KpiCard
      label="Max Drawdown"
      :primary="risk ? formatSignedPercent(risk.maxDrawdown * 100) : '—'"
      :delta="risk?.maxDrawdown ? risk.maxDrawdown * 100 : undefined"
      :loading="loading"
    />
    <KpiCard
      label="Beta"
      :primary="risk ? risk.beta.toFixed(2) : '—'"
      :loading="loading"
    />
    <!-- VaR side-by-side table: render risk.var entries as a mini-table -->
    <!-- Error state: same pattern as AllocationChart.vue lines 177-180 -->
    <div v-if="error" class="chart-error" role="alert">
      <span>Failed to load risk metrics. Check your connection and try again.</span>
      <button class="retry-btn" @click="store.fetchRisk()">Retry</button>
    </div>
  </section>
</template>
```

**CSS pattern** — use CSS custom property tokens only (no hardcoded hex), identical to KpiCard lines 64–132:
```css
<style scoped>
.risk-scorecard {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: var(--space-md);
}
/* Reuse .chart-error, .retry-btn from AllocationChart.vue */
</style>
```

---

### `frontend/src/components/CorrelationHeatmap.vue` (component, request-response)

**Analog:** `frontend/src/components/AllocationChart.vue`

**Imports + props pattern** (lines 1–15 of AllocationChart.vue):
```typescript
<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import type { EChartsOption } from 'echarts/types/dist/shared'
import type { CorrelationMatrixDto } from '@/api/analytics'
import { usePortfolioStore } from '@/stores/portfolio'

// No THEME_KEY prop needed — provided globally in App.vue via provide(THEME_KEY, ...)
// VChart picks it up automatically (AllocationChart.vue comment line 187)

const store = usePortfolioStore()
const loading   = computed(() => store.correlation.loading)
const error     = computed(() => store.correlation.error)
const corrData  = computed(() => store.correlation.data)

const props = defineProps<{
  correlation: CorrelationMatrixDto | null
  loading: boolean
  error: string | null
}>()
const emit = defineEmits<{ retry: [] }>()
```

**ECharts heatmap option** (from RESEARCH.md — exact structure verified for ECharts 6.1.0):
```typescript
const option = computed<EChartsOption>(() => {
  if (!props.correlation?.tickers.length) return {}
  const dto = props.correlation
  const n = dto.tickers.length
  const data: [number, number, number][] = []
  for (let i = 0; i < n; i++)
    for (let j = 0; j < n; j++)
      data.push([j, i, dto.matrix[i][j]])  // [xIndex, yIndex, value]

  return {
    tooltip: {
      formatter: (params: any) => {
        const [xi, yi, v] = params.data
        return `${dto.tickers[yi]} / ${dto.tickers[xi]}: ${v.toFixed(3)}`
      }
    },
    xAxis: { type: 'category', data: dto.tickers, axisLabel: { rotate: 45 } },
    yAxis: { type: 'category', data: [...dto.tickers].reverse() },
    // yAxis reversed so (0,0) = top-left, matching matrix[0][0] diagonal
    visualMap: { min: -1, max: 1, calculable: true, orient: 'horizontal',
      left: 'center', bottom: 8,
      color: ['#ef4444', '#f8fafc', '#3b82f6']  // red=+1, white=0, blue=-1
    },
    series: [{ type: 'heatmap', data,
      label: { show: true, formatter: (p: any) => p.data[2].toFixed(2),
               fontSize: 10, color: '#1e293b' },
      emphasis: { itemStyle: { shadowBlur: 10 } }
    }]
  }
})
```

**Template pattern** (copy AllocationChart.vue lines 144–199 — loading/error/empty/chart states):
```html
<template>
  <figure class="chart-panel" :aria-busy="loading" aria-label="Correlation heatmap">
    <div v-if="loading" class="skeleton" style="height: 320px" aria-hidden="true" />
    <div v-else-if="error" class="chart-error" role="alert">
      <span>Failed to load correlation data. Check your connection and try again.</span>
      <button class="retry-btn" @click="emit('retry')">Retry</button>
    </div>
    <div v-else-if="!props.correlation?.tickers.length" class="chart-empty">
      No correlation data available.
    </div>
    <!-- No theme prop: THEME_KEY provided in App.vue propagates automatically -->
    <v-chart v-else class="chart" :option="option" :autoresize="true" />
    <figcaption class="sr-only">Pairwise return correlation heatmap.</figcaption>
  </figure>
</template>
```

**CSS pattern** — copy `.chart-panel`, `.chart`, `.skeleton`, `.chart-error`, `.retry-btn`, `.chart-empty`, `.sr-only` from AllocationChart.vue lines 202–328.

---

### `frontend/src/components/AttributionChart.vue` (component, request-response)

**Analog:** `frontend/src/components/AllocationChart.vue`

**Same imports/props scaffold** as CorrelationHeatmap.vue (replace dto type with `AttributionDto`).

**ECharts bar option** (from RESEARCH.md):
```typescript
const option = computed<EChartsOption>(() => {
  if (!props.attribution) return {}
  const dto = props.attribution
  const labels = ['Alpha', 'Mkt-RF', 'SMB', 'HML']
  const values = [
    dto.alphaAnnualized * 100,
    dto.contribMktAnnualized * 100,
    dto.contribSmbAnnualized * 100,
    dto.contribHmlAnnualized * 100,
  ]
  const colors = values.map(v => v >= 0 ? '#22c55e' : '#ef4444') // green/red per sign
  return {
    tooltip: { formatter: '{b}: {c}%' },
    xAxis: { type: 'category', data: labels },
    yAxis: { type: 'value', axisLabel: { formatter: '{value}%' } },
    series: [{
      type: 'bar',
      data: values.map((v, i) => ({ value: v, itemStyle: { color: colors[i] } })),
      label: { show: true, position: 'top',
               formatter: (p: any) => `${p.value.toFixed(2)}%` }
    }]
  }
})
```

**Template/CSS** — same structure as CorrelationHeatmap.vue.

---

### `frontend/src/components/PairsTable.vue` (component, request-response)

**Analog:** `frontend/src/components/HoldingsTable.vue`

**Props + table pattern** (lines 12–16 of HoldingsTable.vue):
```typescript
<script setup lang="ts">
import type { PairResultDto } from '@/api/analytics'

const props = defineProps<{
  pairs: PairResultDto[] | null
  loading: boolean
  error: string | null
}>()
const emit = defineEmits<{ retry: [] }>()
</script>
```

**Signal badge rendering** — no chart, simple HTML table with conditional styling:
```html
<template>
  <div class="table-panel">
    <div v-if="loading" class="skeleton" style="height: 200px" />
    <div v-else-if="error" class="chart-error" role="alert">
      <span>Failed to load pairs data. Check your connection and try again.</span>
      <button class="retry-btn" @click="emit('retry')">Retry</button>
    </div>
    <div v-else-if="!props.pairs?.length" class="chart-empty">
      No cointegrated pairs found in current holdings.
    </div>
    <table v-else class="pairs-table" aria-label="Cointegration pairs">
      <thead><tr>
        <th>Pair</th><th>p-value</th><th>Hedge β</th><th>Z-score</th><th>Signal</th>
      </tr></thead>
      <tbody>
        <tr v-for="pair in props.pairs" :key="`${pair.tickerY}-${pair.tickerX}`">
          <td>{{ pair.tickerY }} / {{ pair.tickerX }}</td>
          <td>{{ pair.pValue.toFixed(3) }}</td>
          <td>{{ pair.hedgeRatio.toFixed(3) }}</td>
          <td :class="zScoreClass(pair.spreadZScore)">{{ pair.spreadZScore.toFixed(2) }}</td>
          <td><span :class="['signal-badge', signalClass(pair.signal)]">{{ pair.signal }}</span></td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
```

---

### Frontend Component Tests

**Analog:** `frontend/src/__tests__/components/KpiCard.test.ts`

**Test scaffold** (lines 1–45 of analog — copy exactly for each component):
```typescript
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import RiskScorecard from '../../components/RiskScorecard.vue'

describe('RiskScorecard', () => {
  it('shows a shimmer skeleton when loading is true', () => {
    const wrapper = mount(RiskScorecard, {
      props: { risk: null, loading: true, error: null }
    })
    expect(wrapper.find('.skeleton').exists()).toBe(true)
  })

  it('shows metric values when loaded', () => {
    const wrapper = mount(RiskScorecard, {
      props: {
        risk: { sharpeRatio: 0.42, annualizedVolatility: 0.28,
                maxDrawdown: -0.15, beta: 1.25, var: [] },
        loading: false,
        error: null
      }
    })
    expect(wrapper.text()).toContain('0.42')
  })

  it('shows error state with retry button when error is set', () => {
    const wrapper = mount(RiskScorecard, {
      props: { risk: null, loading: false, error: 'Failed to load risk metrics' }
    })
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(wrapper.find('.retry-btn').exists()).toBe(true)
  })
})
```

---

## Shared Patterns

### Authentication / Principal Resolution
**Source:** `backend/src/main/java/com/quantlens/portfolio/api/PortfolioController.java` lines 180–187
**Apply to:** `AnalyticsController.java`
```java
private Long resolvePortfolioId(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    String username = authentication.getName();
    return portfolioRepository.findPortfolioIdByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
}
```

### Spring Modulith @ApplicationModule
**Source:** `backend/src/main/java/com/quantlens/portfolio/package-info.java` lines 1–4
**Apply to:** `analytics/package-info.java`
```java
@org.springframework.modulith.ApplicationModule(
        displayName = "Analytics",
        allowedDependencies = {"marketdata::domain", "portfolio::domain"})
package com.quantlens.analytics;
```

### @Transactional(readOnly = true) at class level
**Source:** `backend/.../portfolio/service/PortfolioService.java` line 57
**Apply to:** `RiskCalculator`, `FamaFrenchCalculator`, `CointegrationScanner`, `AnalyticsController`
```java
@Service
@Transactional(readOnly = true)
public class RiskCalculator { ... }
```

### Domain accessor names (exact — must not be guessed)
**Source:** entity classes in `backend/src/main/java/com/quantlens/marketdata/domain/`

| Entity | Method | Return type | Line |
|--------|--------|-------------|------|
| `OhlcvBar` | `.getClosePrice()` | `BigDecimal` | 87 |
| `OhlcvBar` | `.getBarDate()` | `LocalDate` | 75 |
| `OhlcvBar` | `.getSecurity()` | `Security` | 72 |
| `FactorReturn` | `.getMktRf()` | `BigDecimal` | 66 |
| `FactorReturn` | `.getSmb()` | `BigDecimal` | 68 |
| `FactorReturn` | `.getHml()` | `BigDecimal` | 70 |
| `FactorReturn` | `.getRf()` | `BigDecimal` | 72 |
| `FactorReturn` | `.getFactorDate()` | `LocalDate` | 64 |
| `Security` | `.isBenchmark()` | `boolean` | 62 |
| `Security` | `.getSector()` | `String` | 60 |
| `Security` | `.getTicker()` | `String` | 56 |
| `Security` | `.getId()` | `Long` | 54 |

### Repository queries used by analytics
**Source:** `OhlcvBarRepository.java` (lines 25–52), `SecurityRepository.java` (lines 14–16)
```java
// All bars for N securities — primary data-load for equity curve + ADF
ohlcvBarRepository.findAllBySecurityIdsOrdered(List<Long> securityIds)

// Latest bar per security — for current portfolio value (VaR denominator)
ohlcvBarRepository.findLatestBarBySecurityIds(List<Long> securityIds)

// Benchmark (SPX500)
securityRepository.findByBenchmarkTrue()  // returns List<Security> — size == 1

// FactorReturn ordered (ADD to FactorReturnRepository):
factorReturnRepository.findAllByOrderByFactorDateAsc()
```

### Vue component three-state pattern (loading / error / data)
**Source:** `frontend/src/components/AllocationChart.vue` lines 144–199
**Apply to:** All four new Vue components
```html
<div v-if="loading" class="skeleton" />
<div v-else-if="error" class="chart-error" role="alert">
  <span>[Static error copy — never display raw error object (T-03-08)]</span>
  <button class="retry-btn" @click="emit('retry')">Retry</button>
</div>
<div v-else-if="!data" class="chart-empty">[Empty state copy]</div>
<v-chart v-else :option="option" :autoresize="true" />
<!-- No theme prop: THEME_KEY provided globally — do NOT pass theme to VChart -->
```

### Pinia AsyncState pattern
**Source:** `frontend/src/stores/portfolio.ts` lines 15–25, 51–64
**Apply to:** 4 new store state entries
```typescript
// asyncState factory (already defined — reuse):
function asyncState<T>(init: T | null = null): AsyncState<T> {
  return reactive({ data: init, loading: false, error: null }) as AsyncState<T>
}
// Race guard: each fetch receives `version`; abandon write if version !== refreshVersion
// Error strings: '401' → 'Session expired', other → 'Failed to load [resource]'
// NEVER rethrow — errors become .error strings (T-03-05)
```

### CSS scoped token pattern
**Source:** `frontend/src/components/AllocationChart.vue` lines 202–328
**Apply to:** All four new Vue components
```css
/* Always use CSS custom properties — NO hardcoded hex or pixels */
background: var(--color-bg-surface);
border-radius: var(--radius-lg);
box-shadow: var(--shadow-card);
border: 1px solid var(--color-border);
padding: var(--space-md);
color: var(--color-text-primary);
/* Shimmer animation from AllocationChart.vue lines 263-278 — copy verbatim */
```

### BigDecimal → double conversion for statistics
**Source:** `backend/.../portfolio/service/PortfolioService.java` (buildEquityCurve, line 705)
**Apply to:** All three calculator services
```java
// BigDecimal closes from OhlcvBar → double for Hipparchus:
double price = bar.getClosePrice().doubleValue();
// BigDecimal factor values → double:
double mktRf = factorReturn.getMktRf().doubleValue();
// double VaR result → BigDecimal for DTO:
BigDecimal varAmount = BigDecimal.valueOf(varPct * portfolioValue)
        .setScale(2, RoundingMode.HALF_UP);
```

---

## No Analog Found

No files are without a usable analog. All patterns are covered by existing Phase 1–3 code.

---

## Metadata

**Analog search scope:** `backend/src/main/java/com/quantlens/`, `backend/src/test/java/com/quantlens/`, `frontend/src/`
**Files read for pattern extraction:** 19 source files
**Pattern extraction date:** 2026-06-08
