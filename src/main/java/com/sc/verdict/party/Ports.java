package com.sc.verdict.party;

import com.sc.verdict.shared.Money;
import com.sc.verdict.shared.Ids.DealId;
import com.sc.verdict.shared.Ids.PartyId;
import com.sc.verdict.shared.Ids.RoleId;
import com.sc.verdict.shared.Ids.SignatoryId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The substitutable seams of L0. Each is the single file that changes to go from demo fixtures to
 * bank systems: {@link PartyProvider} to client master / CIF, {@link ScreeningPolicy} to the
 * sanctions and CDD stack, {@link MandateStore} to the deal configuration store.
 *
 * <p>All lookups are as-of an {@link Instant} rather than "current", so that any admission decision
 * is reproducible from the decision journal.
 */
public final class Ports {

    private Ports() {}

    public interface PartyProvider {
        Optional<Party> findParty(PartyId id, Instant at);

        Optional<Instruction.Signatory> findSignatory(SignatoryId id, Instant at);
    }

    public interface MandateStore {
        /** All roles the party held in the deal as at {@code at}. May be several. */
        List<DealRole> rolesFor(DealId dealId, PartyId partyId, Instant at);

        /** Mandates attached to a role that are effective as at {@code at}. */
        List<Mandate> mandatesFor(RoleId roleId, Instant at);
    }

    /**
     * A gate, not a field. The call site is real even when the implementation is stubbed, so the
     * control point survives into production rather than being retrofitted.
     */
    public interface ScreeningPolicy {
        ScreeningResult screen(PartyId partyId, Instant at);

        enum ScreeningResult {
            CLEAR,
            /** Positive match requiring investigation. Instructions are refused. */
            HIT,
            /** Screening incomplete. Refused rather than assumed clear — fail closed. */
            PENDING
        }
    }
}
