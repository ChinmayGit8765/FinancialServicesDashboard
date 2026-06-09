package com.quantlens.ai.seed;

import com.quantlens.seed.SeedLog;
import com.quantlens.seed.SeedLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Idempotent seeder for AI fixture content in the {@code ai_seed_content} table.
 *
 * <p>{@code @Order(2)} ensures this runner executes after {@code SeedRunner @Order(1)}
 * which seeds the base portfolio data (securities, users, positions) that the AI
 * seed content references.
 *
 * <h2>Idempotence</h2>
 * Guarded by a {@code seed_log} row with {@code id="ai-v2"}. The completion row is
 * written at the END of the transaction — if the JVM crashes mid-seed, the partial
 * work rolls back and re-runs cleanly on the next restart.
 *
 * <h2>Upsert strategy (ai-v2)</h2>
 * Rows are upserted (updated if they already exist from ai-v1, inserted if new).
 * This ensures corrected content takes effect on existing databases that ran ai-v1.
 * The ai-v1 seed_log row is left intact so partial-v1 detection still works on
 * fresh databases that never ran v1; ai-v2 only checks for its own completion marker.
 *
 * <h2>Seeded universe</h2>
 * One {@code EXPLAIN_POSITION} row per ticker in the seeded portfolio universe (13 tickers),
 * and one {@code DAILY_COMMENTARY} row per persona (3 rows). Total: 16 rows.
 *
 * <h2>EXPLAIN_POSITION content — persona-neutral (WR-06)</h2>
 * EXPLAIN_POSITION content is keyed by TICKER and shared across all personas (all three
 * demo users can hold the same ticker). In ai-v1, the narratives baked in persona-specific
 * share counts ("approximately 50 shares") and portfolio style ("growth-oriented portfolio")
 * which contradicted each persona's actual holdings. ai-v2 content is persona-neutral:
 * it describes the company, sector role, and investment characteristics without referencing
 * specific share counts or a portfolio style.
 *
 * <h2>DAILY_COMMENTARY storage convention</h2>
 * The raw {@code content} string encodes structured commentary in a parseable format
 * for {@code CommentaryService} (Plan 06-03):
 * <ul>
 *   <li><strong>Line 1:</strong> The headline (single sentence, no prefix)</li>
 *   <li><strong>Blank line:</strong> Separator between headline and body</li>
 *   <li><strong>Body paragraph:</strong> One or two prose sentences</li>
 *   <li><strong>Blank line:</strong> Separator between body and bullets</li>
 *   <li><strong>Bullet lines:</strong> Each prefixed with {@code "- "}</li>
 * </ul>
 */
