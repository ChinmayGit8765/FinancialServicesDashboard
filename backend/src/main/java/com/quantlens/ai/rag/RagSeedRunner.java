package com.quantlens.ai.rag;

import com.quantlens.seed.SeedLog;
import com.quantlens.seed.SeedLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Idempotent seeder for the 10-K RAG corpus into {@code vector_store}.
 *
 * <p>{@code @Order(3)} — runs after {@link com.quantlens.ai.seed.AiSeedRunner @Order(2)}
 * which seeds demo answers that {@link com.quantlens.ai.chat.DemoModeAdvisor} returns.
 *
 * <h2>Idempotence</h2>
 * Guarded by a {@code seed_log} row with {@code id="rag-v1"}. The completion row is
 * written at the END of the transaction — if the JVM crashes mid-seed the partial work
 * rolls back and re-runs cleanly on the next restart.
 *
 * <h2>Corpus</h2>
 * 12 authored 10-K-style chunks across AAPL, MSFT, NVDA, JPM, XOM (2-3 chunks per ticker).
 * Sections: "Risk Factors", "MD&A", "Business Overview".  Each chunk is 200-500 words of
 * table-light narrative prose.  Embedded by the {@code @Primary DeterministicHashingEmbeddingModel}
 * — no key, no network.
 */
