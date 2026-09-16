package com.sc.verdict.contract;

import com.sc.verdict.shared.Ids.DealId;

import java.util.Optional;

/**
 * L1 — the contract registry, thin. It resolves a deal to the versioned terms and obligations the
 * decision plane examines. In the hackathon it is backed by fixtures; in production it is the seam
 * to the contract store. Versioning and clause references are modelled; drafting and redlining are
 * not (HLD §9).
 */
public interface ContractRegistry {

    Optional<DealDefinition> findDeal(DealId dealId);
}
