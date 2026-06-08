# Phase 5: Stochastic Forecasting - Pattern Map

**Mapped:** 2026-06-08
**Files analyzed:** 13 new/modified files
**Analogs found:** 13 / 13

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `backend/.../analytics/service/ForecastService.java` | service | CRUD / batch | `RiskCalculator.java` | exact |
| `backend/.../analytics/api/ForecastController.java` | controller | request-response | `AnalyticsController.java` | exact |
| `backend/.../analytics/api/ForecastDto.java` | model (record) | — | `RiskScorecardDto.java` | exact |
| `backend/.../analytics/api/ModelType.java` | model (enum) | — | inline in controller | partial |
| `backend/pom.xml` | config | — | existing `<dependencies>` block | exact |
| `backend/.../analytics/ForecastMathHandComputedTest.java` | test (correctness anchor) | — | `RiskMathHandComputedTest.java` | exact |
| `backend/.../analytics/ForecastStructuralTest.java` | test (structural/golden) | — | `RiskCalculatorTest.java` | exact |
| `backend/.../analytics/ForecastFinmathIntegrationTest.java` | test (integration-lite) | — | `RiskMathHandComputedTest.java` | role-match |
| `backend/.../analytics/ForecastControllerIntegrationTest.java` | test (integration) | request-response | `AnalyticsControllerIntegrationTest.java` | exact |
| `frontend/src/api/forecast.ts` | utility (DTO + fetch) | request-response | `frontend/src/api/analytics.ts` | exact |
| `frontend/src/stores/portfolio.ts` (extend) | store | CRUD | existing `portfolio.ts` | exact |
| `frontend/src/components/MonteCarloFanChart.vue` | component | request-response | `PnlChart.vue` + `AllocationChart.vue` | exact (composite) |
| `docs/MODELS.md` | documentation | — | — | no analog |

---

## Pattern Assignments

---

### `backend/.../analytics/service/ForecastService.java` (service, batch)

**Analog:** `backend/src/main/java/com/quantlens/analytics/service/RiskCalculator.java`

**Imports pattern** (lines 1-33):
```java
package com.quantlens.analytics.service;

import com.quantlens.analytics.api.ForecastDto;
import com.quantlens.analytics.api.ModelType;
import com.quantlens.analytics.service.DateValueDto;
import com.quantlens.portfolio.domain.Position;
import com.quantlens.portfolio.domain.PositionRepository;
import org.hipparchus.random.MersenneTwister;
import org.hipparchus.stat.descriptive.DescriptiveStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
// + finmath imports from RESEARCH.md §Core Classes
```

**Service class declaration** (mirrors `RiskCalculator.java` lines 55-74):
```java
@Service
@Transactional(readOnly = true)
public class ForecastService {

    private static final Logger log = LoggerFactory.getLogger(ForecastService.class);

    // Fixed constants — reproducibility requirement from CONTEXT.md
    private static final int    MC_SEED   = 42;
    private static final int    NUM_PATHS = 5000;
    private static final double HESTON_KAPPA = 2.0;
    private static final double HESTON_THETA = 0.04;
    private static final double HESTON_XI    = 0.3;
    private static final double HESTON_RHO   = -0.7;
    private static final double HESTON_V0    = 0.04;
    private static final double JUMP_LAMBDA  = 0.10;
    private static final double JUMP_MU_J    = -0.10;
    private static final double JUMP_SIGMA_J = 0.15;

    private final PositionRepository positionRepository;
    private final RiskCalculator riskCalculator;           // reuse equity-curve helpers
    private final OhlcvBarRepository ohlcvBarRepository;    // REQUIRED for current portfolio value (latest closes)

    public ForecastService(PositionRepository positionRepository,
                           RiskCalculator riskCalculator,
                           OhlcvBarRepository ohlcvBarRepository) {
        this.positionRepository = positionRepository;
        this.riskCalculator = riskCalculator;
        this.ohlcvBarRepository = ohlcvBarRepository;
        // Feller condition guard — fail-fast at startup
        if (2.0 * HESTON_KAPPA * HESTON_THETA <= HESTON_XI * HESTON_XI) {
            throw new IllegalStateException(
                "Heston Feller condition violated: 2κθ ≤ ξ².");
        }
    }
```

