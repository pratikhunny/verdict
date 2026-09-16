package com.sc.verdict.app;

import java.util.List;

/**
 * The JSON view models the API returns — a stable, demo-friendly shape decoupled from the domain
 * records, so the API contract does not shift when an internal type changes. Money is rendered as
 * {@code {amount, currency}} strings; instants as ISO-8601.
 */
public final class Views {

    private Views() {}

    public record MoneyDto(String amount, String currency) {}

    public record DealView(
            String id, MoneyDto contractValue, long quantity, String goods, String latestShipmentDate,
            List<ObligationDto> obligations, List<ConditionDto> conditions) {}

    public record ObligationDto(
            String id, String payee, String payeeParty, String rule, boolean severable,
            String clause, MoneyDto fullEntitlement) {}

    public record ConditionDto(String kind, String clause) {}

    public record DeterminationView(
            String id, String outcome, MoneyDto released, MoneyDto retained,
            List<LineDto> disbursements, List<ResidualDto> residuals,
            List<FindingDto> findings, List<VerdictDto> verdicts,
            String findingsNotice, String ruleSetVersion, String submissionId, String evidenceDigestShort) {}

    public record LineDto(String obligation, String payee, String payeeParty, MoneyDto amount) {}

    public record ResidualDto(String sourceObligation, String residualObligation, String payee, MoneyDto retained) {}

    public record FindingDto(String grade, String resolution, String clause, double confidence, String detail) {}

    public record VerdictDto(String condition, String status, String grade) {}

    public record LedgerView(
            MoneyDto held, MoneyDto unallocated, MoneyDto reserved, MoneyDto disbursed,
            List<EarmarkDto> earmarks, ReconDto reconciliation) {}

    public record EarmarkDto(String obligation, MoneyDto amount) {}

    public record ReconDto(MoneyDto entitlementTotal, MoneyDto earmarkTotal, boolean balanced) {}

    public record JournalView(List<EntryDto> entries) {}

    public record EntryDto(
            String id, String recordedAt, String determinationId, String outcome,
            String ruleSetVersion, String effectiveAt, String evidenceDigestShort, int factCount) {}

    public record ReplayView(
            String determinationId, boolean identical,
            String originalOutcome, String replayedOutcome,
            MoneyDto originalReleased, MoneyDto replayedReleased) {}

    public record ApprovalView(List<StepDto> steps, DeterminationView determination) {}

    public record StepDto(String label, String result, String detail) {}

    public record GraphView(String name, List<String> nodes) {}
}
