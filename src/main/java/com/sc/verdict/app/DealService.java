package com.sc.verdict.app;

import com.sc.verdict.casework.ApprovalRequest;
import com.sc.verdict.casework.ApprovalWorkflow;
import com.sc.verdict.contract.DealDefinition;
import com.sc.verdict.contract.HeroDealRegistry;
import com.sc.verdict.contract.Obligation;
import com.sc.verdict.entitlement.EntitlementLedger;
import com.sc.verdict.evidence.EvidencePack;
import com.sc.verdict.evidence.ExtractedFact;
import com.sc.verdict.evidence.FixtureExtraction;
import com.sc.verdict.evidence.Submission;
import com.sc.verdict.examination.Determination;
import com.sc.verdict.examination.ExaminationEngine;
import com.sc.verdict.examination.Outcome;
import com.sc.verdict.examination.ToleranceProfile;
import com.sc.verdict.journal.DecisionJournal;
import com.sc.verdict.journal.JournalEntry;
import com.sc.verdict.journal.ReplayEngine;
import com.sc.verdict.ledger.FundControlLedger;
import com.sc.verdict.party.AdmissionDecision;
import com.sc.verdict.party.AuthorityPolicy;
import com.sc.verdict.party.HeroPartyFixtures;
import com.sc.verdict.party.Instruction;
import com.sc.verdict.party.Mandate;
import com.sc.verdict.shared.Ids.DeterminationId;
import com.sc.verdict.shared.Ids.InstructionId;
import com.sc.verdict.shared.Ids.SubmissionId;
import com.sc.verdict.shared.Money;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

/**
 * The demo's orchestration over the domain core, for the single hero deal. It holds the mutable
 * money-plane and journal state in memory so the console can run a pack, show the effect, and reset
 * instantly between packs (scenarios §Implementation notes). Every determination still flows through
 * the real engine, ledger, journal and reconciliation — nothing here is faked, only wired.
 *
 * <p>Single-deal, single-user demo scope: methods are synchronized, which is ample for a stage
 * console. Multi-deal, concurrent operation is a persistence concern deferred to phase 2b.
 */
@Service
public class DealService {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Instant EXAM_AT = Instant.parse("2026-09-15T09:00:00Z");
    private static final ToleranceProfile TOLERANCE = ToleranceProfile.demoDefault();

    private final HeroDealRegistry registry = new HeroDealRegistry();
    private final DealDefinition deal = registry.heroDeal();
    private final FixtureExtraction extraction = new FixtureExtraction();
    private final ExaminationEngine engine = new ExaminationEngine();
    private final HeroPartyFixtures parties = new HeroPartyFixtures();
    private final AuthorityPolicy authority = new AuthorityPolicy(parties, parties, parties);
    private final ApprovalWorkflow workflow = new ApprovalWorkflow();
    private final ReplayEngine replayEngine = new ReplayEngine(engine);

    private FundControlLedger ledger;
    private EntitlementLedger entitlements;
    private DecisionJournal journal;
    private Submission lastLateSubmission;
    private Determination pendingApproval;

    public DealService() {
        reset();
    }

    // ---------------------------------------------------------------- lifecycle

    /** Re-fund and re-earmark the deal to its starting state, clearing prior determinations. */
    public synchronized void reset() {
        ledger = new FundControlLedger(USD);
        ledger.fund(deal.contractValue(), "SETUP", EXAM_AT);
        for (Obligation o : deal.obligations()) {
            ledger.earmark(o.id(), o.fullEntitlement(deal.contractValue()), "SETUP", EXAM_AT);
        }
        entitlements = new EntitlementLedger();
        entitlements.seedFromDeal(deal);
        journal = new DecisionJournal();
        lastLateSubmission = null;
        pendingApproval = null;
    }

    // ---------------------------------------------------------------- operations

    /** Run one evidence pack from a clean funded state; returns the determination. */
    public synchronized Views.DeterminationView runPack(EvidencePack pack) {
        reset();
        Submission submission = extraction.extract(pack, deal.dealId(), EXAM_AT);
        Determination det = examineApplyJournal(submission);
        if (pack == EvidencePack.LATE_SHIPMENT) {
            lastLateSubmission = submission;
            pendingApproval = det;
        }
        return Mapper.determination(det);
    }

