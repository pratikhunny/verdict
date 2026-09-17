package com.sc.verdict.app;

import com.sc.verdict.casework.ApprovalRequest;
import com.sc.verdict.casework.ApprovalWorkflow;
import com.sc.verdict.contract.DealDefinition;
import com.sc.verdict.contract.Obligation;
import com.sc.verdict.entitlement.EntitlementLedger;
import com.sc.verdict.evidence.ExtractedFact;
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
import com.sc.verdict.shared.Ids.SignatoryId;
import com.sc.verdict.shared.Ids.SubmissionId;
import com.sc.verdict.shared.Money;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the demo: many deal types (from {@link DealCatalog}), many transactions, each its own
 * programmable-wallet lifecycle — <em>collect → earmark → intake → examine → determine → disburse</em>
 * — with money steps (collect, earmark, disburse) separated from decision steps (examine, determine).
 * Each transaction carries its own {@link DealDefinition}, so the selected deal type drives the whole
 * flow through the same engine and nodes.
 */
@Service
public class TransactionService {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Instant AT = Instant.parse("2026-09-15T09:00:00Z");
    private static final ToleranceProfile TOLERANCE = ToleranceProfile.demoDefault();

    private final ExaminationEngine engine = new ExaminationEngine();
    private final HeroPartyFixtures parties = new HeroPartyFixtures();
    private final AuthorityPolicy authority = new AuthorityPolicy(parties, parties, parties);
    private final ApprovalWorkflow workflow = new ApprovalWorkflow();
    private final ReplayEngine replayEngine = new ReplayEngine(engine);
    private final ExtractionRouter router;
    private final SampleDocuments samples;
    private final DealCatalog catalog;

    private final Map<String, Txn> transactions = new LinkedHashMap<>();
    private int counter = 0;

    public TransactionService(ExtractionRouter router, SampleDocuments samples, DealCatalog catalog) {
        this.router = router;
        this.samples = samples;
        this.catalog = catalog;
    }

    static final class Txn {
        final String id;
        final String dealType;
        final DealDefinition deal;
        final Money fundAmount;
        final FundControlLedger ledger;
        final EntitlementLedger entitlements = new EntitlementLedger();
        final DecisionJournal journal = new DecisionJournal();
        final List<UploadedDoc> uploads = new ArrayList<>();
        Submission submission;
        Determination determination;
        Determination pendingApproval;
        boolean funded, earmarked, disbursed;
        String status = "NEW";
        int subCounter = 0;

        Txn(String id, String dealType, DealDefinition deal, Money fundAmount) {
            this.id = id;
            this.dealType = dealType;
            this.deal = deal;
            this.fundAmount = fundAmount;
            this.ledger = new FundControlLedger(fundAmount.currency());
        }
    }

    // ---------------------------------------------------------------- transactions

    public synchronized String newTransaction(String dealType) {
        String type = dealType != null && catalog.has(dealType) ? dealType : DealCatalog.MARKETPLACE;
        String id = "TXN-%04d".formatted(++counter);
        transactions.put(id, new Txn(id, type, catalog.deal(type), catalog.fundAmount(type)));
        return id;
    }

    Txn require(String id) {
        Txn t = transactions.get(id);
        if (t == null) throw new IllegalArgumentException("no such transaction: " + id);
        return t;
    }

    // ---------------------------------------------------------------- money-plane steps

    public synchronized void fund(String id) {
        Txn t = require(id);
        if (t.funded) throw new IllegalStateException(id + " is already funded");
        t.ledger.fund(t.fundAmount, id, AT);
        t.funded = true;
        t.status = "FUNDED";
    }

    public synchronized void earmark(String id) {
        Txn t = require(id);
        if (!t.funded) throw new IllegalStateException("fund the wallet before earmarking");
        if (t.earmarked) throw new IllegalStateException(id + " is already earmarked");
        for (Obligation o : examinedObligations(t)) {
            Money full = o.fullEntitlement(t.deal.contractValue());
            t.ledger.earmark(o.id(), full, id, AT);
            t.entitlements.seed(o.id(), full);
        }
        t.earmarked = true;
        t.status = "EARMARKED";
    }