@Component
@Order(2)
public class AiSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AiSeedRunner.class);

    private static final String AI_SEED_VERSION = "ai-v2";
    private static final String EXPLAIN = "EXPLAIN_POSITION";
    private static final String COMMENTARY = "DAILY_COMMENTARY";

    private final AiSeedContentRepository aiSeedContentRepository;
    private final SeedLogRepository seedLogRepository;

    public AiSeedRunner(AiSeedContentRepository aiSeedContentRepository,
                        SeedLogRepository seedLogRepository) {
        this.aiSeedContentRepository = aiSeedContentRepository;
        this.seedLogRepository = seedLogRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean alreadyDone = seedLogRepository.findById(AI_SEED_VERSION)
                .map(SeedLog::isCompleted)
                .orElse(false);
        if (alreadyDone) {
            log.info("AiSeedRunner: seed_log {} already completed — skipping", AI_SEED_VERSION);
            return;
        }

        log.info("AiSeedRunner: starting AI seed content (seed_log {} not yet completed)...",
                AI_SEED_VERSION);

        List<AiSeedContent> fixtures = buildFixtures();
        int inserted = 0;
        int updated  = 0;
        for (AiSeedContent fixture : fixtures) {
            // Upsert: update existing rows (from ai-v1), insert new ones.
            // This ensures corrected content takes effect on existing databases.
            Optional<AiSeedContent> existing = aiSeedContentRepository
                    .findByTypeAndSubjectId(fixture.getType(), fixture.getSubjectId());
            if (existing.isPresent()) {
                existing.get().setContent(fixture.getContent());
                aiSeedContentRepository.save(existing.get());
                updated++;
            } else {
                aiSeedContentRepository.save(fixture);
                inserted++;
            }
        }
        log.info("AiSeedRunner: seed complete — {} inserted, {} updated ({} total defined)",
                inserted, updated, fixtures.size());

        // Mark seed complete — written LAST so a mid-seed failure rolls back (T-06-07)
        SeedLog seedLog = new SeedLog(AI_SEED_VERSION);
        seedLog.setCompleted(true);
        seedLog.setCompletedAt(LocalDateTime.now());
        seedLogRepository.save(seedLog);

        log.info("AiSeedRunner: seed_log {} marked completed", AI_SEED_VERSION);
    }

    // ── fixture authoring ─────────────────────────────────────────────────────
    //
    // WR-06: EXPLAIN_POSITION content is persona-neutral (ai-v2).
    // No specific share counts, no portfolio style references.
    // The ticker symbol is retained in the content because AiSeedRunnerTest asserts
    // that each row references its ticker — this is correct and expected.

    private List<AiSeedContent> buildFixtures() {
        return List.of(
            // ── EXPLAIN_POSITION: Technology sector ───────────────────────────

            new AiSeedContent(EXPLAIN, "AAPL",
                "Apple Inc. (AAPL) holds a dominant position in the global smartphone and services " +
                "ecosystem, with its tightly integrated hardware-software platform underpinning " +
                "durable pricing power and high customer retention across its installed base.\n\n" +
                "The company's Services segment — encompassing the App Store, iCloud, and Apple " +
                "One bundles — provides a recurring revenue layer that partially decouples earnings " +
                "from hardware unit cycles. Apple's strong free cash flow generation and pristine " +
                "balance sheet have historically supported consistent capital return programs.\n\n" +
                "Key risks to monitor include regulatory scrutiny of the App Store model, " +
                "concentration risk in the consumer electronics space, and macroeconomic " +
                "sensitivity in the premium handset segment. AAPL's combination of balance sheet " +
                "strength and innovation cadence makes it a foundational Technology sector holding."),

            new AiSeedContent(EXPLAIN, "MSFT",
                "Microsoft Corporation (MSFT) is a Technology sector leader that has " +
                "systematically transitioned from a legacy software licenser to a " +
                "recurring-revenue cloud business anchored by Azure.\n\n" +
                "Azure's rapid expansion in enterprise workloads, combined with Microsoft's " +
                "early and deep integration of AI capabilities via its partnership with OpenAI, " +
                "appears to be reinforcing a durable competitive moat. The company has " +
                "historically demonstrated an ability to cross-sell across its productivity, " +
                "developer tooling, and cloud infrastructure pillars.\n\n" +
                "MSFT offers a lower-volatility path to technology upside relative to pure-play " +
                "AI names. Its consistent free cash flow generation and investment-grade balance " +
                "sheet provide a degree of defensive resilience uncommon among high-growth " +
                "Technology holdings, making it suitable across a range of portfolio mandates."),

            new AiSeedContent(EXPLAIN, "NVDA",
                "NVIDIA Corporation (NVDA) has evolved from a graphics processing unit supplier " +
                "into the de facto platform for large-scale AI model training and inference, " +
                "positioned at the centre of the accelerated computing megatrend.\n\n" +
                "The company's H-series and Blackwell GPU architectures appear to be sustaining " +
                "substantial pricing power as hyperscalers and sovereign AI initiatives compete " +
                "for compute capacity. Data center revenue has historically demonstrated " +
                "exponential growth trajectories that have consistently exceeded consensus estimates.\n\n" +
                "Investors should note that NVDA carries elevated valuation multiples and " +
                "meaningful volatility relative to broader Technology benchmarks. The stock's " +
                "high beta reflects its leverage to AI capital expenditure cycles, making " +
                "position sizing and multi-year investment horizons important considerations."),

            new AiSeedContent(EXPLAIN, "AMZN",
                "Amazon.com Inc. (AMZN) is a multi-vector Technology holding with three " +
                "distinct business engines: e-commerce marketplace infrastructure, Amazon Web " +
                "Services cloud computing, and a rapidly growing digital advertising segment.\n\n" +
                "AWS appears well-positioned as a foundational cloud provider benefiting from " +
                "enterprise digital transformation spending, while the advertising business has " +
                "historically demonstrated superior unit economics as first-party retail data " +
                "becomes increasingly valuable in a privacy-centric marketing landscape. " +
                "The company's logistics network density creates a durable barrier to entry " +
                "for would-be competitors in the core e-commerce segment.\n\n" +
                "AMZN's diversified earnings profile may provide portfolio resilience during " +
                "periods of sector rotation away from pure-play software names. Investors " +
                "should monitor AWS growth trajectory, advertising margin trends, and capital " +
                "allocation discipline in the retail segment."),

            new AiSeedContent(EXPLAIN, "TSLA",
                "Tesla Inc. (TSLA) occupies a unique intersection of electric vehicle " +
                "manufacturing, energy storage, and autonomous driving technology development, " +
                "classified under the Automotive sector.\n\n" +
                "The company has historically demonstrated an ability to rapidly scale " +
                "manufacturing capacity and reduce per-unit costs, supporting a long-term " +
                "gross margin expansion thesis as the EV market matures. Full Self-Driving " +
                "software monetization and the energy storage segment (Powerwall, Megapack) " +
                "represent optionality that appears underappreciated in traditional commodity " +
                "vehicle sector comparisons.\n\n" +
                "TSLA carries elevated beta and a distinctive idiosyncratic risk profile driven " +
                "by technology execution, competitive intensity in the EV market, and sentiment " +
                "around autonomous driving timelines. Investors should treat the horizon as " +
                "multi-year and plan for near-term price volatility."),

            // ── EXPLAIN_POSITION: Financials sector ──────────────────────────

            new AiSeedContent(EXPLAIN, "JPM",
                "JPMorgan Chase & Co. (JPM) is the largest U.S. bank by assets, offering broad " +
                "exposure to consumer banking, investment banking, commercial lending, and asset " +
                "management — providing a naturally diversified financial-sector allocation.\n\n" +
                "The firm has historically demonstrated superior returns on equity relative to " +
                "money-center bank peers, supported by disciplined risk management and a " +
                "fortress balance sheet. Rising interest rate environments have historically " +
                "benefited JPM's net interest income, while the investment banking and trading " +
                "segments provide counter-cyclical revenue during market volatility.\n\n" +
                "JPMorgan's dividend track record and capital return consistency make it a " +
                "compelling Financials sector holding across income and growth-oriented mandates. " +
                "Key risks include credit cycle deterioration, regulatory capital requirements, " +
                "and macro-sensitivity of investment banking deal flow."),

            new AiSeedContent(EXPLAIN, "BAC",
                "Bank of America Corporation (BAC) operates one of the largest U.S. retail " +
                "deposit franchises, giving it significant sensitivity to interest rate " +
                "movements in both net interest income and deposit repricing dynamics.\n\n" +
                "The bank's Merrill Lynch wealth management platform and BofA Securities " +
                "investment banking division diversify its revenue base beyond net interest " +
                "income, while the vast consumer deposit base has historically provided " +
                "low-cost funding advantages during credit cycles. BAC has demonstrated " +
                "meaningful progress on expense discipline and digital banking adoption " +
                "over recent years.\n\n" +
                "BAC's dividend yield and ongoing share repurchase activity provide total return " +
                "components relevant to income-oriented investors, while the rate sensitivity " +
                "offers leverage to monetary policy shifts. Investors should monitor credit " +
                "quality metrics and the pace of deposit repricing in rising rate environments."),

            // ── EXPLAIN_POSITION: Energy sector ──────────────────────────────

            new AiSeedContent(EXPLAIN, "XOM",
                "Exxon Mobil Corporation (XOM) is a global integrated oil and gas operator " +
                "spanning upstream exploration, downstream refining, and chemical manufacturing, " +
                "providing broad exposure to the Energy sector's commodity cycle.\n\n" +
                "ExxonMobil's Permian Basin and Guyana offshore assets appear to offer " +
                "competitive breakeven economics relative to the global E&P peer group, " +
                "supporting free cash flow generation across a range of commodity price " +
                "scenarios. The company has historically prioritized dividend sustainability " +
                "through commodity cycles, with a multi-decade record of annual dividend growth.\n\n" +
                "Investors should remain cognizant of energy transition risk and the potential " +
                "for commodity price volatility to affect near-term earnings. XOM's scale, " +
                "balance sheet strength, and dividend history make it a defensible Energy " +
                "allocation for portfolios seeking current yield and commodity exposure."),

            new AiSeedContent(EXPLAIN, "CVX",
                "Chevron Corporation (CVX) is a major integrated Energy sector company with " +
                "upstream portfolio concentration in the Permian Basin and legacy deepwater " +
                "Gulf of Mexico assets, with international exposure through the Tengizchevroil " +
                "joint venture in Kazakhstan.\n\n" +
                "The company has historically maintained a lower leverage profile than many " +
                "E&P peers, supporting dividend consistency even during extended commodity " +
                "downturns. Chevron's flexible capital allocation framework — which adjusts " +
                "capex in response to oil price cycles — appears to provide a degree of " +
                "earnings resilience that income-oriented investors tend to value.\n\n" +
                "XOM and CVX together represent a diversified Energy position with distinct " +
                "geographic and operational characteristics, reducing single-company operational " +
                "risk while maintaining sector-level dividend income exposure."),

            // ── EXPLAIN_POSITION: Consumer Staples sector ────────────────────

            new AiSeedContent(EXPLAIN, "PG",
                "Procter & Gamble Co. (PG) maintains a global portfolio of consumer brands " +
                "spanning fabric care, personal care, and home care categories that has " +
                "historically demonstrated defensive earnings characteristics during economic " +
                "contractions.\n\n" +
                "The company's pricing power, supported by strong brand equity and consumer " +
                "loyalty across commodity product categories, has allowed it to sustain real " +
                "revenue growth through inflationary environments. P&G has grown its dividend " +
                "annually for over six decades, cementing its status as a Dividend King — a " +
                "characteristic valued across income and defensive portfolio mandates alike.\n\n" +
                "PG's low beta and its tendency to exhibit negative correlation to cyclical " +
                "sectors makes it a natural portfolio volatility dampener, supporting total " +
                "return stability during periods of broad market stress."),

            new AiSeedContent(EXPLAIN, "KO",
                "The Coca-Cola Company (KO) operates a global beverage distribution network " +
                "with a brand portfolio spanning over 500 brands that provides durable revenue " +
                "streams relatively insensitive to macroeconomic cycles.\n\n" +
                "The company's asset-light business model — having refranchised the majority " +
                "of its bottling operations — has historically generated high returns on " +
                "invested capital and strong free cash flow conversion relative to revenue. " +
                "KO has raised its dividend annually for over 60 consecutive years, a record " +
                "that is particularly relevant for investors focused on income sustainability.\n\n" +
                "The combination of brand durability, pricing power, and dividend reliability " +
                "positions KO as a foundational Consumer Staples holding. The defensive sector " +
                "characteristics provide return smoothing during periods of broad market " +
                "volatility, complementing cyclical equity exposures."),

            new AiSeedContent(EXPLAIN, "WMT",
                "Walmart Inc. (WMT) is a Consumer Staples company representing broad exposure " +
                "to essential consumer spending and the ongoing transformation of omnichannel " +
                "retail. Walmart's scale — over 10,500 stores globally — provides purchasing " +
                "leverage and logistical capabilities that are difficult for competitors to replicate.\n\n" +
                "The company has been investing heavily in its e-commerce capabilities and " +
                "advertising network, which appears to be generating a higher-margin revenue " +
                "stream that supplements the historically thin-margin core retail business. " +
                "Walmart+ membership and the growing Walmart Connect advertising platform may " +
                "represent meaningful earnings drivers not yet fully reflected in traditional " +
                "retail earnings models.\n\n" +
                "Consumer spending on groceries and essential goods — Walmart's core business " +
                "— has historically shown strong resilience during economic downturns. WMT " +
                "offers a combination of dividend income and defensive earnings quality across " +
                "income-oriented and defensively-positioned portfolio mandates."),

            // ── EXPLAIN_POSITION: Healthcare sector ──────────────────────────

            new AiSeedContent(EXPLAIN, "JNJ",
                "Johnson & Johnson (JNJ) is a Healthcare sector company operating across " +
                "MedTech and pharmaceutical segments following the separation of its Consumer " +
                "Health business (Kenvue), providing exposure to both medical devices and " +
                "pharmaceutical innovation.\n\n" +
                "The company's pharmaceutical pipeline — particularly oncology and immunology — " +
                "appears well-positioned to sustain top-line growth as legacy blockbuster " +
                "products face biosimilar competition. JNJ's MedTech segment, encompassing " +
                "orthopedic implants and surgical robotics, benefits from long-term " +
                "demographic tailwinds tied to an aging global population.\n\n" +
                "J&J has maintained Dividend Aristocrat status for decades, making it a " +
                "natural Healthcare sector holding for portfolios seeking defensive exposure " +
                "with a stable income contribution. Regulatory and litigation risk should be " +
                "monitored as ongoing factors affecting the investment thesis."),

            // ── DAILY_COMMENTARY per persona ──────────────────────────────────
            // DAILY_COMMENTARY content is intentionally persona-specific — it describes the
            // user's own portfolio behaviour for the week. Unlike EXPLAIN_POSITION (shared
            // by ticker), each commentary row belongs to a single persona.

            new AiSeedContent(COMMENTARY, "GROWTH",
                "Technology and AI infrastructure momentum accelerated this week, led by continued " +
                "strength in NVDA and MSFT as enterprise AI spending shows no signs of deceleration.\n\n" +
                "Your growth portfolio's concentrated Technology exposure has been well-rewarded " +
                "this week, with AI-linked positions driving the majority of portfolio appreciation. " +
                "NVDA's data center segment continues to generate upside surprises as hyperscalers " +
                "compete aggressively for GPU capacity, while MSFT's Azure AI integration is " +
                "beginning to reflect in accelerating enterprise cloud attach rates.\n\n" +
                "- NVDA: Data center bookings remain robust; pricing power in H-series and " +
                  "Blackwell GPUs appears sustained through current capacity constraints\n" +
                "- AAPL: Services revenue growth outpacing hardware units; operating margins " +
                  "holding near record levels despite FX headwinds\n" +
                "- AMZN: AWS revenue reacceleration trajectory intact; advertising segment " +
                  "delivering high-margin contribution above Street estimates\n" +
                "- TSLA: Near-term delivery volatility creates tactical noise; the FSD " +
                  "monetization thesis remains the key long-term value driver to monitor"),

            new AiSeedContent(COMMENTARY, "INCOME",
                "Dividend income streams remained resilient this week as financial sector " +
                "earnings and energy free cash flow continue to support the portfolio's " +
                "yield-generation objectives.\n\n" +
                "Your income portfolio's diversification across Financials, Energy, and " +
                "Consumer Staples provided stability this week as the broader market absorbed " +
                "macroeconomic uncertainty. The banking positions (JPM, BAC) are benefiting " +
                "from a still-elevated rate environment, while the Energy holdings (XOM, CVX) " +
                "maintain strong free cash flow generation supportive of dividend sustainability.\n\n" +
                "- JPM: Investment banking pipeline strengthening; net interest income holding " +
                  "above cycle-average despite deposit repricing pressures\n" +
                "- BAC: Consumer deposit retention trends improving; expense discipline " +
                  "translating to operating leverage above peer-group median\n" +
                "- XOM: Permian production volumes ahead of guidance; Guyana ramp progressing " +
                  "on schedule, supporting medium-term cash flow visibility\n" +
                "- KO/PG: Pricing-driven revenue growth moderating as expected; volume recovery " +
                  "in emerging markets partially offsetting developed-market normalization"),

            new AiSeedContent(COMMENTARY, "BALANCED",
                "The balanced portfolio's multi-sector construction navigated a mixed market " +
                "environment effectively, with defensive holdings offsetting technology sector " +
                "rotation pressures during mid-week volatility.\n\n" +
                "Your balanced allocation demonstrated its diversification benefit this week " +
                "as sector rotation created short-term divergence between Technology growth " +
                "names and defensive holdings. AAPL and MSFT remain core Technology " +
                "contributors, while JPM, JNJ, and the Consumer Staples positions (PG, KO) " +
                "provided stabilizing income and lower-volatility return characteristics.\n\n" +
                "- AAPL/MSFT: Technology positions performing in line with large-cap growth " +
                  "benchmarks; AI services narrative intact\n" +
                "- JPM: Financials exposure providing yield and value characteristics that " +
                  "complement the growth-oriented Technology holdings\n" +
                "- JNJ: Healthcare defensive positioning performing as expected; MedTech " +
                  "volume recovery on track post-elective-procedure normalization\n" +
                "- XOM/PG/KO: Defensive and income-generating positions offsetting growth " +
                  "volatility; combined yield contribution supporting total return stability")
        );
    }
}