**Calibration pattern — reuse RiskCalculator helpers** (mirrors `RiskCalculator.java` lines 84-92, 261-270):
```java
// In ForecastService.forecast(Long portfolioId, ModelType model, int horizonDays):
List<Position> positions = positionRepository.findByPortfolioIdWithSecurity(portfolioId);
List<DateValueDto> curve = riskCalculator.buildEquityCurveLocal(positions);   // package-accessible
double[] dailyLogReturns = RiskCalculator.logReturns(curve);                  // static helper

DescriptiveStatistics stats = new DescriptiveStatistics(dailyLogReturns);
double annualizedMu    = stats.getMean() * 252.0;
double annualizedSigma = stats.getStandardDeviation() * Math.sqrt(252.0);
```

**Current portfolio value pattern** (mirrors `RiskCalculator.java` lines 197-210):
```java
// Mirrors the VaR section of RiskCalculator.computeRiskScorecard
List<Long> secIds = positions.stream()
    .filter(p -> p.getQuantity().signum() > 0)
    .map(p -> p.getSecurity().getId())
    .distinct()
    .collect(Collectors.toList());
Map<Long, BigDecimal> latestClose = latestCloseBySecurityId(secIds);  // extract same private helper
double initialValue = 0.0;
for (Position pos : positions) {
    if (pos.getQuantity().signum() <= 0) continue;
    BigDecimal close = latestClose.get(pos.getSecurity().getId());
    if (close != null) initialValue += pos.getQuantity().multiply(close).doubleValue();
}
```

**Percentile extraction helper** (pure new code, no analog — from RESEARCH.md §Step 5):
```java
private static double[] extractPercentiles(double[] pathValues) {
    double[] sorted = pathValues.clone();
    Arrays.sort(sorted);
    int n = sorted.length;
    return new double[] {
        sorted[clamp((int) Math.ceil(0.05 * n) - 1, 0, n - 1)],
        sorted[clamp((int) Math.ceil(0.25 * n) - 1, 0, n - 1)],
        sorted[clamp((int) Math.ceil(0.50 * n) - 1, 0, n - 1)],
        sorted[clamp((int) Math.ceil(0.75 * n) - 1, 0, n - 1)],
        sorted[clamp((int) Math.ceil(0.95 * n) - 1, 0, n - 1)]
    };
}

private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
```

**Error handling pattern** (mirrors `RiskCalculator.java` lines 93-98):
```java
if (dailyLogReturns.length < 2) {
    throw new IllegalArgumentException(
        "Insufficient data: need at least 2 daily returns for calibration.");
}
```

---

### `backend/.../analytics/api/ForecastController.java` (controller, request-response)

**Analog:** `backend/src/main/java/com/quantlens/analytics/api/AnalyticsController.java`

**Full class declaration pattern** (lines 44-65):
```java
package com.quantlens.analytics.api;

import com.quantlens.analytics.service.ForecastService;
import com.quantlens.portfolio.domain.PortfolioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/portfolio")
public class ForecastController {

    private final PortfolioRepository portfolioRepository;
    private final ForecastService forecastService;

    public ForecastController(PortfolioRepository portfolioRepository,
                              ForecastService forecastService) {
        this.portfolioRepository = portfolioRepository;
        this.forecastService = forecastService;
    }
```

**Endpoint pattern with query params** (extends `AnalyticsController.java` lines 76-81 + adds `@RequestParam`):
```java
/**
 * Returns Monte Carlo percentile bands for the authenticated user's portfolio.
 * model: one of GBM, JUMP_DIFFUSION, HESTON, BOOTSTRAP
 * horizon: trading days to project forward; clamped to [1, 504] (DoS guard — RESEARCH.md §Security)
 */
@GetMapping("/forecast")
@Transactional(readOnly = true)
public ResponseEntity<ForecastDto> getForecast(
        Authentication authentication,
        @RequestParam(defaultValue = "GBM") ModelType model,
        @RequestParam(defaultValue = "252") int horizon) {
    Long portfolioId = resolvePortfolioId(authentication);
    int clampedHorizon = Math.max(1, Math.min(504, horizon));
    return ResponseEntity.ok(forecastService.forecast(portfolioId, model, clampedHorizon));
}
```

**`resolvePortfolioId` — copy verbatim** (lines 141-148):
```java
// COPY VERBATIM from AnalyticsController — do NOT modify logic
private Long resolvePortfolioId(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    String username = authentication.getName();
    return portfolioRepository.findPortfolioIdByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
}
```

**Note on module boundary comment** (lines 19-43 of `AnalyticsController.java`): copy the Javadoc format and the IDOR-prevention comment explaining that portfolioId is NEVER accepted as `@RequestParam` or `@PathVariable`. Adapt to explain the `model` and `horizon` params specifically.

