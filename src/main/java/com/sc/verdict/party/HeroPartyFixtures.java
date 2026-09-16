package com.sc.verdict.party;

import com.sc.verdict.party.Ports.MandateStore;
import com.sc.verdict.party.Ports.PartyProvider;
import com.sc.verdict.party.Ports.ScreeningPolicy;
import com.sc.verdict.shared.Ids.DealId;
import com.sc.verdict.shared.Ids.PartyId;
import com.sc.verdict.shared.Ids.RoleId;
import com.sc.verdict.shared.Ids.SignatoryId;
import com.sc.verdict.shared.Money;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The hero deal's parties, mandates and signatories as an in-memory implementation of the three L0
 * ports — the same fixture the authority scenarios use, promoted to main so the orchestrator and the
 * demo can drive the real admission gate rather than a stub. Party ids match
 * {@code HeroDealRegistry} by value without importing it, so L0 stays independent of L1.
 *
 * <p>The mandate of interest for the demo is the buyer's: a Treasury Manager may waive discrepancies
 * up to USD 50k single-handed; above that, two signatories. Pack 4's USD 600k approval therefore
 * needs the CFO to countersign — L0 doing real work mid-demo.
 */
public final class HeroPartyFixtures implements PartyProvider, MandateStore, ScreeningPolicy {

    public static final DealId DEAL = new DealId("DEAL-2026-0417");

    public static final PartyId BUYER = new PartyId("PTY-BUYER-MY");
    public static final PartyId SUPPLIER = new PartyId("PTY-SELLER-VN");
    public static final PartyId BANK = new PartyId("PTY-SCB-AGENT");
    public static final PartyId MARKETPLACE = new PartyId("PTY-MARKETPLACE");

    public static final SignatoryId BUYER_TREASURY = new SignatoryId("SIG-BUYER-TREASURY");
    public static final SignatoryId BUYER_CFO = new SignatoryId("SIG-BUYER-CFO");
    public static final SignatoryId SUPPLIER_DIRECTOR = new SignatoryId("SIG-SELLER-DIRECTOR");
    public static final SignatoryId BANK_OPS = new SignatoryId("SIG-BANK-OPS");

    public static final RoleId BUYER_OBLIGOR = new RoleId("ROLE-BUYER-OBLIGOR");
    public static final RoleId SUPPLIER_OBLIGEE = new RoleId("ROLE-SELLER-OBLIGEE");
    public static final RoleId BANK_AGENT = new RoleId("ROLE-BANK-AGENT");
    public static final RoleId MARKETPLACE_OBSERVER = new RoleId("ROLE-MARKETPLACE-OBSERVER");

    private static final Instant ONBOARDED = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant DEAL_START = Instant.parse("2026-06-01T00:00:00Z");

    private final Map<PartyId, Party> partyById = new HashMap<>();
    private final Map<SignatoryId, Instruction.Signatory> signatoryById = new HashMap<>();
    private final List<DealRole> roles = new ArrayList<>();
    private final Map<RoleId, List<Mandate>> mandatesByRole = new HashMap<>();

    public HeroPartyFixtures() {
        party(BUYER, "Selangor Components Sdn Bhd", Party.PartyType.CORPORATE, "MY");
        party(SUPPLIER, "Hanoi Precision Trading JSC", Party.PartyType.CORPORATE, "VN");
        party(BANK, "Standard Chartered Bank (Escrow Agent)", Party.PartyType.BANK_INTERNAL, "SG");
        party(MARKETPLACE, "Marketplace Operator", Party.PartyType.CORPORATE, "SG");

        signatory(BUYER_TREASURY, BUYER, "Nurul Hasan, Treasury Manager");
        signatory(BUYER_CFO, BUYER, "Adrian Lim, CFO");
        signatory(SUPPLIER_DIRECTOR, SUPPLIER, "Tran Minh, Director");
        signatory(BANK_OPS, BANK, "Escrow Operations Maker");

        role(BUYER_OBLIGOR, BUYER, DealRole.RoleType.OBLIGOR);
        role(SUPPLIER_OBLIGEE, SUPPLIER, DealRole.RoleType.OBLIGEE);
        role(BANK_AGENT, BANK, DealRole.RoleType.ESCROW_AGENT);
        role(MARKETPLACE_OBSERVER, MARKETPLACE, DealRole.RoleType.OBSERVER);

        // Buyer: single-signature waivers to USD 50k, dual-signature waivers without limit.
        mandate(BUYER_OBLIGOR, Mandate.InstructionType.WAIVE_DISCREPANCY, Money.of("50000", "USD"), Mandate.Quorum.single());
        mandate(BUYER_OBLIGOR, Mandate.InstructionType.WAIVE_DISCREPANCY, null, Mandate.Quorum.dual());
        mandate(BUYER_OBLIGOR, Mandate.InstructionType.FUND, null, Mandate.Quorum.single());

        // Bank as escrow agent releases and refunds.
        mandate(BANK_AGENT, Mandate.InstructionType.RELEASE, null, Mandate.Quorum.single());
        mandate(BANK_AGENT, Mandate.InstructionType.REFUND, null, Mandate.Quorum.single());
    }

    private void party(PartyId id, String name, Party.PartyType type, String jurisdiction) {
        partyById.put(id, new Party(id, name, type, jurisdiction,
                List.of(new Party.PartyIdentifier("LEI", "LEI-" + id.value())),
                Party.PartyStatus.ACTIVE, ONBOARDED));
    }

    private void signatory(SignatoryId id, PartyId party, String name) {
        signatoryById.put(id, new Instruction.Signatory(id, party, name, ONBOARDED, null));
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
        return roles.stream().filter(r -> r.dealId().equals(dealId) && r.partyId().equals(partyId)).toList();
    }

    @Override
    public List<Mandate> mandatesFor(RoleId roleId, Instant at) {
        return mandatesByRole.getOrDefault(roleId, List.of());
    }

    @Override
    public ScreeningResult screen(PartyId partyId, Instant at) {
        return ScreeningResult.CLEAR;
    }
}