    public synchronized Views.LedgerView disburse(String id) {
        Txn t = require(id);
        if (t.determination == null) throw new IllegalStateException("examine before disbursing");
        if (t.disbursed) throw new IllegalStateException(id + " is already disbursed");
        if (t.determination.outcome() != Outcome.RELEASE && t.determination.outcome() != Outcome.PARTIAL_RELEASE) {
            throw new IllegalStateException("nothing to disburse — determination is " + t.determination.outcome());
        }
        settle(t, t.determination);
        t.disbursed = true;
        t.status = t.determination.outcome().name();
        return Mapper.ledger(t.ledger, t.entitlements, t.fundAmount.currency(), t.deal);
    }

    // ---------------------------------------------------------------- documents (INTAKE)

    public synchronized List<UploadedDoc> upload(String id, String filename, String text) {
        Txn t = requireOpen(id);
        String docId = "DOC-%s-%d".formatted(id, t.uploads.size() + 1);
        t.uploads.add(new UploadedDoc(docId, filename, UploadedDoc.inferType(text), text,
                text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length));
        t.submission = null;
        return List.copyOf(t.uploads);
    }

    public synchronized List<UploadedDoc> loadSample(String id, String scenario) {
        Txn t = requireOpen(id);
        t.uploads.clear();
        t.uploads.addAll(samples.forScenario(t.dealType, scenario));
        t.submission = null;
        return List.copyOf(t.uploads);
    }

    public synchronized List<UploadedDoc> clearDocuments(String id) {
        Txn t = requireOpen(id);
        t.uploads.clear();
        t.submission = null;
        return List.of();
    }

    // ---------------------------------------------------------------- decision-plane steps

    public synchronized Submission extract(String id) {
        Txn t = requireOpen(id);
        if (t.uploads.isEmpty()) throw new IllegalStateException("upload or load a document before extracting");
        t.submission = router.extract(id + "-SUB-" + (++t.subCounter), t.deal.dealId(), t.uploads, AT);
        t.status = "EXTRACTED";
        return t.submission;
    }

    public synchronized Determination examine(String id) {
        Txn t = requireOpen(id);
        if (!t.earmarked) throw new IllegalStateException("fund and earmark the wallet before examining");
        if (t.submission == null) throw new IllegalStateException("extract the documents before examining");
        Determination det = decide(t, t.submission);
        t.determination = det;
        t.pendingApproval = det.outcome() == Outcome.HOLD_PENDING_APPROVAL ? det : null;
        t.status = det.outcome().name();
        return det;
    }

    public synchronized Views.ApprovalView approve(String id) {
        Txn t = require(id);
        if (t.pendingApproval == null) throw new IllegalStateException("no HOLD_PENDING_APPROVAL to approve on " + id);
        var steps = new ArrayList<Views.StepDto>();
        Money exposure = trancheValue(t);
        ApprovalRequest request = workflow.draft(t.pendingApproval, HeroPartyFixtures.BUYER,
                exposure, LocalDate.parse("2026-09-15"));

        AdmissionDecision alone = authority.admit(waive(exposure, List.of()));
        steps.add(new Views.StepDto("Approver signs — single signatory",
                alone.getClass().getSimpleName(),
                exposure + " exceeds the USD 50k single-signature ceiling — one of two signatures"));

        AdmissionDecision dual = authority.admit(waive(exposure, List.of(HeroPartyFixtures.BUYER_CFO)));
        String rationale = dual instanceof AdmissionDecision.Admitted a ? a.rationale() : "";
        steps.add(new Views.StepDto("Counter-signatory signs", dual.getClass().getSimpleName(), rationale));
        if (!dual.isAdmitted()) throw new IllegalStateException("approval was not admitted: " + dual);

        ExtractedFact approvalFact = workflow.asEvidence(request, rationale, AT);
        Submission reexamined = t.submission.withAdditionalFacts(
                new SubmissionId(id + "-SUB-APPROVED"), AT, List.of(approvalFact));
        Determination released = decide(t, reexamined);
        settle(t, released);
        t.determination = released;
        t.pendingApproval = null;
        t.disbursed = true;
        t.status = released.outcome().name();
        steps.add(new Views.StepDto("Approval enters as evidence → re-examined → disbursed",
                released.outcome().name(), "finding approved by entitled party; engine re-examines"));
        return new Views.ApprovalView(steps, Mapper.determination(released));
    }

