package com.sc.verdict.party;

import com.sc.verdict.shared.Money;
import com.sc.verdict.shared.Ids.DealId;
import com.sc.verdict.shared.Ids.InstructionId;
import com.sc.verdict.shared.Ids.PartyId;
import com.sc.verdict.shared.Ids.RoleId;
import com.sc.verdict.shared.Ids.SignatoryId;
import com.sc.verdict.party.Ports.MandateStore;
import com.sc.verdict.party.Ports.PartyProvider;
import com.sc.verdict.party.Ports.ScreeningPolicy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Executable scenarios for the hero deal: a cross-border marketplace trade between a Malaysian
 * buyer and a Vietnamese seller, with Standard Chartered as escrow agent and the marketplace
 * operator as an observer.
 *
 * <p>Run with {@code java AuthorityScenarios} to print the admission table. Each row is a control
 * that either holds or does not; the harness exits non-zero if any expectation fails.
 */
public final class AuthorityScenarios {

    private static final Instant ONBOARDED = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant DEAL_START = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");
    /** After onboarding but before the deal existed: isolates the role window as the binding control. */
    private static final Instant BEFORE_DEAL = Instant.parse("2026-05-01T00:00:00Z");

    private static final PartyId BUYER = new PartyId("PTY-BUYER-MY");
    private static final PartyId SELLER = new PartyId("PTY-SELLER-VN");
    private static final PartyId BANK = new PartyId("PTY-SCB-AGENT");
    private static final PartyId MARKETPLACE = new PartyId("PTY-MARKETPLACE");
    private static final PartyId SANCTIONED = new PartyId("PTY-SANCTIONED");

    private static final SignatoryId BUYER_TREASURY = new SignatoryId("SIG-BUYER-TREASURY");
    private static final SignatoryId BUYER_CFO = new SignatoryId("SIG-BUYER-CFO");
    private static final SignatoryId SELLER_DIRECTOR = new SignatoryId("SIG-SELLER-DIRECTOR");
    private static final SignatoryId BANK_OPS = new SignatoryId("SIG-BANK-OPS");
    private static final SignatoryId MARKETPLACE_ADMIN = new SignatoryId("SIG-MARKETPLACE-ADMIN");

    private static final DealId DEAL = new DealId("DEAL-2026-0417");

    private static final RoleId BUYER_OBLIGOR = new RoleId("ROLE-BUYER-OBLIGOR");
    private static final RoleId SELLER_OBLIGEE = new RoleId("ROLE-SELLER-OBLIGEE");
    private static final RoleId BANK_AGENT = new RoleId("ROLE-BANK-AGENT");
    private static final RoleId MARKETPLACE_OBSERVER = new RoleId("ROLE-MARKETPLACE-OBSERVER");

    public static void main(String[] args) {
        var fixtures = new Fixtures();
        var policy = new AuthorityPolicy(fixtures, fixtures, fixtures);
        var results = new ArrayList<Row>();

        results.add(run(policy, "Treasury waives USD 5k discrepancy",
                waiver(BUYER, BUYER_TREASURY, "5000", "USD", NOW, List.of()),
                "Admitted"));

        results.add(run(policy, "Treasury waives USD 80k alone (above 50k cap)",
                waiver(BUYER, BUYER_TREASURY, "80000", "USD", NOW, List.of()),
                "RequiresCountersignature"));

        results.add(run(policy, "Treasury + CFO jointly waive USD 80k",
                waiver(BUYER, BUYER_TREASURY, "80000", "USD", NOW, List.of(BUYER_CFO)),
                "Admitted"));

        results.add(run(policy, "Treasury countersigns own USD 80k waiver (four-eyes)",
                waiver(BUYER, BUYER_TREASURY, "80000", "USD", NOW, List.of(BUYER_TREASURY)),
                "RequiresCountersignature"));

        results.add(run(policy, "MYR amendment against a USD-only mandate",
                instruction(SELLER, SELLER_DIRECTOR, Mandate.InstructionType.AMEND_OBLIGATION,
                        "5000", "MYR", NOW, List.of()),
                "CURRENCY_MISMATCH"));

        results.add(run(policy, "MYR waiver falls through to uncapped dual mandate",
                waiver(BUYER, BUYER_TREASURY, "5000", "MYR", NOW, List.of()),
                "RequiresCountersignature"));

        results.add(run(policy, "Marketplace observer attempts a waiver",
                waiver(MARKETPLACE, MARKETPLACE_ADMIN, "1000", "USD", NOW, List.of()),
                "ROLE_MAY_NOT_INSTRUCT"));

        results.add(run(policy, "Seller attempts to release funds to itself",
                instruction(SELLER, SELLER_DIRECTOR, Mandate.InstructionType.RELEASE, "10000", "USD", NOW, List.of()),
                "NO_MANDATE_FOR_INSTRUCTION"));

        results.add(run(policy, "Bank ops releases as escrow agent",
                instruction(BANK, BANK_OPS, Mandate.InstructionType.RELEASE, "600000", "USD", NOW, List.of()),
                "Admitted"));

        results.add(run(policy, "Waiver back-dated before buyer held the role",
                waiver(BUYER, BUYER_TREASURY, "5000", "USD", BEFORE_DEAL, List.of()),
                "NO_ROLE_IN_DEAL"));

        results.add(run(policy, "Screening hit on acting party",
                waiver(SANCTIONED, BUYER_TREASURY, "5000", "USD", NOW, List.of()),
                "SCREENING_NOT_CLEAR"));

        results.add(run(policy, "Corporate instructs with no named signatory",
                waiver(BUYER, null, "5000", "USD", NOW, List.of()),
                "SIGNATORY_REQUIRED"));

        print(results);
        long failures = results.stream().filter(r -> !r.passed()).count();
        if (failures > 0) {
            System.out.printf("%n%d scenario(s) failed.%n", failures);
            System.exit(1);
        }
        System.out.printf("%nAll %d scenarios behaved as specified.%n", results.size());
    }

