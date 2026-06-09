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
 * Guarded by a {@code seed_log} row with {@code id="ai-v3"}. The completion row is
 * written at the END of the transaction — if the JVM crashes mid-seed, the partial
 * work rolls back and re-runs cleanly on the next restart.
 *
 * <h2>Upsert strategy (ai-v3)</h2>
 * Rows are upserted (updated if they already exist from ai-v1/ai-v2, inserted if new).
 * This ensures corrected content takes effect on existing databases. Prior seed_log rows
 * (ai-v1, ai-v2) are left intact; ai-v3 only checks for its own completion marker.
 *
 * <h2>Seeded universe</h2>
 * One {@code EXPLAIN_POSITION} row per ticker in the seeded portfolio universe (13 tickers),
 * one {@code DAILY_COMMENTARY} row per persona (3 rows), and 4 {@code RAG_QA} rows for
 * the chat Q&amp;A demo mode ({@code DEFAULT}, {@code AAPL_RISK}, {@code NVDA_AI},
 * {@code JPM_RATES}). Total: 20 rows.
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

    private static final String AI_SEED_VERSION    = "ai-v4";
    private static final String EXPLAIN            = "EXPLAIN_POSITION";
    private static final String COMMENTARY         = "DAILY_COMMENTARY";
    private static final String RAG_QA             = "RAG_QA";
    private static final String STRUCTURED_INSIGHT = "STRUCTURED_INSIGHT";

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

            // ── RAG_QA: demo chat answers with embedded citations ─────────────
            // Content is authored JSON: {"answer":"...","citations":[{"ticker":...}]}
            // ChatService parses this in demo mode (DemoModeAdvisor short-circuits).
            // Citations reference actual seeded chunks (same ticker/section/source as
            // RagSeedRunner corpus) so demo citations are genuine references.

            new AiSeedContent(RAG_QA, "DEFAULT",
                "{\"answer\":\"Based on the 10-K filings in this portfolio, the key regulatory " +
                "and strategic risks span several themes. Apple faces significant scrutiny over " +
                "its App Store policies under the EU Digital Markets Act, which could reduce " +
                "Services segment margins by 300-500 basis points as third-party payment systems " +
                "are mandated. NVIDIA's data centre dominance carries supply chain concentration " +
                "risk given exclusive reliance on TSMC for leading-edge fabrication, alongside " +
                "export control restrictions limiting GPU sales to China. JPMorgan Chase faces " +
                "credit cycle normalisation headwinds as consumer credit card charge-off rates " +
                "return to historical averages following pandemic-era stimulus. ExxonMobil " +
                "navigates long-duration energy transition risk as policy-driven demand destruction " +
                "for petroleum products accelerates across major economies. Each company's " +
                "management team has disclosed mitigation strategies in their respective " +
                "Risk Factors and MD\\u0026A sections.\"," +
                "\"citations\":[" +
                "{\"ticker\":\"AAPL\",\"section\":\"Risk Factors\",\"source\":\"AAPL 10-K FY2023\"," +
                "\"excerpt\":\"Apple Inc. faces significant regulatory scrutiny regarding its App Store policies and the broader digital marketplace ecosystem.\"}," +
                "{\"ticker\":\"NVDA\",\"section\":\"Risk Factors\",\"source\":\"NVDA 10-K FY2024\"," +
                "\"excerpt\":\"NVIDIA Corporation's business is heavily concentrated in the data centre segment following the explosive growth in demand for accelerated computing.\"}," +
                "{\"ticker\":\"JPM\",\"section\":\"Risk Factors\",\"source\":\"JPM 10-K FY2023\"," +
                "\"excerpt\":\"JPMorgan Chase & Co. operates within a complex and evolving regulatory environment that imposes significant compliance obligations.\"}," +
                "{\"ticker\":\"XOM\",\"section\":\"Risk Factors\",\"source\":\"XOM 10-K FY2023\"," +
                "\"excerpt\":\"ExxonMobil Corporation faces material risks associated with the global energy transition and the evolving regulatory landscape.\"}" +
                "]}"),

            new AiSeedContent(RAG_QA, "AAPL_RISK",
                "{\"answer\":\"Apple's 10-K Risk Factors section discloses several material risks. " +
                "The most prominent is regulatory scrutiny of the App Store: the EU Digital Markets " +
                "Act requires Apple to allow alternative app distribution and third-party payment " +
                "systems on iOS, potentially reducing the 15-30% App Store commission and compressing " +
                "Services gross margins by 300-500 basis points. In the United States, the Department " +
                "of Justice has filed antitrust litigation challenging Apple's restrictions on " +
                "third-party NFC payment access and messaging interoperability. Japan, the UK, and " +
                "South Korea have also initiated investigations, resulting in voluntary concessions. " +
                "Apple's Services segment — which contributes approximately 22% of total revenue at " +
                "roughly $85.2 billion — is the most margin-sensitive component of the business, " +
                "making regulatory outcomes disproportionately important to the earnings trajectory. " +
                "Management maintains that the integrated platform provides security and privacy " +
                "benefits justifying its model, but the litigation reserve and regulatory engagement " +
                "costs are material and ongoing.\"," +
                "\"citations\":[" +
                "{\"ticker\":\"AAPL\",\"section\":\"Risk Factors\",\"source\":\"AAPL 10-K FY2023\"," +
                "\"excerpt\":\"Apple Inc. faces significant regulatory scrutiny regarding its App Store policies and the broader digital marketplace ecosystem. The European Commission's Digital Markets Act...\"}," +
                "{\"ticker\":\"AAPL\",\"section\":\"MD&A\",\"source\":\"AAPL 10-K FY2023\"," +
                "\"excerpt\":\"Apple's Services segment delivered net revenue of approximately 85.2 billion dollars in fiscal year 2023, representing 22 percent of total company net sales.\"}" +
                "]}"),

            new AiSeedContent(RAG_QA, "NVDA_AI",
                "{\"answer\":\"NVIDIA's 10-K reveals that its data centre segment is the dominant " +
                "growth engine, generating approximately $47.5 billion in fiscal 2024 revenue — " +
                "a 217% year-over-year increase driven by H100 GPU demand for generative AI " +
                "training workloads. The Hopper GPU architecture became the de facto standard " +
                "for large language model training across major AI research laboratories. " +
                "Management highlighted the transition from training-only to inference compute " +
                "as a key TAM expansion opportunity, since inference requires sustained GPU " +
                "capacity across deployed model fleets. The forthcoming Blackwell GB200 NVLink " +
                "system targets inference economics specifically. NVIDIA's CUDA software ecosystem " +
                "creates significant switching costs that reinforce hardware platform stickiness. " +
                "Key risks include export control restrictions on GPU sales to China (the H20 chip " +
                "carries lower margins), supply chain concentration at TSMC, and longer-term " +
                "competition from custom silicon developed by Google (TPUs), Amazon (Trainium), " +
                "and Microsoft (Maia).\"," +
                "\"citations\":[" +
                "{\"ticker\":\"NVDA\",\"section\":\"MD&A\",\"source\":\"NVDA 10-K FY2024\"," +
                "\"excerpt\":\"NVIDIA's Data Center segment generated revenue of approximately 47.5 billion dollars in fiscal year 2024, representing extraordinary growth of approximately 217 percent.\"}," +
                "{\"ticker\":\"NVDA\",\"section\":\"Risk Factors\",\"source\":\"NVDA 10-K FY2024\"," +
                "\"excerpt\":\"NVIDIA Corporation's business is heavily concentrated in the data centre segment. This concentration creates material risk if demand from hyperscalers decelerates.\"}" +
                "]}"),

            new AiSeedContent(RAG_QA, "JPM_RATES",
                "{\"answer\":\"JPMorgan Chase's 2023 10-K shows that rising interest rates were " +
                "the primary driver of record earnings. Net interest income of approximately " +
                "$89.3 billion grew 34% year-over-year as the Federal Reserve's tightening cycle " +
                "expanded the firm's net interest margin. Full-year 2023 net income reached a " +
                "record $49.6 billion, with return on tangible common equity of approximately 21% " +
                "— well above the firm's 17% long-term target. Management guided that NII will " +
                "normalise over the medium term as deposit betas increase and eventual rate cuts " +
                "reduce the benefit. On the risk side, the Basel III endgame proposal would " +
                "increase JPMorgan's CET1 requirement by approximately 25% under the original " +
                "proposal, constraining capital return capacity. Credit cycle normalisation in " +
                "the consumer card portfolio — with charge-off rates returning to historical " +
                "averages — represents a headwind to consumer banking profitability relative " +
                "to 2021-2022 levels.\"," +
                "\"citations\":[" +
                "{\"ticker\":\"JPM\",\"section\":\"MD&A\",\"source\":\"JPM 10-K FY2023\"," +
                "\"excerpt\":\"JPMorgan Chase reported record net income of approximately 49.6 billion dollars in full-year 2023, driven by net interest income expansion.\"}," +
                "{\"ticker\":\"JPM\",\"section\":\"Risk Factors\",\"source\":\"JPM 10-K FY2023\"," +
                "\"excerpt\":\"JPMorgan Chase & Co. operates within a complex and evolving regulatory environment. The Basel III endgame proposal would increase JPMorgan's CET1 requirement by approximately 25 percent.\"}" +
                "]}"),

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
                  "volatility; combined yield contribution supporting total return stability"),

            // ── STRUCTURED_INSIGHT: per-persona sector exposure (ai-v4) ──────────
            // JSON must match StructuredInsightRecord exactly: title, subtitle, series:[{label,value}]
            // Parsed by ObjectMapper.readValue in demo mode — any schema deviation throws
            // JsonMappingException (08-RESEARCH Pitfall 2).

            new AiSeedContent(STRUCTURED_INSIGHT, "GROWTH",
                "{\"title\":\"Sector Exposure\",\"subtitle\":\"AI-Detected Allocation (Demo)\"," +
                "\"series\":[" +
                "{\"label\":\"Technology\",\"value\":62.5}," +
                "{\"label\":\"Automotive\",\"value\":17.3}," +
                "{\"label\":\"Consumer Staples\",\"value\":10.1}," +
                "{\"label\":\"Cash\",\"value\":10.1}" +
                "]}"),

            new AiSeedContent(STRUCTURED_INSIGHT, "INCOME",
                "{\"title\":\"Sector Exposure\",\"subtitle\":\"AI-Detected Allocation (Demo)\"," +
                "\"series\":[" +
                "{\"label\":\"Financials\",\"value\":36.2}," +
                "{\"label\":\"Energy\",\"value\":28.4}," +
                "{\"label\":\"Consumer Staples\",\"value\":25.9}," +
                "{\"label\":\"Cash\",\"value\":9.5}" +
                "]}"),

            new AiSeedContent(STRUCTURED_INSIGHT, "BALANCED",
                "{\"title\":\"Sector Exposure\",\"subtitle\":\"AI-Detected Allocation (Demo)\"," +
                "\"series\":[" +
                "{\"label\":\"Technology\",\"value\":32.1}," +
                "{\"label\":\"Financials\",\"value\":18.6}," +
                "{\"label\":\"Healthcare\",\"value\":15.4}," +
                "{\"label\":\"Consumer Staples\",\"value\":14.2}," +
                "{\"label\":\"Energy\",\"value\":11.8}," +
                "{\"label\":\"Cash\",\"value\":7.9}" +
                "]}")
        );
    }
}
