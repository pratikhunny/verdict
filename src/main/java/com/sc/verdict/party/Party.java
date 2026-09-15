package com.sc.verdict.party;

import com.sc.verdict.shared.Money;
import com.sc.verdict.shared.Ids.PartyId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A legal entity or natural person known to the platform, independent of any deal.
 *
 * <p>The escrow agent (the bank) is itself a {@code Party} with type {@link PartyType#BANK_INTERNAL}
 * and participates in deals under the same authority rules as any counterparty. The <em>platform
 * operator</em> — who configures rulesets and onboards clients — is a separate concern and is not
 * modelled here; platform authority and deal authority are distinct planes and never cross.
 *
 * <p>Identifiers are held as a list rather than fixed columns because schemes vary by jurisdiction
 * (LEI for a Malaysian corporate, business registration number for a Vietnamese SME) and new
 * schemes appear without notice.
 */
public record Party(
        PartyId id,
        String legalName,
        PartyType type,
        String jurisdiction,
        List<PartyIdentifier> identifiers,
        PartyStatus status,
        Instant statusEffectiveFrom) {

    public Party {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(statusEffectiveFrom, "statusEffectiveFrom");
        if (legalName == null || legalName.isBlank()) {
            throw new IllegalArgumentException("legalName must not be blank");
        }
        identifiers = List.copyOf(identifiers == null ? List.of() : identifiers);
    }

    /**
     * Whether this party may participate in instructions as at {@code at}.
     *
     * <p>Status is effective-dated so that a release adjudicated today against evidence from six
     * months ago resolves against the status that applied <em>then</em>, not the status now.
     */
    public boolean isActiveAt(Instant at) {
        return status == PartyStatus.ACTIVE && !at.isBefore(statusEffectiveFrom);
    }

    public Optional<PartyIdentifier> identifier(String scheme) {
        return identifiers.stream().filter(i -> i.scheme().equalsIgnoreCase(scheme)).findFirst();
    }

    public enum PartyType {
        /** Incorporated entity; acts through {@link Signatory} natural persons. */
        CORPORATE,
        /** Natural person acting in their own right. */
        INDIVIDUAL,
        /** The bank acting as escrow agent, trustee, or account bank. */
        BANK_INTERNAL,
        /** Marketplace operator, inspection agency, court — participates but holds no funds. */
        THIRD_PARTY_AGENT
    }

    public enum PartyStatus {
        DRAFT,
        ACTIVE,
        /** Retained in deals but may not instruct; typically a screening or credit event. */
        SUSPENDED,
        EXITED
    }

    /** An external identity assertion, e.g. {@code ("LEI", "5493001KJTIIGC8Y1R12")}. */
    public record PartyIdentifier(String scheme, String value) {
        public PartyIdentifier {
            Objects.requireNonNull(scheme, "scheme");
            Objects.requireNonNull(value, "value");
        }
    }
}
