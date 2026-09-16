package com.sc.verdict.contract;

import com.sc.verdict.shared.Ids.MilestoneId;

import java.util.List;
import java.util.Objects;

/**
 * A trigger: the event whose evidence, once it satisfies the milestone's conditions, discharges the
 * obligations attached to it.
 *
 * <p>The load-bearing distinction of the whole product lives here (glossary, ADR-003): a milestone
 * is a <em>trigger</em>, an obligation is an <em>entitlement</em>, and one milestone discharges
 * several obligations — supplier tranche, marketplace commission, bank fee. A system that collapses
 * the two cannot express a multi-payee split or a clean partial release.
 */
public record Milestone(MilestoneId id, String name, List<Condition> conditions) {

    public Milestone {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(conditions, "conditions");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("milestone name must not be blank");
        }
        conditions = List.copyOf(conditions);
    }
}
