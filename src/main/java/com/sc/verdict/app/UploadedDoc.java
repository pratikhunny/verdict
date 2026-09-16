package com.sc.verdict.app;

import com.sc.verdict.shared.Hashing;

/**
 * A document uploaded (or loaded as a sample) into a transaction: its filename, an inferred type,
 * the extracted text an extractor reads, and a content hash for provenance. Both the deterministic
 * parser and the LLM read {@link #text()} — the only difference on stage is who does the reading.
 */
public record UploadedDoc(String id, String filename, String type, String text, int sizeBytes) {

    public String contentHash() {
        return Hashing.sha256Hex(text);
    }

    /** Infer a document type from the text, so the UI can label an uploaded file sensibly. */
    public static String inferType(String text) {
        String t = text == null ? "" : text.toLowerCase();
        if (t.contains("bill of lading") || t.contains("b/l")) return "eBL";
        if (t.contains("commercial invoice") || t.contains("invoice")) return "commercial-invoice";
        if (t.contains("packing list")) return "packing-list";
        return "document";
    }
}
