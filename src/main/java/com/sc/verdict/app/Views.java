package com.sc.verdict.app;

import java.util.List;

/**
 * The JSON view models the API returns — a stable, demo-friendly shape decoupled from the domain
 * records. Money is rendered as {@code {amount, currency}} strings; instants as ISO-8601.
 */
public final class Views {

    private Views() {}

    public record MoneyDto(String amount, String currency) {}

    // ---- contract & parties ----

    public record ContractView(
            String id, String dealType, MoneyDto tranche, List<ChipDto> chips, String tolerance,
            List<PartyView> parties, List<ResponsibilityView> responsibilities,
            List<ConditionDto> conditions, List<ObligationDto> payees) {}

    public record ChipDto(String label, String value) {}

    // ---- new-order form (per-transaction inputs the operator supplies) ----

    /** The fields an operator fills to open a transaction under a deal type; drives a data-driven form. */
    public record OrderFormView(String dealType, String title, List<FieldSpec> fields) {}

    /** One input field. {@code type} is text|number|date|money; {@code demo} is the "Fill for demo" value. */
    public record FieldSpec(String key, String label, String type, String demo, String suffix, String help) {}

    /** An onboarded entity (L0) — one-time KYC / screening, reused across deals. */
    public record PartyView(String name, String type, String jurisdiction, String screening) {}

    /** A party's role and signing authority in this particular deal — changes per deal. */
    public record ResponsibilityView(String party, String role, String mandate) {}

    public record ConditionDto(String kind, String clause) {}

    public record ObligationDto(String id, String payee, String payeeParty, String rule,
                                boolean severable, String clause, MoneyDto fullEntitlement) {}

    // ---- transactions ----

    public record TransactionView(String id, String dealType, String status, MoneyDto held, MoneyDto released,
                                  MoneyDto retained, String outcome, int documentCount,
                                  boolean funded, boolean earmarked, boolean extracted,
                                  boolean determined, boolean disbursed,
                                  MoneyDto fundAmount, MoneyDto milestoneValue, List<ChipDto> orderTerms) {}

    // ---- documents & extraction ----

    public record UploadedDocView(String id, String filename, String type, int sizeBytes, String text) {}

    public record FactView(String field, String key, String value, double confidence, String sourceDocument) {}

    public record ExtractionResultView(String extractor, List<UploadedDocView> documents, List<FactView> facts) {}

    public record ExtractionModeView(String mode, boolean liveAvailable, String model) {}

    // ---- determination ----

    public record DeterminationView(
            String id, String outcome, MoneyDto released, MoneyDto retained,
            List<LineDto> disbursements, List<ResidualDto> residuals,
            List<FindingDto> findings, List<VerdictDto> verdicts,
            String findingsNotice, String ruleSetVersion, String submissionId, String evidenceDigestShort) {}

    public record LineDto(String obligation, String payee, String payeeParty, MoneyDto amount) {}

    public record ResidualDto(String sourceObligation, String residualObligation, String payee, MoneyDto retained) {}

    public record FindingDto(String grade, String resolution, String clause, double confidence, String detail) {}

    public record VerdictDto(String condition, String status, String grade) {}

    // ---- ledger & journal ----

    public record LedgerView(MoneyDto held, MoneyDto unallocated, MoneyDto reserved, MoneyDto disbursed,
                             List<EarmarkDto> earmarks, ReconDto reconciliation) {}

    public record EarmarkDto(String obligation, String payee, MoneyDto amount) {}

    public record ReconDto(MoneyDto entitlementTotal, MoneyDto earmarkTotal, boolean balanced) {}

    public record JournalView(List<EntryDto> entries) {}

    public record EntryDto(String id, String recordedAt, String determinationId, String outcome,
                           String ruleSetVersion, String effectiveAt, String evidenceDigestShort, int factCount) {}

    public record ReplayView(String determinationId, boolean identical, String originalOutcome,
                             String replayedOutcome, MoneyDto originalReleased, MoneyDto replayedReleased) {}

    // ---- approval & graphs ----

    public record ApprovalView(List<StepDto> steps, DeterminationView determination) {}

    public record StepDto(String label, String result, String detail) {}

    public record GraphView(String name, List<String> nodes) {}

    /** A real graph: nodes with positions and explicit edges (edges = transitions/conditions). */
    public record GraphView2(String name, List<GNode> nodes, List<GEdge> edges) {}

    public record GNode(String id, String label, int x, int y, String kind) {}

    public record GEdge(String from, String to, String label, boolean dashed) {}
}
