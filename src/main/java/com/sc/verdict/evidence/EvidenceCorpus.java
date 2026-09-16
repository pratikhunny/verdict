package com.sc.verdict.evidence;

import java.util.List;

/**
 * The demo document corpus as prose — the text an extraction adapter actually reads. The fixture
 * adapter returns pre-baked facts for determinism; the live LLM adapter reads these same documents
 * and extracts the facts itself. Keeping the corpus here, framework-free, means both adapters read
 * "the same documents" and the only difference on stage is who did the reading.
 */
public final class EvidenceCorpus {

    /** One document reduced to its identity, type and readable text. */
    public record DocText(String id, String type, String content) {}

    /** Construction-retention (RERA) documents — engineer completion certificates — per scenario. */
    public List<DocText> constructionDocumentsFor(String scenario) {
        String s = scenario.toUpperCase();
        String header = """
                ENGINEER COMPLETION CERTIFICATE
                Project: Marina Heights, Tower B      RERA Reg: RERA-2026-MH-0007
                Certifying engineer: R. Iyer, Chartered Engineer (Licence CE-4471)
                Milestone: Structure
                """;
        String content = switch (s) {
            case "CLEAN" -> header + "Certified completion: 45%\nMilestone clear for disbursement.\n";
            case "INCOMPLETE" -> header + "Certified completion: 30%\nWork continues; not yet at milestone.\n";
            case "OBJECTION" -> header + "Certified completion: 45%\n"
                    + "NOTE: An objection / lien has been filed against this milestone by the buyers' association.\n";
            case "MISSING_CERT" -> """
                    DISBURSEMENT REQUEST
                    Project: Marina Heights, Tower B
                    The developer requests release of the structure-milestone tranche.
                    Supporting proofs to follow.
                    """;
            default -> throw new IllegalArgumentException("unknown construction scenario: " + scenario);
        };
        String type = s.equals("MISSING_CERT") ? "disbursement-request" : "completion-certificate";
        return List.of(new DocText("DOC-CERT-" + s, type, content));
    }

    /** The three documents of one evidence pack, as text. */
    public List<DocText> documentsFor(EvidencePack pack) {
        return switch (pack) {
            case CLEAN -> pack(pack, 1000, "tablet computers", "08 September 2026");
            case COSMETIC_VARIANCE -> pack(pack, 1000, "tablet PCs", "08 September 2026");
            case SHORT_SHIPMENT -> pack(pack, 800, "tablet computers", "08 September 2026");
            case LATE_SHIPMENT -> pack(pack, 1000, "tablet computers", "13 September 2026");
        };
    }

    private List<DocText> pack(EvidencePack pack, int units, String goods, String shippedOnBoard) {
        String s = pack.name();
        String ebl = """
                ELECTRONIC BILL OF LADING
                Carrier: Pacific Star Lines          B/L No: PSL-2026-%s
                Port of loading: Ho Chi Minh City    Port of discharge: Port Klang
                Shipped on board: %s
                Description of goods: %,d units, %s.
                Freight prepaid. Clean on board.
                """.formatted(s, shippedOnBoard, units, goods);
        String invoice = """
                COMMERCIAL INVOICE   No. INV-2026-%s
                Seller: Hanoi Precision Trading JSC   Buyer: Selangor Components Sdn Bhd
                Goods: %s
                Quantity: %,d units
                """.formatted(s, goods, units);
        String packing = """
                PACKING LIST   Ref PL-2026-%s
                %,d units, packed across %d cartons, CIF Port Klang.
                """.formatted(s, units, Math.max(1, units / 10));
        return List.of(
                new DocText("DOC-EBL-" + s, "eBL", ebl),
                new DocText("DOC-INV-" + s, "commercial-invoice", invoice),
                new DocText("DOC-PKG-" + s, "packing-list", packing));
    }
}
