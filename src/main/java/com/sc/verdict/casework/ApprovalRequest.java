package com.sc.verdict.casework;

import com.sc.verdict.examination.FindingGrade;
import com.sc.verdict.shared.Ids.ApprovalId;
import com.sc.verdict.shared.Ids.DealId;
import com.sc.verdict.shared.Ids.DeterminationId;
import com.sc.verdict.shared.Ids.PartyId;
import com.sc.verdict.shared.Money;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A drafted request for a counterparty to approve a finding so the engine may release on
 * re-examination. Carries the exposure at stake (which drives the authority required to approve it),
 * the response window's due date in banking days, and the grade being approved.
 *
 * <p>It is a draft: nothing has moved and no approval has been given. It becomes an approval only
 * when an entitled party's instruction is admitted by the authority gate — at which point the
 * approval re-enters the engine as evidence rather than bypassing it.
 */
public record ApprovalRequest(
        ApprovalId id,
        DealId dealId,
        DeterminationId determination,
        PartyId approver,
        FindingGrade grade,
        Money exposure,
        LocalDate responseDueBy,
        String notice) {

    public ApprovalRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dealId, "dealId");
        Objects.requireNonNull(determination, "determination");
        Objects.requireNonNull(approver, "approver");
        Objects.requireNonNull(grade, "grade");
        Objects.requireNonNull(exposure, "exposure");
        Objects.requireNonNull(responseDueBy, "responseDueBy");
        Objects.requireNonNull(notice, "notice");
    }
}