---

### `backend/.../analytics/api/ForecastDto.java` (model/record)

**Analog:** `backend/src/main/java/com/quantlens/analytics/api/RiskScorecardDto.java`

**Record pattern** (lines 19-25):
```java
package com.quantlens.analytics.api;

/**
 * Forecast result for a portfolio over a forward horizon.
 * All band values are {@code double} (chart-ready; no monetary precision needed).
 *
 * @param model       the model used for simulation
 * @param horizonDays number of trading days projected
 * @param p5          5th-percentile path value at each step (length = horizonDays)
 * @param p25         25th-percentile path value at each step
 * @param p50         median (50th percentile) — Ito-correct log-normal median
 * @param p75         75th-percentile path value at each step
 * @param p95         95th-percentile path value at each step
 */
public record ForecastDto(
        ModelType model,
        int       horizonDays,
        double[]  p5,
        double[]  p25,
        double[]  p50,
        double[]  p75,
        double[]  p95
) {}
```

**Style rules from analog:** single-file record, `public record`, all-`double` statistics fields, Javadoc with `@param` per field, no `@JsonProperty` (default Jackson field-name serialization).

---

### `backend/.../analytics/api/ModelType.java` (enum)

**No direct analog** — new enum. Use standard Java `public enum` in same package as DTO records:

```java
package com.quantlens.analytics.api;

/**
 * Stochastic model for Monte Carlo portfolio forecasting.
 * Used as a {@code @RequestParam} in {@link ForecastController#getForecast}.
 * Spring MVC converts the query-param string to this enum by name (case-sensitive).
 * Unknown values produce a 400 Bad Request automatically via {@code MethodArgumentTypeMismatchException}.
 */
public enum ModelType {
    GBM,
    JUMP_DIFFUSION,
    HESTON,
    BOOTSTRAP
}
```

---

### `backend/pom.xml` (config — new dependency)

**Analog:** existing `<dependency>` blocks in `pom.xml`

**Insert inside `<dependencies>`:**
```xml
<!-- Phase 5: Monte Carlo SDE engine (GBM, Merton jump-diffusion, Heston) -->
<!-- commons-math3:3.6.1 is a harmless transitive dep — do NOT add exclusions -->
<dependency>
    <groupId>net.finmath</groupId>
    <artifactId>finmath-lib</artifactId>
    <version>6.1.7</version>
</dependency>
```

**Verification command after adding:**
```
./mvnw dependency:tree -pl backend | grep commons-math3
```
Expected: exactly one entry `commons-math3:3.6.1`.

---

### `backend/.../analytics/ForecastMathHandComputedTest.java` (test — correctness anchor, HC-11)

**Analog:** `backend/src/test/java/com/quantlens/analytics/RiskMathHandComputedTest.java`

**Class-level pattern** (lines 1-28):
```java
package com.quantlens.analytics;

import org.hipparchus.stat.descriptive.DescriptiveStatistics;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Hand-computed correctness tests for GBM Monte Carlo math (HC-11).
 * <p>
 * NO Spring context, NO Testcontainers. Inputs are tiny fixed arrays whose
 * expected outputs are derived externally, NOT by running ForecastService.
 * This breaks circular validation — see RiskMathHandComputedTest for pattern rationale.
 */
class ForecastMathHandComputedTest {
```

**HC-11 test method pattern** (mirrors `RiskMathHandComputedTest.java` lines 72-106 — derivation + assertion structure):
```java
/**
 * HC-11: GBM analytic mean check — Ito-correct drift guard.
 * <p>
 * Derivation (independent of ForecastService):
 * <pre>
 *   S₀ = 100.0, μ = 0.10, σ = 0.20, T = 1 year
 *   GBM analytic mean: E[S_T] = S₀ · exp(μ · T) = 100 · exp(0.10) = 110.517
 *   Ito-correct median: S₀ · exp((μ − σ²/2) · T) = 100 · exp(0.08) = 108.329
 *   If implementation uses μ instead of (μ−σ²/2) in log-space, median > 110.517
 * </pre>
 */
@Test
void gbm_analyticMean_itoCorrect_hc11() {
    // GIVEN: fixed S₀, μ, σ
    double s0    = 100.0;
    double mu    = 0.10;   // annualized
    double sigma = 0.20;   // annualized

    // WHEN: run 5000 paths × 1 year via ForecastService (or directly via finmath)
    // ... (wire actual simulation call here)

    // THEN: hand-derived expected values (NOT from ForecastService output)
    double expectedMean   = s0 * Math.exp(mu * 1.0);          // 110.517
    double expectedMedian = s0 * Math.exp((mu - sigma * sigma / 2.0) * 1.0); // 108.329

    // Verify mean within 1%
    assertThat(actualMean)
        .as("GBM E[S_T] must match analytic S₀·exp(μT) within 1%% (HC-11 Ito guard)")
        .isCloseTo(expectedMean, within(expectedMean * 0.01));
    // Verify median < mean (always true for log-normal; guards against sign flip)
    assertThat(actualMedian)
        .as("GBM median must be < mean for log-normal distribution")
        .isLessThan(actualMean);
    // Verify Ito correction: median ≈ expected Ito median within 0.5%
    assertThat(actualMedian)
        .as("GBM median must match Ito-correct S₀·exp((μ−σ²/2)T) within 0.5%%")
        .isCloseTo(expectedMedian, within(expectedMedian * 0.005));
}
```

