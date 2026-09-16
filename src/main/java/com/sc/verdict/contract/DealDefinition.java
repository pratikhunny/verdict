package com.sc.verdict.contract;

import com.sc.verdict.shared.Ids.ContractVersionId;
import com.sc.verdict.shared.Ids.DealId;
import com.sc.verdict.shared.Ids.MilestoneId;
import com.sc.verdict.shared.Money;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * The examined contract terms of one deal, as at one contract version.
 *
 * <p>This is the immutable decision-plane input the examination engine reads: the contract value the
 * entitlements scale against, the terms the conditions compare evidence to (contracted quantity,
 * goods description, latest shipment date), the milestone under examination, and the obligations it
 * triggers. It is L1 (registry) and L2 (obligation model) collapsed to the slice the engine needs;
 * redlining, drafting and amendment history are out of hackathon scope (HLD §9).
 */
public record DealDefinition(
        DealId dealId,
        ContractVersionId contractVersionId,
        Money contractValue,
        long contractedQuantity,
        String goodsDescription,
        LocalDate latestShipmentDate,
        Milestone shipmentMilestone,
        List<Obligation> obligations) {

    public DealDefinition {
        Objects.requireNonNull(dealId, "dealId");
        Objects.requireNonNull(contractVersionId, "contractVersionId");
        Objects.requireNonNull(contractValue, "contractValue");
        Objects.requireNonNull(goodsDescription, "goodsDescription");
        Objects.requireNonNull(latestShipmentDate, "latestShipmentDate");
        Objects.requireNonNull(shipmentMilestone, "shipmentMilestone");
        Objects.requireNonNull(obligations, "obligations");
        if (contractedQuantity <= 0) {
            throw new IllegalArgumentException("contractedQuantity must be positive");
        }
        obligations = List.copyOf(obligations);
    }

    /** Obligations discharged by the given milestone — the multi-payee set, in declared order. */
    public List<Obligation> obligationsFor(MilestoneId milestoneId) {
        return obligations.stream().filter(o -> o.milestoneId().equals(milestoneId)).toList();
    }
}
