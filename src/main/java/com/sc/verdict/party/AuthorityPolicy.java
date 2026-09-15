package com.sc.verdict.party;

import com.sc.verdict.shared.Money;
import com.sc.verdict.party.AdmissionDecision.Reason;
import com.sc.verdict.shared.Ids.RoleId;
import com.sc.verdict.party.Ports.MandateStore;
import com.sc.verdict.party.Ports.PartyProvider;
import com.sc.verdict.party.Ports.ScreeningPolicy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The single admission gate for L0.
 *
 * <p><strong>Invariant:</strong> no instruction is admitted unless a mandate resolves for
 * (party, role, deal, instruction type, amount) as at the instruction's effective instant.
 * Every layer that changes state — case management, ledger, settlement — calls
 * {@link #admit(Instruction)} first. There is no internal path and no bypass.
 *
 * <p>The policy is a pure function of its inputs and the as-of state returned by its ports. It
 * reads no clock, holds no mutable state, and is safe for concurrent use. This is what makes an
 * admission replayable: given the same journal entry, it yields the same decision indefinitely.
 *
 * <p>Where several roles could authorise the same instruction, the policy selects the
 * <em>least-privileged sufficient</em> mandate — the one with the lowest ceiling that still covers
 * the amount. Selecting the widest mandate would silently launder a small instruction through a
 * large authority and misreport in the journal which authority was actually exercised.
 */
public final class AuthorityPolicy {

    private final PartyProvider parties;
    private final MandateStore mandates;
    private final ScreeningPolicy screening;

    public AuthorityPolicy(PartyProvider parties, MandateStore mandates, ScreeningPolicy screening) {
        this.parties = Objects.requireNonNull(parties, "parties");
        this.mandates = Objects.requireNonNull(mandates, "mandates");
        this.screening = Objects.requireNonNull(screening, "screening");
    }

    /**
     * Decide whether an instruction may proceed.
     *
     * <p>Checks run in escalating order of cost and specificity, and the <em>first</em> failure is
     * returned. This is deliberate: reporting one reason keeps rejection codes stable and avoids
     * disclosing the mandate structure of a deal to a party that failed the identity gate.
     *
     * @param instruction the request, carrying its own effective instant for temporal resolution
     * @return {@link AdmissionDecision.Admitted}, {@link AdmissionDecision.RequiresCountersignature},
     *         or {@link AdmissionDecision.Rejected} with a stable code
     * @throws NullPointerException if {@code instruction} is null
     */
    public AdmissionDecision admit(Instruction instruction) {
        Objects.requireNonNull(instruction, "instruction");
        final Instant at = instruction.effectiveAt();

        // 1. Identity: the party must exist and be active as at the effective instant.
        Optional<Party> maybeParty = parties.findParty(instruction.actingParty(), at);
        if (maybeParty.isEmpty()) {
            return AdmissionDecision.reject(Reason.PARTY_UNKNOWN,
                    "No party " + instruction.actingParty() + " as at " + at);
        }
        Party party = maybeParty.get();
        if (!party.isActiveAt(at)) {
            return AdmissionDecision.reject(Reason.PARTY_NOT_ACTIVE,
                    "Party " + party.id() + " status " + party.status() + " as at " + at);
        }

        // 2. Financial crime gate. Fails closed: PENDING is not CLEAR.
        var screeningResult = screening.screen(party.id(), at);
        if (screeningResult != ScreeningPolicy.ScreeningResult.CLEAR) {
            return AdmissionDecision.reject(Reason.SCREENING_NOT_CLEAR,
                    "Screening returned " + screeningResult + " for " + party.id());
        }

        // 3. Signatory binding. A corporate cannot act except through a named natural person.
        var signatoryCheck = checkSignatory(party, instruction, at);
        if (signatoryCheck.isPresent()) {
            return signatoryCheck.get();
        }

        // 4. Role: the party must have held an instructing role in this deal at that instant.
        List<DealRole> roles = mandates.rolesFor(instruction.dealId(), party.id(), at).stream()
                .filter(r -> r.isEffectiveAt(at))
                .toList();
        if (roles.isEmpty()) {
            return AdmissionDecision.reject(Reason.NO_ROLE_IN_DEAL,
                    "Party " + party.id() + " held no role in " + instruction.dealId() + " at " + at);
        }
        List<DealRole> instructingRoles = roles.stream()
                .filter(r -> !AdmissionDecision.NON_INSTRUCTING_ROLES.contains(r.role()))
                .toList();
        if (instructingRoles.isEmpty()) {
            return AdmissionDecision.reject(Reason.ROLE_MAY_NOT_INSTRUCT,
                    "Only non-instructing roles held: " + roles.stream().map(DealRole::role).toList());
        }

        // 5. Mandate: gather every candidate, then pick the least-privileged sufficient one.
        var candidates = new ArrayList<Candidate>();
        Reason nearestFailure = Reason.NO_MANDATE_FOR_INSTRUCTION;
        String nearestDetail = "No mandate for " + instruction.type() + " on any held role";

        for (DealRole role : instructingRoles) {
            for (Mandate mandate : mandates.mandatesFor(role.id(), at)) {
                if (mandate.instructionType() != instruction.type() || !mandate.isEffectiveAt(at)) {
                    continue;
                }
                if (!delegationIntact(mandate, at)) {
                    nearestFailure = Reason.DELEGATION_CHAIN_BROKEN;
                    nearestDetail = "Delegation source " + mandate.delegatedFrom() + " not effective at " + at;
                    continue;
                }
                var ceilingCheck = withinCeiling(mandate, instruction);
                if (ceilingCheck.isPresent()) {
                    nearestFailure = ceilingCheck.get().reason();
                    nearestDetail = ceilingCheck.get().detail();
                    continue;
                }
                candidates.add(new Candidate(role, mandate));
            }
        }

        if (candidates.isEmpty()) {
            return AdmissionDecision.reject(nearestFailure, nearestDetail);
        }

        Candidate chosen = leastPrivilegedSufficient(candidates);

        // 6. Quorum. Distinct signers only — one person cannot satisfy dual control alone.
        int obtained = instruction.distinctSigners().size();
        int required = chosen.mandate().quorum().required();
        if (obtained < required) {
            return new AdmissionDecision.RequiresCountersignature(chosen.role().id(), required, obtained);
        }

        return new AdmissionDecision.Admitted(chosen.role().id(),
                "%s under %s mandate%s".formatted(
                        chosen.role().role(),
                        instruction.type(),
                        chosen.mandate().ceilingIfAny().map(c -> " capped at " + c).orElse(" (uncapped)")));
    }

    private Optional<AdmissionDecision> checkSignatory(Party party, Instruction instruction, Instant at) {
        boolean actsThroughPeople = party.type() == Party.PartyType.CORPORATE
                || party.type() == Party.PartyType.BANK_INTERNAL;
        if (!actsThroughPeople) {
            return Optional.empty();
        }
        var actingSignatory = instruction.actingSignatoryIfAny();
        if (actingSignatory.isEmpty()) {
            return Optional.of(AdmissionDecision.reject(Reason.SIGNATORY_REQUIRED,
                    "Party type " + party.type() + " must instruct through a named signatory"));
        }
        // Every signer — acting and countersigning — must be bound to this party and effective.
        for (var signerId : instruction.distinctSigners()) {
            var signatory = parties.findSignatory(signerId, at);
            if (signatory.isEmpty()
                    || !signatory.get().partyId().equals(party.id())
                    || !signatory.get().isEffectiveAt(at)) {
                return Optional.of(AdmissionDecision.reject(Reason.SIGNATORY_NOT_BOUND,
                        "Signatory " + signerId + " not bound to " + party.id() + " at " + at));
            }
        }
        return Optional.empty();
    }

    /** A delegated mandate cannot outlive the authority it derives from. */
    private boolean delegationIntact(Mandate mandate, Instant at) {
        return mandate.delegationSource()
                .map(source -> mandates.mandatesFor(source, at).stream()
                        .anyMatch(m -> m.instructionType() == mandate.instructionType() && m.isEffectiveAt(at)))
                .orElse(true);
    }

    private Optional<AdmissionDecision.Rejected> withinCeiling(Mandate mandate, Instruction instruction) {
        var ceiling = mandate.ceilingIfAny();
        var amount = instruction.amountIfAny();
        if (ceiling.isEmpty()) {
            return Optional.empty();
        }
        if (amount.isEmpty()) {
            // A capped mandate cannot authorise an unquantified instruction: the cap is unverifiable.
            return Optional.of(new AdmissionDecision.Rejected(Reason.CEILING_EXCEEDED,
                    "Capped mandate cannot authorise an instruction carrying no amount"));
        }
        if (!ceiling.get().currency().equals(amount.get().currency())) {
            return Optional.of(new AdmissionDecision.Rejected(Reason.CURRENCY_MISMATCH,
                    "Mandate ceiling %s cannot authorise %s".formatted(ceiling.get(), amount.get())));
        }
        if (amount.get().compareTo(ceiling.get()) > 0) {
            return Optional.of(new AdmissionDecision.Rejected(Reason.CEILING_EXCEEDED,
                    "Amount %s exceeds ceiling %s".formatted(amount.get(), ceiling.get())));
        }
        return Optional.empty();
    }

    /**
     * Prefer the tightest mandate that still authorises the instruction, so the journal records the
     * narrowest authority actually exercised. Uncapped mandates rank last.
     */
    private Candidate leastPrivilegedSufficient(List<Candidate> candidates) {
        return candidates.stream()
                .min((a, b) -> {
                    var ca = a.mandate().ceilingIfAny();
                    var cb = b.mandate().ceilingIfAny();
                    if (ca.isEmpty() && cb.isEmpty()) return 0;
                    if (ca.isEmpty()) return 1;
                    if (cb.isEmpty()) return -1;
                    if (!ca.get().currency().equals(cb.get().currency())) return 0;
                    return ca.get().compareTo(cb.get());
                })
                .orElseThrow();
    }

    private record Candidate(DealRole role, Mandate mandate) {}
}
