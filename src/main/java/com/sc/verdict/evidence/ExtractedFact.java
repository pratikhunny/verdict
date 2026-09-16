package com.sc.verdict.evidence;

import com.sc.verdict.shared.Ids.DocumentId;
import com.sc.verdict.shared.Versions.AdapterVersion;

import java.util.Objects;

/**
 * An assertion by an extraction adapter: a fact, the confidence with which it is asserted, and the
 * document it came from.
 *
 * <p>This is the L3 → L4 boundary made concrete (ADR-002). An adapter <em>asserts</em>; it never
 * decides. The value is carried as text — the exact bytes that were hashed — so that the fact
 * replays without re-parsing model output, and so the examination engine, not the adapter, owns
 * every interpretation of it.
 *
 * <p>{@code confidence} is provenance the engine may act on (fail closed below a threshold), not a
 * decision. {@code sourceHash} plus {@code adapterVersion} are what a replay pins: the same bytes,
 * read by the same adapter version, are the same fact.
 */
public record ExtractedFact(
        FactKey key,
        String value,
        double confidence,
        DocumentId sourceDocument,
        String sourceHash,
        AdapterVersion adapterVersion) {

    public ExtractedFact {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(sourceDocument, "sourceDocument");
        Objects.requireNonNull(sourceHash, "sourceHash");
        Objects.requireNonNull(adapterVersion, "adapterVersion");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be in [0,1]: " + confidence);
        }
    }

    public boolean asBoolean() {
        return Boolean.parseBoolean(value);
    }

    public long asLong() {
        return Long.parseLong(value.trim());
    }
}