    // ---------------------------------------------------------------- helpers

    private static Instruction waiver(PartyId party, SignatoryId signatory, String amount,
                                      String ccy, Instant at, List<SignatoryId> counter) {
        return instruction(party, signatory, Mandate.InstructionType.WAIVE_DISCREPANCY, amount, ccy, at, counter);
    }

    private static Instruction instruction(PartyId party, SignatoryId signatory,
                                           Mandate.InstructionType type, String amount, String ccy,
                                           Instant at, List<SignatoryId> counter) {
        return new Instruction(
                new InstructionId("INS-" + Math.abs(java.util.UUID.randomUUID().hashCode())),
                DEAL, party, signatory, type, Money.of(amount, ccy), counter, at);
    }

    private static Row run(AuthorityPolicy policy, String name, Instruction instruction, String expected) {
        AdmissionDecision decision = policy.admit(instruction);
        String actual = switch (decision) {
            case AdmissionDecision.Admitted a -> "Admitted";
            case AdmissionDecision.RequiresCountersignature r -> "RequiresCountersignature";
            case AdmissionDecision.Rejected r -> r.reason().name();
        };
        String note = switch (decision) {
            case AdmissionDecision.Admitted a -> a.rationale();
            case AdmissionDecision.RequiresCountersignature r ->
                    "%d of %d signatures".formatted(r.obtained(), r.required());
            case AdmissionDecision.Rejected r -> r.reason().code() + " " + r.detail();
        };
        return new Row(name, expected, actual, note, expected.equals(actual));
    }

    private static void print(List<Row> rows) {
        System.out.printf("%n%-48s %-28s %-28s %s%n", "SCENARIO", "EXPECTED", "ACTUAL", "");
        System.out.println("-".repeat(140));
        for (Row r : rows) {
            System.out.printf("%-48s %-28s %-28s %s %s%n",
                    truncate(r.name(), 47), r.expected(), r.actual(), r.passed() ? "OK  " : "FAIL",
                    truncate(r.note(), 60));
        }
    }

    private static String truncate(String s, int n) {
        return s == null ? "" : (s.length() <= n ? s : s.substring(0, n - 1) + "\u2026");
    }

    private record Row(String name, String expected, String actual, String note, boolean passed) {}

    // ---------------------------------------------------------------- fixtures

    /** In-memory implementations of all three L0 ports. Replaced wholesale in production. */
    static final class Fixtures implements PartyProvider, MandateStore, ScreeningPolicy {

        private final Map<PartyId, Party> partyById = new HashMap<>();
        private final Map<SignatoryId, Instruction.Signatory> signatoryById = new HashMap<>();
        private final List<DealRole> roles = new ArrayList<>();
        private final Map<RoleId, List<Mandate>> mandatesByRole = new HashMap<>();

