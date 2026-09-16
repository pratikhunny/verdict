package com.sc.verdict.evidence;

import com.sc.verdict.shared.Hashing;
import com.sc.verdict.shared.Ids.DealId;
import com.sc.verdict.shared.Ids.DocumentId;
import com.sc.verdict.shared.Ids.SubmissionId;
import com.sc.verdict.shared.Versions.AdapterVersion;

import java.time.Instant;
import java.util.List;

/**
 * The fixed-corpus extraction adapter used for the demo. It returns pre-baked facts with pre-baked
 * confidences so that the four packs are deterministic on stage — extraction is the one thing you do
 * not want to be live during a ten-minute demo (scenarios §Implementation notes).
 *
 * <p>What is <em>not</em> faked: the document hashes are real SHA-256 over canonical content, the
 * provenance is real, and the fact/confidence contract is exactly the one a production document-AI
 * adapter would satisfy. Replacing this class with that adapter changes nothing the engine sees.
 */
public final class FixtureExtraction implements ExtractionAdapter {

    private static final AdapterVersion VERSION = new AdapterVersion("EXT-2026.09-v1");

    @Override
    public AdapterVersion version() {
        return VERSION;
    }

    @Override
    public Submission extract(EvidencePack pack, DealId dealId, Instant at) {
        return switch (pack) {
            case CLEAN -> submission(pack, dealId, at, 1000, "tablet computers", "2026-09-08", 0.99);
            case COSMETIC_VARIANCE -> submission(pack, dealId, at, 1000, "tablet PCs", "2026-09-08", 0.94);
            case SHORT_SHIPMENT -> submission(pack, dealId, at, 800, "tablet computers", "2026-09-08", 0.99);
            case LATE_SHIPMENT -> submission(pack, dealId, at, 1000, "tablet computers", "2026-09-13", 0.99);
        };
    }

    private Submission submission(EvidencePack pack, DealId dealId, Instant at,
                                  long quantity, String goods, String shipDate, double goodsConfidence) {
        String suffix = pack.name();

        String eblContent = "eBL|units=%d|goods=%s|shipped=%s".formatted(quantity, goods, shipDate);
        String invContent = "commercial-invoice|units=%d|goods=%s".formatted(quantity, goods);
        String pkgContent = "packing-list|units=%d".formatted(quantity);

        Document ebl = new Document(new DocumentId("DOC-EBL-" + suffix), "eBL", Hashing.sha256Hex(eblContent));
        Document inv = new Document(new DocumentId("DOC-INV-" + suffix), "commercial-invoice", Hashing.sha256Hex(invContent));
        Document pkg = new Document(new DocumentId("DOC-PKG-" + suffix), "packing-list", Hashing.sha256Hex(pkgContent));

        List<ExtractedFact> facts = List.of(
                new ExtractedFact(FactKey.EBL_PRESENT, "true", 0.99, ebl.id(), ebl.contentHash(), VERSION),
                new ExtractedFact(FactKey.EVIDENCED_QUANTITY, Long.toString(quantity), 0.99, ebl.id(), ebl.contentHash(), VERSION),
                new ExtractedFact(FactKey.GOODS_DESCRIPTION, goods, goodsConfidence, ebl.id(), ebl.contentHash(), VERSION),
                new ExtractedFact(FactKey.SHIPMENT_DATE, shipDate, 0.99, ebl.id(), ebl.contentHash(), VERSION));

        return new Submission(new SubmissionId("SUB-" + suffix), dealId, List.of(ebl, inv, pkg), facts, at);
    }
}