**Key conventions from analog:**
- Class-level Javadoc explains "NO Spring context, NO Testcontainers"
- Each test has a `Derivation:` block in Javadoc showing the hand-calculation
- `assertThat(...).as("description").isCloseTo(expected, within(tolerance))`
- `within()` from `org.assertj.core.api.Assertions.within`
- Test method names use `_handComputed_` and end with a `_hcNN` suffix

---

### `backend/.../analytics/ForecastStructuralTest.java` (test — structural/golden)

**Analog:** `backend/src/test/java/com/quantlens/analytics/RiskCalculatorTest.java`

**Class pattern** (lines 1-48):
```java
package com.quantlens.analytics;

import com.quantlens.AbstractPostgresIntegrationTest;
import com.quantlens.analytics.api.ForecastDto;
import com.quantlens.analytics.api.ModelType;
import com.quantlens.analytics.service.ForecastService;
import com.quantlens.portfolio.domain.AppUserRepository;
import com.quantlens.portfolio.domain.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structural and golden-value tests for ForecastService.
 * Extends AbstractPostgresIntegrationTest — uses seeded Testcontainers Postgres.
 * Results are deterministic: fixed seed=42, fixed seeded portfolio data.
 */
@Transactional
class ForecastStructuralTest extends AbstractPostgresIntegrationTest {

    @Autowired private ForecastService forecastService;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private AppUserRepository appUserRepository;
```

**Structural tests pattern** (mirrors `RiskCalculatorTest.java` — assertThat chain + within):
```java
@Test
void percentileBands_areMonotonicallyOrdered_allModels() {
    // p5 ≤ p25 ≤ p50 ≤ p75 ≤ p95 at every time step
    for (ModelType model : ModelType.values()) {
        ForecastDto dto = forecastService.forecast(alicePortfolioId, model, 252);
        for (int t = 0; t < dto.horizonDays(); t++) {
            assertThat(dto.p5()[t]).isLessThanOrEqualTo(dto.p25()[t]);
            assertThat(dto.p25()[t]).isLessThanOrEqualTo(dto.p50()[t]);
            // ... etc.
        }
    }
}

@Test
void bandsWiden_withHorizon_gbm() {
    ForecastDto dto = forecastService.forecast(alicePortfolioId, ModelType.GBM, 252);
    double widthAt1  = dto.p95()[0]  - dto.p5()[0];
    double widthAt252 = dto.p95()[251] - dto.p5()[251];
    assertThat(widthAt252).isGreaterThan(widthAt1);
}

@Test
void reproducibility_fixedSeed_gbm() {
    ForecastDto run1 = forecastService.forecast(alicePortfolioId, ModelType.GBM, 252);
    ForecastDto run2 = forecastService.forecast(alicePortfolioId, ModelType.GBM, 252);
    assertThat(run1.p50()).isEqualTo(run2.p50());  // byte-identical arrays
}
```

---

### `backend/.../analytics/ForecastFinmathIntegrationTest.java` (test — integration-lite)

**Analog:** `RiskMathHandComputedTest.java` (no-Spring-context unit test) + finmath API from RESEARCH.md

