package com.sc.verdict.shared;

import java.util.Objects;

/**
 * Typed identifiers. Passing a {@code DealId} where a {@code PartyId} is expected is a
 * compile error rather than a production incident.
 */
public final class Ids {

    private Ids() {}

    private static String require(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public record PartyId(String value) {
        public PartyId { value = require(value, "partyId"); }
        @Override public String toString() { return value; }
    }

    public record DealId(String value) {
        public DealId { value = require(value, "dealId"); }
        @Override public String toString() { return value; }
    }

    public record RoleId(String value) {
        public RoleId { value = require(value, "roleId"); }
        @Override public String toString() { return value; }
    }

    /** A natural person acting for a corporate party. */
    public record SignatoryId(String value) {
        public SignatoryId { value = require(value, "signatoryId"); }
        @Override public String toString() { return value; }
    }

    /** Client-supplied idempotency key for a single instruction attempt. */
    public record InstructionId(String value) {
        public InstructionId { value = require(value, "instructionId"); }
        @Override public String toString() { return value; }
    }

    // ---- decision plane ----

    public record MilestoneId(String value) {
        public MilestoneId { value = require(value, "milestoneId"); }
        @Override public String toString() { return value; }
    }

    public record ConditionId(String value) {
        public ConditionId { value = require(value, "conditionId"); }
        @Override public String toString() { return value; }
    }

    public record ObligationId(String value) {
        public ObligationId { value = require(value, "obligationId"); }
        @Override public String toString() { return value; }
    }

    public record ContractVersionId(String value) {
        public ContractVersionId { value = require(value, "contractVersionId"); }
        @Override public String toString() { return value; }
    }

    public record SubmissionId(String value) {
        public SubmissionId { value = require(value, "submissionId"); }
        @Override public String toString() { return value; }
    }

    public record DeterminationId(String value) {
        public DeterminationId { value = require(value, "determinationId"); }
        @Override public String toString() { return value; }
    }
}