        Fixtures() {
            party(BUYER, "Selangor Components Sdn Bhd", Party.PartyType.CORPORATE, "MY");
            party(SELLER, "Hanoi Precision Trading JSC", Party.PartyType.CORPORATE, "VN");
            party(BANK, "Standard Chartered Bank (Escrow Agent)", Party.PartyType.BANK_INTERNAL, "SG");
            party(MARKETPLACE, "Proxtera Marketplace Operator", Party.PartyType.CORPORATE, "SG");
            party(SANCTIONED, "Screened Counterparty Ltd", Party.PartyType.CORPORATE, "XX");

            signatory(BUYER_TREASURY, BUYER, "Nurul Hasan, Treasury Manager");
            signatory(BUYER_CFO, BUYER, "Adrian Lim, CFO");
            signatory(SELLER_DIRECTOR, SELLER, "Tran Minh, Director");
            signatory(BANK_OPS, BANK, "Escrow Operations Maker");
            signatory(MARKETPLACE_ADMIN, MARKETPLACE, "Platform Administrator");

            role(BUYER_OBLIGOR, BUYER, DealRole.RoleType.OBLIGOR);
            role(SELLER_OBLIGEE, SELLER, DealRole.RoleType.OBLIGEE);
            role(BANK_AGENT, BANK, DealRole.RoleType.ESCROW_AGENT);
            role(MARKETPLACE_OBSERVER, MARKETPLACE, DealRole.RoleType.OBSERVER);

            // Buyer: single-signature waivers to USD 50k, dual-signature waivers without limit.
            mandate(BUYER_OBLIGOR, Mandate.InstructionType.WAIVE_DISCREPANCY,
                    Money.of("50000", "USD"), Mandate.Quorum.single());
            mandate(BUYER_OBLIGOR, Mandate.InstructionType.WAIVE_DISCREPANCY,
                    null, Mandate.Quorum.dual());
            mandate(BUYER_OBLIGOR, Mandate.InstructionType.FUND, null, Mandate.Quorum.single());

            // Seller may only instruct amendments; it cannot release to itself.
            // Deliberately capped in USD only: there is no wider fallback mandate behind it.
            mandate(SELLER_OBLIGEE, Mandate.InstructionType.AMEND_OBLIGATION,
                    Money.of("25000", "USD"), Mandate.Quorum.single());

            // The bank participates as a party, bound by the same rules as any counterparty.
            mandate(BANK_AGENT, Mandate.InstructionType.RELEASE, null, Mandate.Quorum.single());
            mandate(BANK_AGENT, Mandate.InstructionType.REFUND, null, Mandate.Quorum.single());
        }

        private void party(PartyId id, String name, Party.PartyType type, String jurisdiction) {
            partyById.put(id, new Party(id, name, type, jurisdiction,
                    List.of(new Party.PartyIdentifier("LEI", "LEI-" + id.value())),
                    Party.PartyStatus.ACTIVE, ONBOARDED));
        }

        private void signatory(SignatoryId id, PartyId party, String name) {
            signatoryById.put(id, new Instruction.Signatory(id, party, name,
                    ONBOARDED, null));
        }

        private void role(RoleId id, PartyId party, DealRole.RoleType type) {
            roles.add(new DealRole(id, DEAL, party, type, DEAL_START, null));
        }

        private void mandate(RoleId role, Mandate.InstructionType type, Money ceiling, Mandate.Quorum quorum) {
            mandatesByRole.computeIfAbsent(role, k -> new ArrayList<>())
                    .add(new Mandate(role, type, ceiling, quorum, null, DEAL_START, null));
        }

        @Override
        public Optional<Party> findParty(PartyId id, Instant at) {
            return Optional.ofNullable(partyById.get(id));
        }

        @Override
        public Optional<Instruction.Signatory> findSignatory(SignatoryId id, Instant at) {
            return Optional.ofNullable(signatoryById.get(id));
        }

        @Override
        public List<DealRole> rolesFor(DealId dealId, PartyId partyId, Instant at) {
            return roles.stream()
                    .filter(r -> r.dealId().equals(dealId) && r.partyId().equals(partyId))
                    .toList();
        }

        @Override
        public List<Mandate> mandatesFor(RoleId roleId, Instant at) {
            return mandatesByRole.getOrDefault(roleId, List.of());
        }

        @Override
        public ScreeningResult screen(PartyId partyId, Instant at) {
            return partyId.equals(SANCTIONED) ? ScreeningResult.HIT : ScreeningResult.CLEAR;
        }
    }
}
