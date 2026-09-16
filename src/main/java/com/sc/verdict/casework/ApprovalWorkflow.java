package com.sc.verdict.casework;

import com.sc.verdict.evidence.ExtractedFact;
import com.sc.verdict.evidence.FactKey;
import com.sc.verdict.examination.Determination;
import com.sc.verdict.examination.Finding;
import com.sc.verdict.examination.FindingGrade;
import com.sc.verdict.shared.BankingCalendar;
import com.sc.verdict.shared.Hashing;
import com.sc.verdict.shared.Ids.ApprovalId;
import com.sc.verdict.shared.Ids.DocumentId;
import com.sc.verdict.shared.Ids.PartyId;
import com.sc.verdict.shared.Money;
import com.sc.verdict.shared.Versions.AdapterVersion;

import java.time.LocalDate;
import java.util.Objects;

/**
 * L5 — case and approval, thin. It does two things: draft an approval request from a
 * HOLD_PENDING_APPROVAL determination, and turn a granted approval into an {@link ExtractedFact} so
 * the engine can re-examine.
 *
 * <p>The second is the beat the room remembers: the approval does not go around the engine, it goes
 * back into it, as evidence (scenarios, Pack 4). The workflow does not itself admit the approval —
 * admission is L0's job, called before this — it only records, as a fact with full confidence and
 * provenance, that an admitted approval exists.
 */
public final class ApprovalWorkflow {

    /** The response window in banking days. A liability control (see {@link BankingCalendar}). */
    public static final int RESPONSE_WINDOW_BANKING_DAYS = 5;

    private static final AdapterVersion APPROVAL_INTAKE = new AdapterVersion("APPROVAL-INTAKE-v1");

    /** Draft the approval request for the first party-approvable finding on the determination. */
    public ApprovalRequest draft(Determination determination, PartyId approver,
                                 Money exposure, LocalDate windowStart) {
        Objects.requireNonNull(determination, "determination");
        FindingGrade grade = determination.blockingFindings().stream()
                .map(Finding::grade)
                .filter(FindingGrade::isPartyApprovable)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "determination has no party-approvable finding to draft against"));

        LocalDate dueBy = BankingCalendar.addBankingDays(windowStart, RESPONSE_WINDOW_BANKING_DAYS);
        String notice = determination.renderFindingsNotice()
                + "Approval requested from " + approver + " for the " + grade + " finding.\n"
                + "Response window: " + RESPONSE_WINDOW_BANKING_DAYS + " banking days, by " + dueBy + ".\n";

        return new ApprovalRequest(
                new ApprovalId("APR-" + determination.id().value()),
                determination.dealId(), determination.id(), approver, grade, exposure, dueBy, notice);
    }

    /**
     * Turn a granted (and separately admitted) approval into an evidence fact for re-examination.
     * The fact asserts that the approver approved a finding of {@code grade}; the engine treats it
     * like any other fact, with no knowledge that a human produced it.
     */
    public ExtractedFact asEvidence(ApprovalRequest request, String admissionRationale, java.time.Instant at) {
        Objects.requireNonNull(request, "request");
        String content = "approval|%s|%s|%s|%s".formatted(
                request.id(), request.approver(), request.grade(), admissionRationale);
        String hash = Hashing.sha256Hex(content);
        return new ExtractedFact(FactKey.APPROVAL_GRANTED, request.grade().name(), 1.0,
                new DocumentId("DOC-" + request.id().value()), hash, APPROVAL_INTAKE);
    }
}