```java
package com.quantlens.analytics;

// NO Spring imports — plain JUnit 5
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test: finmath-lib model instantiation + path generation runs without exception.
 * NO Spring context, NO Testcontainers — just exercises the finmath API.
 * Fails at compile time if finmath is not in pom.xml (Wave 0 gate).
 */
class ForecastFinmathIntegrationTest {

    @Test
    void gbm_instantiatesAndGeneratesPaths_noException() throws Exception {
        // TimeDiscretizationFromArray, BrownianMotionFromMersenneRandomNumbers,
        // BlackScholesModel, EulerSchemeFromProcessModel, MonteCarloAssetModel
        // — verify getAssetValue(1, 0).getRealizations().length == 5000
        // Full snippet in RESEARCH.md §Complete GBM Setup
    }

    @Test
    void heston_fellerConditionSatisfied_instantiatesOk() throws Exception {
        // Assert: 2*KAPPA*THETA > XI*XI before building HestonModel
    }
}
```

---

### `backend/.../analytics/ForecastControllerIntegrationTest.java` (test — integration)

**Analog:** `backend/src/test/java/com/quantlens/analytics/AnalyticsControllerIntegrationTest.java`

**Full class header pattern** (lines 1-37):
```java
package com.quantlens.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlens.AbstractPostgresIntegrationTest;
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

class ForecastControllerIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
```

**Auth test pattern** (mirrors `AnalyticsControllerIntegrationTest.java` lines 54-64):
```java
@Test
void forecast_unauthenticated_returns401() {
    ResponseEntity<String> response = restTemplate.exchange(
        "/api/portfolio/forecast?model=GBM",
        HttpMethod.GET,
        HttpEntity.EMPTY,
        String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
}
```

**Authenticated test pattern** (mirrors lines 75-107 with JSON-shape verification):
```java
@Test
void forecast_gbm_returns200_withCorrectShape() throws Exception {
    String cookie = loginAndGetSessionCookie("alice");
    ResponseEntity<String> response = authenticatedGet(
        "/api/portfolio/forecast?model=GBM&horizon=252", cookie);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("model").asText()).isEqualTo("GBM");
    assertThat(body.path("horizonDays").asInt()).isEqualTo(252);
    assertThat(body.path("p50").isArray()).isTrue();
    assertThat(body.path("p50").size()).isEqualTo(252);
}
```

**`loginAndGetSessionCookie` + `authenticatedGet` helpers** — copy verbatim from `AnalyticsControllerIntegrationTest.java` lines 214-238. These are identical across all controller integration tests.

---

### `frontend/src/api/forecast.ts` (utility — DTO + fetch)

**Analog:** `frontend/src/api/analytics.ts`

**DTO-only module pattern** (lines 1-10 of `analytics.ts`):
```typescript
// IMPORTANT: Do NOT set axios.defaults or register interceptors here.
// api/auth.ts already registers CSRF and 401 interceptors on the shared axios singleton.
// This module declares DTO interfaces only; fetch calls live in the Pinia portfolio store.

import axios from 'axios'

export type ModelType = 'GBM' | 'JUMP_DIFFUSION' | 'HESTON' | 'BOOTSTRAP'

export interface ForecastDto {
  model:       ModelType
  horizonDays: number
  p5:          number[]
  p25:         number[]
  p50:         number[]
  p75:         number[]
  p95:         number[]
}
```

**No standalone fetch function** — fetch lives in the store (same pattern as `analytics.ts` which declares interfaces only; the actual `axios.get` calls are in `portfolio.ts`).

---

### `frontend/src/stores/portfolio.ts` (extend — add forecast state)

**Analog:** existing `frontend/src/stores/portfolio.ts` — follow the Phase-4 `risk`/`correlation`/`attribution`/`pairs` addition pattern exactly.

**New import line** (after existing analytics imports, line 17):
```typescript
import type { ForecastDto, ModelType } from '../api/forecast'
```

**New state declaration** (after `pairs`, around line 51):
```typescript
const forecast = asyncState<ForecastDto>(null)
```

**New fetch action** (follows `fetchPairs` pattern, lines 224-237):
```typescript
/**
 * Fetch Monte Carlo forecast bands.
 * model: stochastic model selector — switching model re-fetches.
 * horizon: trading days to project (default 252 = 1 year).
 * version: race-guard — see fetchHoldings for semantics.
 */
async function fetchForecast(
    model: ModelType = 'GBM',
    horizon = 252,
    version?: number
): Promise<void> {
  forecast.loading = true
  forecast.error = null
  try {
    const { data } = await axios.get<ForecastDto>(
      '/api/portfolio/forecast',
      { params: { model, horizon } }
    )
    if (version !== undefined && version !== refreshVersion) return
    forecast.data = data
  } catch (e: any) {
    if (version !== undefined && version !== refreshVersion) return
    forecast.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to load forecast'
  } finally {
    forecast.loading = false
  }
}
```