    /**
     * Run the Pack 4 approval loop on a standing HOLD_PENDING_APPROVAL: the buyer approves, the
     * approval passes the real L0 admission gate (Treasury alone is short of the ceiling; the CFO
     * countersigns), and the approval re-enters the engine as evidence for re-examination.
     */
    public synchronized Views.ApprovalView approve() {
        if (pendingApproval == null || pendingApproval.outcome() != Outcome.HOLD_PENDING_APPROVAL) {
            throw new IllegalStateException("no HOLD_PENDING_APPROVAL determination to approve — run Pack 4 first");
        }
        var steps = new ArrayList<Views.StepDto>();

        ApprovalRequest request = workflow.draft(pendingApproval, HeroPartyFixtures.BUYER,
                Money.of("600000", "USD"), LocalDate.parse("2026-09-15"));

        Instruction alone = waive(List.of());
        AdmissionDecision aloneDecision = authority.admit(alone);
        steps.add(new Views.StepDto(
                "Buyer approves — Treasury Manager alone",
                aloneDecision.getClass().getSimpleName(),
                "USD 600,000 exceeds the USD 50k single-signature ceiling — one of two signatures"));

        Instruction dual = waive(List.of(HeroPartyFixtures.BUYER_CFO));
        AdmissionDecision dualDecision = authority.admit(dual);
        String rationale = dualDecision instanceof AdmissionDecision.Admitted a ? a.rationale() : "";
        steps.add(new Views.StepDto("CFO countersigns", dualDecision.getClass().getSimpleName(), rationale));

        if (!dualDecision.isAdmitted()) {
            throw new IllegalStateException("approval was not admitted: " + dualDecision);
        }

        ExtractedFact approvalFact = workflow.asEvidence(request, rationale, EXAM_AT);
        Submission reexamined = lastLateSubmission.withAdditionalFacts(
                new SubmissionId("SUB-LATE_SHIPMENT-APPROVED"), EXAM_AT, List.of(approvalFact));
        Determination released = examineApplyJournal(reexamined);
        steps.add(new Views.StepDto("Approval enters as evidence → re-examined",
                released.outcome().name(), "TIMING finding approved by entitled party; engine re-examines"));

        pendingApproval = null;
        return new Views.ApprovalView(steps, Mapper.determination(released));
    }

    public synchronized Views.ReplayView replay(String determinationId) {
        Optional<JournalEntry> entry = journal.forDetermination(new DeterminationId(determinationId));
        JournalEntry e = entry.orElseThrow(() ->
                new IllegalArgumentException("no journal entry for determination " + determinationId));
        ReplayEngine.ReplayResult r = replayEngine.replay(e);
        return new Views.ReplayView(determinationId, r.identical(),
                r.original().outcome().name(), r.replayed().outcome().name(),
                Mapper.money(r.original().released()), Mapper.money(r.replayed().released()));
    }

    // ---------------------------------------------------------------- views

    public synchronized Views.DealView dealView() {
        return Mapper.deal(deal);
    }

    public synchronized Views.LedgerView ledgerView() {
        return Mapper.ledger(ledger, entitlements, USD);
    }

    public synchronized Views.JournalView journalView() {
        return new Views.JournalView(journal.entries().stream().map(Mapper::entry).toList());
    }

    /** The determination awaiting a counterparty approval, or null if none is pending. */
    public synchronized Views.DeterminationView pendingView() {
        return pendingApproval == null ? null : Mapper.determination(pendingApproval);
    }

    // ---------------------------------------------------------------- internals

    private Determination examineApplyJournal(Submission submission) {
        Determination det = engine.examine(deal, submission, TOLERANCE, ExaminationEngine.RULE_SET, EXAM_AT);
        journal.append(deal, submission, TOLERANCE, ExaminationEngine.RULE_SET, EXAM_AT, det, Instant.now());
        ledger.apply(det);
        entitlements.apply(det);
        return det;
    }

    private Instruction waive(List<com.sc.verdict.shared.Ids.SignatoryId> countersignatures) {
        return new Instruction(
                new InstructionId("INS-APPROVAL-" + (countersignatures.isEmpty() ? "1" : "2")),
                HeroPartyFixtures.DEAL, HeroPartyFixtures.BUYER, HeroPartyFixtures.BUYER_TREASURY,
                Mandate.InstructionType.WAIVE_DISCREPANCY, Money.of("600000", "USD"),
                countersignatures, EXAM_AT);
    }
}