    public synchronized Views.ReplayView replay(String id, String determinationId) {
        JournalEntry entry = require(id).journal.forDetermination(new DeterminationId(determinationId))
                .orElseThrow(() -> new IllegalArgumentException("no journal entry for " + determinationId));
        ReplayEngine.ReplayResult r = replayEngine.replay(entry);
        return new Views.ReplayView(determinationId, r.identical(),
                r.original().outcome().name(), r.replayed().outcome().name(),
                Mapper.money(r.original().released()), Mapper.money(r.replayed().released()));
    }

    // ---------------------------------------------------------------- extraction mode

    public synchronized Views.ExtractionModeView extractionMode() {
        return new Views.ExtractionModeView(router.mode().name(), router.liveAvailable(), router.modelLabel());
    }

    public synchronized Views.ExtractionModeView setExtractionMode(String mode) {
        router.setMode(ExtractionRouter.Mode.valueOf(mode.toUpperCase()));
        return extractionMode();
    }

    // ---------------------------------------------------------------- views

    public synchronized Views.LedgerView ledgerView(String id) {
        Txn t = require(id);
        return Mapper.ledger(t.ledger, t.entitlements, t.fundAmount.currency(), t.deal);
    }

    public synchronized Views.JournalView journalView(String id) {
        return new Views.JournalView(require(id).journal.entries().stream().map(Mapper::entry).toList());
    }

    public synchronized Views.TransactionView transactionView(String id) {
        Txn t = require(id);
        return Mapper.transaction(t, USD, catalog.orderTerms(t.dealType));
    }

    public synchronized List<Views.TransactionView> allTransactionViews() {
        return transactions.values().stream().map(t -> Mapper.transaction(t, USD, catalog.orderTerms(t.dealType))).toList();
    }

    public synchronized Views.ExtractionResultView extractionView(String id) {
        Txn t = require(id);
        return Mapper.extraction(t.uploads, t.submission, router.activeLabel());
    }

    public synchronized Views.DeterminationView determinationView(String id) {
        Determination d = require(id).determination;
        return d == null ? null : Mapper.determination(d);
    }

    public synchronized Views.DeterminationView pendingView(String id) {
        Determination d = require(id).pendingApproval;
        return d == null ? null : Mapper.determination(d);
    }

    // ---------------------------------------------------------------- internals

    private Determination decide(Txn t, Submission submission) {
        Determination det = engine.examine(t.deal, submission, TOLERANCE, ExaminationEngine.RULE_SET, AT);
        t.journal.append(t.deal, submission, TOLERANCE, ExaminationEngine.RULE_SET, AT, det, Instant.now());
        return det;
    }

    private void settle(Txn t, Determination det) {
        t.ledger.apply(det);
        t.entitlements.apply(det);
    }

    private Txn requireOpen(String id) {
        Txn t = require(id);
        if (t.determination != null) {
            throw new IllegalStateException(id + " is already settled — start a new transaction for different evidence");
        }
        return t;
    }

    private static List<Obligation> examinedObligations(Txn t) {
        return t.deal.obligationsFor(t.deal.shipmentMilestone().id());
    }

    private static Money trancheValue(Txn t) {
        Money v = Money.zero(t.deal.contractValue().currency());
        for (Obligation o : examinedObligations(t)) {
            v = v.plus(o.fullEntitlement(t.deal.contractValue()));
        }
        return v;
    }

    private Instruction waive(Money amount, List<SignatoryId> countersignatures) {
        return new Instruction(
                new InstructionId("INS-APPROVAL-" + (countersignatures.isEmpty() ? "1" : "2")),
                HeroPartyFixtures.DEAL, HeroPartyFixtures.BUYER, HeroPartyFixtures.BUYER_TREASURY,
                Mandate.InstructionType.WAIVE_DISCREPANCY, amount, countersignatures, AT);
    }
}