**`refreshAll` addition** (after `fetchPairs(myVersion)` on line 264):
```typescript
fetchForecast('GBM', 252, myVersion),
```

**`$reset` addition** (after `pairs` reset line 283):
```typescript
forecast.data = null; forecast.loading = false; forecast.error = null
```

**`return` block** (after `fetchPairs,` around line 307):
```typescript
forecast,
fetchForecast,
```

---

### `frontend/src/components/MonteCarloFanChart.vue` (component, request-response)

**Analog — structural scaffold:** `frontend/src/components/PnlChart.vue` (ECharts v-chart, loading/error/empty states, `<figure>` layout, scoped CSS tokens)

**Analog — model selector toggle:** `frontend/src/components/AllocationChart.vue` (`.chart-toggle` / `.toggle-btn` segmented button pattern, `chartType` ref → `option` computed)

**Script setup — imports pattern** (mirrors `PnlChart.vue` lines 1-6 + `AllocationChart.vue` lines 1-6):
```typescript
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import VChart from 'vue-echarts'
import type { EChartsOption } from 'echarts/types/dist/shared'
import type { ForecastDto } from '@/api/forecast'
import type { ModelType } from '@/api/forecast'
// FAN_COLORS — read CSS tokens via getComputedStyle at module init (see chart-colors.ts pattern)
```

**Props pattern** (mirrors `PnlChart.vue` lines 11-15):
```typescript
const props = defineProps<{
  forecast: ForecastDto | null
  loading:  boolean
  error:    string | null
}>()

const emit = defineEmits<{ retry: []; 'update:model': [ModelType] }>()

// Model selector — drives re-fetch (AllocationChart.vue lines 20-21 pattern)
const selectedModel = ref<ModelType>('GBM')
watch(selectedModel, (model) => {
  emit('update:model', model)
})
```

**ECharts option computed** (mirrors `PnlChart.vue` lines 19-92 structure, fan bands from RESEARCH.md §Band-Difference Trick):
```typescript
const option = computed<EChartsOption>(() => {
  if (!props.forecast?.p50?.length) return {}

  const { p5, p25, p50, p75, p95 } = props.forecast
  const steps = p50.map((_, i) => `Day ${i + 1}`)

  return {
    xAxis: { type: 'category', data: steps, axisLabel: { ... } },
    yAxis: {
      type: 'value',
      axisLabel: {
        formatter: (v: number) =>
          new Intl.NumberFormat('en-US', {
            style: 'currency', currency: 'USD', notation: 'compact', maximumFractionDigits: 1
          }).format(v),
      },
    },
    // NOTE: ECharts renders on <canvas> and CANNOT resolve CSS custom properties at paint time.
    // Resolve the fan tokens ONCE via getComputedStyle into a FAN_COLORS object (see chart-colors.ts / 05-03 Task 1)
    // and use FAN_COLORS.* below — do NOT pass 'var(--color-fan-*)' strings (they render transparent/black on canvas).
    series: [
      // Base (p5 floor, invisible fill)
      { type: 'line', data: p5, stack: 'fan', symbol: 'none',
        lineStyle: { opacity: 0 }, areaStyle: { color: 'transparent' } },
      // Band p5→p25 (outer)
      { type: 'line', data: p25.map((v, i) => v - p5[i]), stack: 'fan',
        symbol: 'none', lineStyle: { opacity: 0 },
        areaStyle: { color: FAN_COLORS.bandOuter } },
      // Band p25→p75 (IQR, more opaque)
      { type: 'line', data: p75.map((v, i) => v - p25[i]), stack: 'fan',
        symbol: 'none', lineStyle: { opacity: 0 },
        areaStyle: { color: FAN_COLORS.bandInner } },
      // Band p75→p95 (outer)
      { type: 'line', data: p95.map((v, i) => v - p75[i]), stack: 'fan',
        symbol: 'none', lineStyle: { opacity: 0 },
        areaStyle: { color: FAN_COLORS.bandOuter } },
      // Median line (NOT stacked — absolute p50 values)
      { type: 'line', name: 'Median (p50)', data: p50,
        symbol: 'none',
        lineStyle: { color: FAN_COLORS.median, width: 2 } },
    ],
    tooltip: { trigger: 'axis', formatter: (params: any) => { ... } },
  }
})
```

