package com.sc.verdict.evidence;

/**
 * The facts an extraction adapter may assert about a submission. Each key maps to exactly one
 * {@link com.sc.verdict.contract.Condition.Kind} the examination engine evaluates.
 *
 * <p>{@link #APPROVAL_GRANTED} is the key that closes the loop in ADR terms: a counterparty approval,
 * once admitted by the authority gate, re-enters the engine as an ordinary fact rather than
 * bypassing it. The engine does not know or care that a human produced it — it is evidence like any
 * other, with provenance and full confidence.
 */
public enum FactKey {
    /** Whether the transport document (eBL) is present. Value: {@code "true"}/{@code "false"}. */
    EBL_PRESENT,
    /** The quantity the evidence attests to. Value: an integer count of units. */
    EVIDENCED_QUANTITY,
    /** The goods description on the evidence. Value: free text. */
    GOODS_DESCRIPTION,
    /** The shipment date on the transport document. Value: ISO-8601 date. */
    SHIPMENT_DATE,
    /** A finding grade approved by an entitled party. Value: the {@code FindingGrade} name. */
    APPROVAL_GRANTED
}