@Component
@Order(3)
public class RagSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RagSeedRunner.class);
    private static final String RAG_SEED_VERSION = "rag-v1";

    private final VectorStore vectorStore;
    private final SeedLogRepository seedLogRepository;

    public RagSeedRunner(VectorStore vectorStore,
                         SeedLogRepository seedLogRepository) {
        this.vectorStore        = vectorStore;
        this.seedLogRepository  = seedLogRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean alreadyDone = seedLogRepository.findById(RAG_SEED_VERSION)
                .map(SeedLog::isCompleted)
                .orElse(false);
        if (alreadyDone) {
            log.info("RagSeedRunner: seed_log {} already completed — skipping", RAG_SEED_VERSION);
            return;
        }

        log.info("RagSeedRunner: starting RAG corpus seed (seed_log {} not yet completed)...",
                RAG_SEED_VERSION);

        List<Document> chunks = buildChunks();
        vectorStore.add(chunks);
        log.info("RagSeedRunner: seeded {} chunks into vector_store", chunks.size());

        // Mark seed complete — written LAST so a mid-seed failure rolls back (T-07-SEED)
        SeedLog seedLog = new SeedLog(RAG_SEED_VERSION);
        seedLog.setCompleted(true);
        seedLog.setCompletedAt(LocalDateTime.now());
        seedLogRepository.save(seedLog);

        log.info("RagSeedRunner: seed_log {} marked completed", RAG_SEED_VERSION);
    }

    /**
     * Builds the 10-K RAG corpus chunks.
     *
     * <p>12 authored chunks across AAPL, MSFT, NVDA, JPM, XOM (2–3 per ticker).
     * Sections: "Risk Factors", "MD&amp;A", "Business Overview". 200–500 words each,
     * table-light narrative prose.  Metadata keys map 1:1 to {@link com.quantlens.ai.api.CitationDto}.
     *
     * @return list of {@link Document} chunks ready for {@code VectorStore.add()}
     */
    private List<Document> buildChunks() {
        return List.of(

            // ── AAPL ─────────────────────────────────────────────────────────────

            new RagSeedContent(
                "Apple Inc. faces significant regulatory scrutiny regarding its App Store policies " +
                "and the broader digital marketplace ecosystem. The European Commission's Digital " +
                "Markets Act designates Apple as a gatekeeper and requires the company to allow " +
                "alternative app distribution and third-party payment systems on iOS. Apple has " +
                "historically derived a commission of 15 to 30 percent on App Store transactions, " +
                "and analyst estimates suggest that mandatory interoperability requirements could " +
                "reduce Services segment gross margins by 300 to 500 basis points over a two-year " +
                "transition period. In the United States, the Department of Justice has filed " +
                "antitrust litigation challenging Apple's restrictions on third-party access to " +
                "NFC payment hardware, cloud services, and messaging interoperability. Additional " +
                "scrutiny from competition authorities in Japan, the United Kingdom, and South " +
                "Korea has resulted in voluntary concessions in several markets, though the " +
                "cumulative revenue impact across all jurisdictions remains uncertain. Apple has " +
                "argued that its integrated platform model provides security and privacy benefits " +
                "to consumers that outweigh the competitive costs of openness. The company " +
                "maintains significant litigation reserves and has a dedicated government affairs " +
                "function to manage regulatory engagement globally. However, the proliferation " +
                "of simultaneous regulatory investigations across major markets represents a " +
                "material operational and financial risk that investors should monitor carefully " +
                "when evaluating the long-term growth prospects of the Services segment.",
                "AAPL", "Risk Factors", "AAPL 10-K FY2023", "2023"
            ).toDocument(),

            new RagSeedContent(
                "Apple's Services segment delivered net revenue of approximately 85.2 billion " +
                "dollars in fiscal year 2023, representing 22 percent of total company net sales " +
                "and growing at a rate of approximately 9 percent year-over-year. The segment " +
                "encompasses the App Store, iCloud subscriptions, Apple Music, Apple TV+, Apple " +
                "Arcade, Apple Pay transaction fees, licensing arrangements with search engine " +
                "partners, and AppleCare extended warranty contracts. Management has consistently " +
                "highlighted Services as the primary driver of gross margin expansion over the " +
                "medium term, given that the segment generates gross margins in the range of " +
                "70 to 75 percent compared to approximately 36 percent for the Products segment. " +
                "The installed base of active Apple devices reached approximately 2 billion units " +
                "during fiscal 2023, providing a large addressable market for continued Services " +
                "attach rate improvement. The transition from one-time hardware purchases to " +
                "recurring subscription revenue has meaningfully reduced the cyclicality of " +
                "Apple's earnings relative to hardware unit cycles. Management's stated objective " +
                "is to continue growing paid subscriptions, which exceeded 1 billion for the " +
                "first time in fiscal 2022, as the primary lever for Services revenue acceleration.",
                "AAPL", "MD&A", "AAPL 10-K FY2023", "2023"
            ).toDocument(),

            // ── MSFT ─────────────────────────────────────────────────────────────

            new RagSeedContent(
                "Microsoft Corporation faces competitive risks across its primary business " +
                "segments — Productivity and Business Processes, Intelligent Cloud, and More " +
                "Personal Computing. In the cloud infrastructure segment, Azure competes " +
                "primarily with Amazon Web Services and Google Cloud Platform for enterprise " +
                "workloads, and the market share dynamics among the three major hyperscalers " +
                "are closely watched by institutional investors. Microsoft has maintained that " +
                "its enterprise relationships, established through decades of on-premises " +
                "software licensing, provide a structural advantage in hybrid cloud migrations " +
                "that pure-play cloud competitors lack. The company also faces regulatory risk " +
                "around its 69 billion dollar acquisition of Activision Blizzard, which received " +
                "extended scrutiny from the UK's Competition and Markets Authority and the " +
                "Federal Trade Commission before ultimately closing in fiscal 2024. In the " +
                "artificial intelligence segment, Microsoft's deep partnership with OpenAI and " +
                "the integration of Copilot features across Microsoft 365 and Azure create both " +
                "significant revenue opportunity and meaningful execution risk as the technology " +
                "matures. Cybersecurity risks are also prominently disclosed, given that " +
                "Microsoft's software and cloud infrastructure constitute critical systems " +
                "for governments and major financial institutions worldwide.",
                "MSFT", "Risk Factors", "MSFT 10-K FY2023", "2023"
            ).toDocument(),

            new RagSeedContent(
                "Microsoft's Intelligent Cloud segment, which includes Azure, SQL Server, " +
                "Windows Server, and GitHub, reported revenue of approximately 87.9 billion " +
                "dollars in fiscal year 2023, growing at approximately 19 percent year-over-year. " +
                "Azure and other cloud services within the segment grew at approximately " +
                "29 percent, though management noted that growth rates were moderating from " +
                "pandemic-era peaks as enterprise customers optimised existing cloud commitments " +
                "before expanding further. The company's AI-infused product roadmap — anchored " +
                "by Azure OpenAI Service, GitHub Copilot, and Microsoft 365 Copilot — is " +
                "expected to create a new pricing tier opportunity that management believes will " +
                "accelerate average revenue per user over the next three to five years. Microsoft " +
                "continues to invest heavily in data centre infrastructure globally, with capital " +
                "expenditures increasing substantially to support AI compute demand. The " +
                "Productivity and Business Processes segment, which includes Microsoft 365 " +
                "commercial, LinkedIn, and Dynamics 365, contributed approximately 69.3 billion " +
                "dollars in revenue, with commercial cloud products driving most of the growth " +
                "as seat counts expanded and pricing tiers migrated upward across the installed base.",
                "MSFT", "MD&A", "MSFT 10-K FY2023", "2023"
            ).toDocument(),

            // ── NVDA ─────────────────────────────────────────────────────────────

            new RagSeedContent(
                "NVIDIA Corporation's business is heavily concentrated in the data centre " +
                "segment following the explosive growth in demand for accelerated computing " +
                "infrastructure supporting large-scale artificial intelligence model training " +
                "and inference. This concentration creates material risk if demand from " +
                "hyperscalers and sovereign AI programmes decelerates more rapidly than " +
                "management anticipates. The company also faces export control restrictions " +
                "imposed by the United States government that limit the specification of " +
                "certain GPU products sold to China and other restricted geographies. The " +
                "H20 chip, designed to comply with these restrictions, carries lower average " +
                "selling prices and margins than the H100 and H200 products that dominate " +
                "data centre revenue in unrestricted markets. Supply chain concentration is " +
                "a further risk factor — NVIDIA's leading-edge GPU manufacturing is " +
                "exclusively sourced from Taiwan Semiconductor Manufacturing Company, creating " +
                "geographic concentration risk in the semiconductor fabrication supply chain. " +
                "Competition from custom silicon developed internally by hyperscaler customers " +
                "including Google's TPUs, Amazon's Trainium, and Microsoft's Maia represents " +
                "a longer-term risk to NVIDIA's dominant market position in AI compute.",
                "NVDA", "Risk Factors", "NVDA 10-K FY2024", "2024"
            ).toDocument(),

            new RagSeedContent(
                "NVIDIA's Data Center segment generated revenue of approximately 47.5 billion " +
                "dollars in fiscal year 2024, representing extraordinary growth of approximately " +
                "217 percent year-over-year, driven by hyperscaler and cloud service provider " +
                "demand for H100 and H200 GPU clusters for generative AI workloads. The company's " +
                "Hopper GPU architecture, anchored by the H100 tensor core GPU, became the " +
                "de facto standard for large language model training as measured by adoption " +
                "across every major AI research laboratory and commercial AI platform. Management " +
                "highlighted that the transition from training-only to inference compute as AI " +
                "models move to production is expected to expand the total addressable market " +
                "meaningfully, as inference workloads require sustained GPU capacity across " +
                "deployed model fleets. The forthcoming Blackwell GPU architecture, including " +
                "the GB200 NVLink rack system, is designed to deliver substantially higher " +
                "performance per dollar for inference workloads. NVIDIA's software ecosystem " +
                "— encompassing CUDA, cuDNN, TensorRT, and the NEMO framework — reinforces " +
                "the hardware platform with a deeply integrated software stack that creates " +
                "significant switching costs for customers who have optimised their AI workloads " +
                "on NVIDIA hardware.",
                "NVDA", "MD&A", "NVDA 10-K FY2024", "2024"
            ).toDocument(),

            // ── JPM ──────────────────────────────────────────────────────────────

            new RagSeedContent(
                "JPMorgan Chase & Co. operates within a complex and evolving regulatory " +
                "environment that imposes significant compliance obligations and capital " +
                "requirements on its operations. As a global systemically important bank, " +
                "JPMorgan is subject to enhanced capital buffers under Basel III and IV " +
                "frameworks, stress testing requirements administered by the Federal Reserve, " +
                "and resolution planning obligations under the Dodd-Frank Act. The Basel III " +
                "endgame proposal in the United States, as released in 2023, would require " +
                "large banks to hold substantially higher risk-weighted capital against trading " +
                "book positions and operational risk, with management estimating that the " +
                "original proposal would increase JPMorgan's Common Equity Tier 1 requirement " +
                "by approximately 25 percent. Credit risk represents the largest component of " +
                "JPMorgan's risk framework, given its exposure to consumer credit cards, " +
                "auto loans, home mortgages, commercial real estate, and corporate lending. " +
                "The credit cycle normalisation following pandemic-era stimulus has resulted " +
                "in net charge-off rates on consumer credit card balances returning toward " +
                "historical averages, which management has acknowledged represents a headwind " +
                "to consumer banking profitability relative to the 2021-2022 period.",
                "JPM", "Risk Factors", "JPM 10-K FY2023", "2023"
            ).toDocument(),

            new RagSeedContent(
                "JPMorgan Chase reported record net income of approximately 49.6 billion dollars " +
                "in full-year 2023, driven by net interest income expansion benefiting from the " +
                "Federal Reserve's interest rate tightening cycle and strong performance in the " +
                "investment banking and markets businesses. Net interest income of approximately " +
                "89.3 billion dollars represented a year-over-year increase of approximately " +
                "34 percent, reflecting the impact of higher benchmark rates on the firm's " +
                "deposit franchise. Management guided to net interest income normalisation " +
                "over the medium term as deposit betas increase and rate cuts reduce the " +
                "benefit to net interest margin. The consumer and community banking segment " +
                "continued to demonstrate strong deposit retention despite the availability " +
                "of money market alternatives, a testament to the firm's brand strength and " +
                "branch network density in key metropolitan markets. The Corporate and " +
                "Investment Bank segment delivered markets revenue of approximately 30.1 " +
                "billion dollars, with fixed income markets particularly strong during " +
                "periods of elevated rate and credit volatility. JPMorgan's return on " +
                "tangible common equity reached approximately 21 percent for the full year, " +
                "comfortably above the firm's long-term 17 percent target.",
                "JPM", "MD&A", "JPM 10-K FY2023", "2023"
            ).toDocument(),

            // ── XOM ──────────────────────────────────────────────────────────────

            new RagSeedContent(
                "ExxonMobil Corporation faces material risks associated with the global energy " +
                "transition and the evolving regulatory landscape governing greenhouse gas " +
                "emissions. Policy-driven demand destruction for petroleum-based fuels — " +
                "through carbon pricing mechanisms, fuel economy standards, and electric vehicle " +
                "mandates — represents a long-duration structural risk to ExxonMobil's upstream " +
                "exploration and production assets. The company maintains that oil and gas will " +
                "remain a material portion of the global energy mix through 2050 under most " +
                "credible energy transition scenarios, and that ExxonMobil's low-cost resource " +
                "base provides competitive resiliency across a range of demand outcomes. " +
                "Commodity price volatility is an inherent risk in the energy sector, and " +
                "ExxonMobil's earnings are sensitive to movements in the Brent crude price, " +
                "U.S. natural gas prices, and refining crack spreads. The company manages " +
                "this volatility through its integrated structure, which provides a natural " +
                "hedge between upstream and downstream segments during periods of commodity " +
                "price stress. Geopolitical risk in producing regions, particularly the " +
                "company's exposure in Guyana, Nigeria, and the Middle East, also warrants " +
                "ongoing investor attention given the potential for production disruption " +
                "or contract renegotiation in politically unstable environments.",
                "XOM", "Risk Factors", "XOM 10-K FY2023", "2023"
            ).toDocument(),

            new RagSeedContent(
                "ExxonMobil's upstream operations in the Permian Basin and Guyana emerged as " +
                "the primary drivers of production growth and capital efficiency in fiscal year " +
                "2023. The Permian Basin, following ExxonMobil's acquisition of Pioneer Natural " +
                "Resources in a deal that closed in 2024, now represents the company's single " +
                "largest producing asset with industry-leading breakeven economics estimated " +
                "below 35 dollars per barrel. In Guyana, ExxonMobil operates the Stabroek " +
                "block in partnership with Hess Corporation and CNOOC, which has become one " +
                "of the most significant deepwater oil discoveries of the past two decades " +
                "with estimated recoverable resources exceeding 11 billion oil-equivalent barrels. " +
                "ExxonMobil generated free cash flow of approximately 36.1 billion dollars " +
                "during 2023, supporting both an 18 billion dollar capital return programme " +
                "and continued investment in its Low Carbon Solutions business targeting " +
                "carbon capture, hydrogen production, and biofuel development. The company " +
                "raised its annual dividend for the 41st consecutive year in 2023, maintaining " +
                "its Dividend Aristocrat status and reinforcing its reputation as a reliable " +
                "income investment across energy sector allocations.",
                "XOM", "Business Overview", "XOM 10-K FY2023", "2023"
            ).toDocument()

        );
    }
}