**Template pattern** (mirrors `AllocationChart.vue` lines 144-199):
```vue
<template>
  <figure class="chart-panel" :aria-busy="loading" aria-label="Monte Carlo fan chart">
    <!-- Model selector toggle (AllocationChart.vue .chart-toggle pattern, lines 152-171) -->
    <div v-if="!loading && !error && props.forecast?.p50?.length"
         class="chart-toggle" role="group" aria-label="Forecast model">
      <button v-for="m in (['GBM','JUMP_DIFFUSION','HESTON','BOOTSTRAP'] as ModelType[])"
              :key="m"
              :class="['toggle-btn', { active: selectedModel === m }]"
              @click="selectedModel = m"
              :aria-pressed="selectedModel === m">
        {{ m }}
      </button>
    </div>

    <!-- Loading skeleton (PnlChart.vue lines 102-103) -->
    <div v-if="loading" class="skeleton" style="height: 320px" aria-hidden="true" />

    <!-- Error state (PnlChart.vue lines 105-109 — T-03-08: static copy only) -->
    <div v-else-if="error" class="chart-error" role="alert">
      <span>Failed to load forecast. Check your connection and try again.</span>
      <button class="retry-btn" @click="emit('retry')">Retry</button>
    </div>

    <!-- Empty state -->
    <div v-else-if="!props.forecast?.p50?.length" class="chart-empty">
      No forecast data available.
    </div>

    <!-- Chart — NO :theme prop (THEME_KEY in App.vue propagates, PnlChart.vue line 116) -->
    <v-chart v-else class="chart" :option="option" :autoresize="true" />

    <figcaption class="sr-only">
      Monte Carlo fan chart. Shows projected portfolio value percentile bands (p5, p25, p50, p75, p95)
      over the forecast horizon. Select a model with the toggle buttons above.
    </figcaption>
  </figure>
</template>
```

**Scoped CSS pattern** (copy from `PnlChart.vue` lines 130-215 + add `.toggle-btn` from `AllocationChart.vue` lines 233-261):
```css
/* All colors/spacing/radius via CSS custom properties — no hardcoded hex */
.chart-panel { /* same as PnlChart.vue */ }
.chart { height: 320px; width: 100%; }
/* Fan-chart CSS token consumption — defined in style.css lines 65-68 */
/* --color-fan-p50, --color-fan-band-1, --color-fan-band-2 used directly in series areaStyle */
/* Heston disclaimer text */
.heston-disclaimer {
  font-size: 11px;
  color: var(--color-text-muted);
  padding: var(--space-xs) var(--space-md);
}
```

---

### `frontend/src/views/DashboardView.vue` (modify — replace SlotPlaceholder)

**Location of the placeholder to replace** (line 203-205):
```vue
<!-- Row 5: Monte Carlo slot (col 12) -->
<div class="col-12">
  <SlotPlaceholder label="Monte Carlo Forecast — Phase 5" minHeight="320px" />
</div>
```

**Replace with** (following the attribution/pairs pattern from lines 185-199):
```vue
<!-- Row 5: Monte Carlo fan chart (col 12) — Phase 5 -->
<div class="col-12">
  <MonteCarloFanChart
    :forecast="portfolioStore.forecast.data"
    :loading="portfolioStore.forecast.loading"
    :error="portfolioStore.forecast.error"
    @retry="retryForecast"
    @update:model="(m) => portfolioStore.fetchForecast(m)"
  />
</div>
```

**New import at top of `<script setup>`:**
```typescript
import MonteCarloFanChart from '../components/MonteCarloFanChart.vue'
```

**New handler function** (after `retryPairs` on line 94):
```typescript
function retryForecast(): void { portfolioStore.fetchForecast() }
```

---

## Shared Patterns

### Authentication / Principal Scoping
**Source:** `AnalyticsController.java` lines 141-148
**Apply to:** `ForecastController.java`
```java
// COPY VERBATIM — resolvePortfolioId
private Long resolvePortfolioId(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    String username = authentication.getName();
    return portfolioRepository.findPortfolioIdByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
}
```

### asyncState Factory
**Source:** `frontend/src/stores/portfolio.ts` lines 21-31
**Apply to:** `portfolio.ts` (forecast state addition)
```typescript
interface AsyncState<T> {
  data: T | null
  loading: boolean
  error: string | null
}

function asyncState<T>(init: T | null = null): AsyncState<T> {
  // Cast required: reactive() unwraps nested refs (UnwrapRef<T> vs T)
  return reactive({ data: init, loading: false, error: null }) as AsyncState<T>
}
```

