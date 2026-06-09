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

/**
 * Idempotent seeder for AI fixture content in the {@code ai_seed_content} table.
 *
 * <p>{@code @Order(2)} ensures this runner executes after {@code SeedRunner @Order(1)}
 * which seeds the base portfolio data (securities, users, positions) that the AI
 * seed content references.
 *
 * <h2>Idempotence</h2>
 * Guarded by a {@code seed_log} row with {@code id="ai-v1"}. The completion row is
 * written at the END of the transaction — if the JVM crashes mid-seed, the partial
 * work rolls back and re-runs cleanly on the next restart.
 *
 * <h2>Seeded universe</h2>
 * One {@code EXPLAIN_POSITION} row per ticker in the seeded portfolio universe (13 tickers),
 * and one {@code DAILY_COMMENTARY} row per persona (3 rows). Total: 16 rows.
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
 * Example: {@code "Headline text\n\nBody paragraph.\n\n- Bullet 1\n- Bullet 2"}
 */
@Component
@Order(2)
public class AiSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AiSeedRunner.class);

    private static final String AI_SEED_VERSION = "ai-v1";
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
            log.info("AiSeedRunner: seed_log ai-v1 already completed — skipping");
            return;
        }

        log.info("AiSeedRunner: starting AI seed content (seed_log ai-v1 not yet completed)...");

        List<AiSeedContent> fixtures = buildFixtures();
        int seeded = 0;
        for (AiSeedContent fixture : fixtures) {
            // Defensive idempotency per-row: skip if already present (e.g. partial previous run
            // without a seed_log row). Prevents uq_ai_seed unique-constraint violations.
            boolean exists = aiSeedContentRepository
                    .findByTypeAndSubjectId(fixture.getType(), fixture.getSubjectId())
                    .isPresent();
            if (!exists) {
                aiSeedContentRepository.save(fixture);
                seeded++;
            }
        }
        log.info("AiSeedRunner: seeded {} AI content rows ({} total defined)",
                seeded, fixtures.size());

        // Mark seed complete — written LAST so a mid-seed failure rolls back (T-06-07)
        SeedLog seedLog = new SeedLog(AI_SEED_VERSION);
        seedLog.setCompleted(true);
        seedLog.setCompletedAt(LocalDateTime.now());
        seedLogRepository.save(seedLog);

        log.info("AiSeedRunner: seed complete — seed_log ai-v1 marked completed");
    }

    // ── fixture authoring ─────────────────────────────────────────────────────

    private List<AiSeedContent> buildFixtures() {
        return List.of(
            // ── EXPLAIN_POSITION: Growth persona (Alice) ──────────────────────

            new AiSeedContent(EXPLAIN, "AAPL",
                "Apple Inc. (AAPL) occupies a central role in a growth-oriented portfolio, " +
                "driven by its dominant position in the global smartphone and services ecosystem. " +
                "With approximately 50 shares held, the position represents meaningful exposure to " +
                "the Technology sector's secular trends in mobile computing and subscription revenue.\n\n" +
                "Apple appears well-positioned to sustain premium margins through its tightly " +
                "integrated hardware-software platform, which has historically demonstrated strong " +
                "pricing power and customer retention. The company's growing Services segment — " +
                "encompassing the App Store, iCloud, and Apple One bundles — has historically " +
                "provided a recurring revenue layer that partially decouples earnings from " +
                "hardware unit cycles.\n\n" +
                "Investors should remain mindful of concentration risk within the consumer " +
                "electronics space and the potential impact of regulatory scrutiny on the App " +
                "Store model. Nevertheless, for a growth portfolio, AAPL's combination of " +
                "balance sheet strength and innovation cadence makes it a cornerstone allocation."),

            new AiSeedContent(EXPLAIN, "MSFT",
                "Microsoft Corporation (MSFT) is a cornerstone holding for growth-oriented " +
                "investors, with its approximately 20-share position reflecting a conviction " +
                "bet on enterprise cloud computing and AI platform adoption. The Technology " +
                "sector leader has systematically transitioned from a legacy software licenser " +
                "to a recurring-revenue cloud business anchored by Azure.\n\n" +
                "Azure's rapid expansion in enterprise workloads, combined with Microsoft's " +
                "early and deep integration of AI capabilities via its partnership with OpenAI, " +
                "appears to be reinforcing a durable competitive moat. The company has " +
                "historically demonstrated an ability to cross-sell across its productivity, " +
                "developer tooling, and cloud infrastructure pillars.\n\n" +
                "From a portfolio construction standpoint, MSFT offers a lower-volatility path " +
                "to technology upside relative to pure-play AI names. Its consistent free cash " +
                "flow generation and investment-grade balance sheet provide a degree of " +
                "defensive resilience uncommon among high-growth Technology holdings."),

            new AiSeedContent(EXPLAIN, "NVDA",
                "NVIDIA Corporation (NVDA) represents an aggressive growth position within the " +
                "portfolio, with approximately 30 shares providing significant exposure to " +
                "the accelerated computing and AI infrastructure megatrend. Classified under " +
                "the Technology sector, NVDA has evolved from a graphics processing unit " +
                "supplier into the de facto platform for large-scale AI model training and inference.\n\n" +
                "The company's H-series and Blackwell GPU architectures appear to be " +
                "sustaining substantial pricing power as hyperscalers and sovereign AI " +
                "initiatives compete for compute capacity. Data center revenue has historically " +
                "demonstrated exponential growth trajectories that have consistently exceeded " +
                "consensus estimates.\n\n" +
                "Investors should note that NVDA carries elevated valuation multiples and " +
                "meaningful volatility relative to broader Technology benchmarks — the position " +
                "sizing at approximately 30 shares reflects a balanced approach to capturing " +
                "AI upside while managing single-stock concentration risk."),

            new AiSeedContent(EXPLAIN, "AMZN",
                "Amazon.com Inc. (AMZN) is a multi-vector Technology holding representing " +
                "approximately 40 shares in the portfolio. The investment thesis encompasses " +
                "three distinct business engines: e-commerce marketplace infrastructure, " +
                "Amazon Web Services cloud computing, and the rapidly growing digital " +
                "advertising segment.\n\n" +
                "AWS appears well-positioned as a foundational cloud provider benefiting from " +
                "enterprise digital transformation spending, while the advertising business has " +
                "historically demonstrated superior unit economics as first-party retail data " +
                "becomes increasingly valuable in a privacy-centric marketing landscape. " +
                "The company's logistics network density creates a durable barrier to entry " +
                "for would-be competitors in the core e-commerce segment.\n\n" +
                "The relatively large share count reflects a conviction allocation to AMZN's " +
                "diversified earnings profile, which may provide portfolio resilience during " +
                "periods of sector rotation away from pure-play software names."),

            new AiSeedContent(EXPLAIN, "TSLA",
                "Tesla Inc. (TSLA) is a high-conviction, high-volatility position comprising " +
                "approximately 15 shares in the growth portfolio. Classified under the " +
                "Automotive sector, Tesla occupies a unique intersection of electric vehicle " +
                "manufacturing, energy storage, and autonomous driving technology development.\n\n" +
                "The company has historically demonstrated an ability to rapidly scale " +
                "manufacturing capacity and reduce per-unit costs, supporting a long-term " +
                "gross margin expansion thesis as the EV market matures. Full Self-Driving " +
                "software monetization and the energy storage segment (Powerwall, Megapack) " +
                "represent optionality that appears underappreciated in commodity vehicle " +
                "sector comparisons.\n\n" +
                "The smaller share count (15 shares) relative to other growth holdings reflects " +
                "TSLA's elevated beta and idiosyncratic risk profile. Investors should treat " +
                "this as a speculative growth position where near-term volatility is expected " +
                "and the investment horizon should be multi-year."),

            // ── EXPLAIN_POSITION: Income persona (Bob) ────────────────────────

            new AiSeedContent(EXPLAIN, "JPM",
                "JPMorgan Chase & Co. (JPM) is a core Financials holding representing " +
                "approximately 30 shares in an income-oriented portfolio. As the largest " +
                "U.S. bank by assets, JPMorgan offers broad exposure to consumer banking, " +
                "investment banking, commercial lending, and asset management — providing a " +
                "naturally diversified financial-sector allocation.\n\n" +
                "The firm has historically demonstrated superior returns on equity relative to " +
                "money-center bank peers, supported by disciplined risk management and a " +
                "fortress balance sheet. Rising interest rate environments have historically " +
                "benefited JPM's net interest income, while the investment banking and trading " +
                "segments provide counter-cyclical revenue during market volatility.\n\n" +
                "For an income portfolio, JPMorgan's dividend track record and capital return " +
                "consistency are particularly relevant. The position appears well-sized to " +
                "capture Financials sector exposure without excessive concentration in a " +
                "single banking relationship."),

            new AiSeedContent(EXPLAIN, "BAC",
                "Bank of America Corporation (BAC) is a high-volume position in the income " +
                "portfolio at approximately 80 shares, reflecting a deliberate overweight to " +
                "the Financials sector's consumer banking segment. BAC operates one of the " +
                "largest U.S. retail deposit franchises, giving it significant sensitivity " +
                "to interest rate movements.\n\n" +
                "The bank's Merrill Lynch wealth management platform and BofA Securities " +
                "investment banking division diversify its revenue base beyond net interest " +
                "income, while the vast consumer deposit base has historically provided " +
                "low-cost funding advantages during credit cycles. BAC has demonstrated " +
                "meaningful progress on expense discipline and digital banking adoption " +
                "over recent years.\n\n" +
                "The substantial share count reflects a yield-and-value thesis: BAC's " +
                "dividend yield at current prices, combined with ongoing share repurchase " +
                "activity, offers an attractive total return profile for investors prioritizing " +
                "current income alongside moderate capital appreciation potential."),

            new AiSeedContent(EXPLAIN, "XOM",
                "Exxon Mobil Corporation (XOM) is an anchor Energy sector holding at " +
                "approximately 40 shares, providing the income portfolio with meaningful " +
                "exposure to integrated oil and gas operations spanning upstream exploration, " +
                "downstream refining, and chemical manufacturing.\n\n" +
                "ExxonMobil's Permian Basin and Guyana offshore assets appear to offer " +
                "competitive breakeven economics relative to the global E&P peer group, " +
                "supporting free cash flow generation across a range of commodity price " +
                "scenarios. The company has historically prioritized dividend sustainability " +
                "through commodity cycles, a trait that aligns well with an income-focused " +
                "investment mandate.\n\n" +
                "Investors should remain cognizant of energy transition risk and the potential " +
                "for commodity price volatility to affect near-term earnings. Nevertheless, " +
                "XOM's scale, balance sheet strength, and dividend history make it a " +
                "defensible Energy allocation for a portfolio seeking current yield."),

            new AiSeedContent(EXPLAIN, "CVX",
                "Chevron Corporation (CVX) complements the XOM position as a second Energy " +
                "sector allocation at approximately 25 shares. Chevron's upstream portfolio " +
                "is weighted toward the Permian Basin and legacy deepwater Gulf of Mexico " +
                "assets, with international exposure through the Tengizchevroil joint venture " +
                "in Kazakhstan.\n\n" +
                "The company has historically maintained a lower leverage profile than many " +
                "E&P peers, supporting dividend consistency even during extended commodity " +
                "downturns. Chevron's flexible capital allocation framework — which adjusts " +
                "capex in response to oil price cycles — appears to provide a degree of " +
                "earnings resilience that income investors tend to value.\n\n" +
                "Holding both XOM and CVX in the income portfolio creates a diversified " +
                "Energy position with distinct geographic and operational characteristics, " +
                "reducing single-company operational risk while maintaining sector-level " +
                "dividend income exposure."),

            new AiSeedContent(EXPLAIN, "PG",
                "Procter & Gamble Co. (PG) is a Consumer Staples anchor position at " +
                "approximately 35 shares in the income portfolio. P&G's portfolio of global " +
                "consumer brands — spanning fabric care, personal care, and home care " +
                "categories — has historically demonstrated defensive earnings characteristics " +
                "during economic contractions.\n\n" +
                "The company's pricing power, supported by strong brand equity and consumer " +
                "loyalty across commodity product categories, has allowed it to sustain real " +
                "revenue growth through inflationary environments. P&G has grown its dividend " +
                "annually for over six decades, cementing its status as a Dividend King — a " +
                "characteristic central to its role in an income-oriented strategy.\n\n" +
                "For portfolio construction purposes, PG's low beta and negative correlation " +
                "to cyclical sectors makes it a natural portfolio volatility dampener, " +
                "supporting total return stability during periods of broad market stress."),

            new AiSeedContent(EXPLAIN, "KO",
                "The Coca-Cola Company (KO) represents approximately 60 shares in the income " +
                "portfolio, making it one of the larger Consumer Staples allocations by share " +
                "count. Coca-Cola's global beverage distribution network and brand portfolio " +
                "spanning over 500 brands appear to provide durable revenue streams that are " +
                "relatively insensitive to macroeconomic cycles.\n\n" +
                "The company's asset-light business model — having refranchised the majority " +
                "of its bottling operations — has historically generated high returns on " +
                "invested capital and strong free cash flow conversion relative to revenue. " +
                "KO has raised its dividend annually for over 60 consecutive years, a record " +
                "that is particularly relevant for investors focused on income sustainability.\n\n" +
                "The high share count at KO reflects a deliberate income-tilted sizing decision: " +
                "the dividend yield provides a meaningful current income component, while the " +
                "defensive sector characteristics smooth overall portfolio return volatility."),

            new AiSeedContent(EXPLAIN, "WMT",
                "Walmart Inc. (WMT) is a Consumer Staples holding at approximately 50 shares, " +
                "representing broad exposure to essential consumer spending and the ongoing " +
                "transformation of omnichannel retail. Walmart's scale — over 10,500 stores " +
                "globally — provides purchasing leverage and logistical capabilities that are " +
                "difficult for competitors to replicate.\n\n" +
                "The company has been investing heavily in its e-commerce capabilities and " +
                "advertising network, which appears to be generating a higher-margin revenue " +
                "stream that supplements the historically thin-margin core retail business. " +
                "Walmart+ membership and the growing Walmart Connect advertising platform may " +
                "represent meaningful earnings drivers that are not yet fully reflected in " +
                "traditional retail earnings models.\n\n" +
                "For income investors, WMT offers a combination of dividend income and " +
                "defensive earnings quality. Consumer spending on groceries and essential " +
                "goods — Walmart's core business — has historically shown strong resilience " +
                "during economic downturns, supporting dividend reliability through cycles."),

            // ── EXPLAIN_POSITION: Balanced persona (Charlie) ─────────────────

            new AiSeedContent(EXPLAIN, "JNJ",
                "Johnson & Johnson (JNJ) is a Healthcare sector holding at approximately " +
                "20 shares in the balanced portfolio, providing defensive sector exposure " +
                "alongside the growth-oriented Technology and income-oriented Financials " +
                "allocations. JNJ operates across MedTech and pharmaceutical segments " +
                "following the separation of its Consumer Health business (Kenvue).\n\n" +
                "The company's pharmaceutical pipeline — particularly oncology and immunology — " +
                "appears well-positioned to sustain top-line growth as legacy blockbuster " +
                "products face biosimilar competition. JNJ's MedTech segment, encompassing " +
                "orthopedic implants and surgical robotics, benefits from long-term " +
                "demographic tailwinds tied to an aging global population.\n\n" +
                "J&J has maintained Dividend Aristocrat status for decades, making it a " +
                "natural fit within a balanced portfolio seeking Healthcare sector exposure " +
                "with a stable income contribution. The position appears appropriately sized " +
                "to provide sector diversification without overconcentration in a complex " +
                "regulatory and litigation environment."),

            // ── DAILY_COMMENTARY per persona ──────────────────────────────────

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
                "Charlie's balanced allocation demonstrated its diversification benefit this " +
                "week as sector rotation created short-term divergence between Technology " +
                "growth names and defensive holdings. AAPL and MSFT remain core Technology " +
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