### Race-guard version pattern
**Source:** `frontend/src/stores/portfolio.ts` lines 63-76
**Apply to:** `fetchForecast` action
```typescript
async function fetchX(version?: number): Promise<void> {
  x.loading = true
  x.error = null
  try {
    const { data } = await axios.get<XDto>('/api/portfolio/x')
    if (version !== undefined && version !== refreshVersion) return
    x.data = data
  } catch (e: any) {
    if (version !== undefined && version !== refreshVersion) return
    x.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to load x'
  } finally {
    x.loading = false
  }
}
```

### ECharts No-Theme-Prop Rule
**Source:** `PnlChart.vue` line 116 comment + `AllocationChart.vue` line 188 comment
**Apply to:** `MonteCarloFanChart.vue`
```vue
<!-- NO :theme prop — THEME_KEY provided in App.vue propagates automatically -->
<v-chart class="chart" :option="option" :autoresize="true" />
```

### Error State Static Copy (T-03-08)
**Source:** `PnlChart.vue` lines 105-109
**Apply to:** `MonteCarloFanChart.vue` error state
```vue
<div v-else-if="error" class="chart-error" role="alert">
  <span>Failed to load [resource]. Check your connection and try again.</span>
  <button class="retry-btn" @click="emit('retry')">Retry</button>
</div>
```

### Integration Test Auth Helpers
**Source:** `AnalyticsControllerIntegrationTest.java` lines 214-238
**Apply to:** `ForecastControllerIntegrationTest.java` — copy both helpers verbatim:
```java
private String loginAndGetSessionCookie(String username) { ... }
private ResponseEntity<String> authenticatedGet(String path, String sessionCookie) { ... }
```

### Fan-Chart CSS Tokens (from `style.css` lines 65-68)
**Apply to:** `MonteCarloFanChart.vue` series `areaStyle` — use CSS vars directly in the option object, not hardcoded rgba:
```
--color-fan-p50:    #0ea5e9
--color-fan-band-1: rgba(14, 165, 233, 0.25)   /* IQR band p25-p75 */
--color-fan-band-2: rgba(14, 165, 233, 0.12)   /* outer bands p5-p25, p75-p95 */
```
**Note:** ECharts renders on `<canvas>`, so CSS vars cannot be resolved directly at paint time. Read them once at module init via `getComputedStyle(document.documentElement)` and export as constants (follow the `CHART_COLORS` pattern in `frontend/src/plugins/chart-colors.ts`). Create a `FAN_COLORS` export alongside `CHART_COLORS` or inline the resolved values into `MonteCarloFanChart.vue`'s script setup.

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `docs/MODELS.md` | documentation | — | No other model-rationale docs exist in the repo. Write from scratch using the RESEARCH.md §MODELS.md Outline as the structural guide. |
| `backend/.../api/ModelType.java` | model (enum) | — | No existing enums in analytics package. Standard Java `public enum` in same package as DTOs. |

---

## Key Accessor Notes

- `RiskCalculator.buildEquityCurveLocal(positions)` — **instance method** (not static); inject `RiskCalculator` into `ForecastService` and call `riskCalculator.buildEquityCurveLocal(positions)`.
- `RiskCalculator.logReturns(curve)` — **static** helper (line 261); call as `RiskCalculator.logReturns(curve)`.
- `PositionRepository.findByPortfolioIdWithSecurity(portfolioId)` — used in both `RiskCalculator.computeRiskScorecard` (line 86) and `ForecastService`; same call signature.
- `OhlcvBarRepository.findLatestBarBySecurityIds(securityIds)` — used in `RiskCalculator` lines 386-393 for current portfolio value; extract into a shared helper or duplicate the private `latestCloseBySecurityId` pattern.
- `portfolioStore.forecast` — accessed **whole** from components (never destructured — Pitfall 5, `portfolio.ts` line 20 comment).
- `ForecastDto` accessors in Java: `dto.model()`, `dto.horizonDays()`, `dto.p5()`, `dto.p25()`, `dto.p50()`, `dto.p75()`, `dto.p95()` — standard Java record accessor names.

---

## Metadata

**Analog search scope:** `backend/src/main/java/com/quantlens/analytics/`, `backend/src/test/java/com/quantlens/analytics/`, `frontend/src/components/`, `frontend/src/stores/`, `frontend/src/api/`, `frontend/src/plugins/`, `frontend/src/views/`
**Files read:** 13 source files + CONTEXT.md + RESEARCH.md
**Pattern extraction date:** 2026-06-08
