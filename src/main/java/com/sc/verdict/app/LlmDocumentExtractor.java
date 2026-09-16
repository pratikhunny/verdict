package com.sc.verdict.app;

import com.sc.verdict.evidence.Document;
import com.sc.verdict.evidence.ExtractedFact;
import com.sc.verdict.evidence.FactKey;
import com.sc.verdict.evidence.Submission;
import com.sc.verdict.shared.Ids.DealId;
import com.sc.verdict.shared.Ids.DocumentId;
import com.sc.verdict.shared.Ids.SubmissionId;
import com.sc.verdict.shared.Versions.AdapterVersion;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Live extraction: an LLM reads the same uploaded documents the deterministic parser would, via
 * Spring AI's provider-agnostic {@link ChatModel}. This is where AI does its job — reading — with a
 * confidence per field. It never decides (ADR-002); it returns {@link ExtractedFact}s exactly as the
 * deterministic extractor does. Optional and key-gated, so the app boots and runs on rules without it.
 */
@Component
public class LlmDocumentExtractor implements DocumentExtractor {

    private static final String SYSTEM = """
            You are a trade-finance and escrow document extraction service. Read the documents and
            extract only what they state — never infer or complete missing information. Return a
            confidence in [0,1] per field. Dates must be ISO-8601 (YYYY-MM-DD).
            Documents may be a shipping document (bill of lading / invoice) OR a construction
            completion certificate. Set eblPresent for shipping docs; set certificatePresent,
            completionPercent and objectionRaised for construction certificates. Leave fields for the
            other document type at their defaults.
            """;

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final String configuredModel;
    private final String apiKey;

    public LlmDocumentExtractor(
            ObjectProvider<ChatModel> chatModelProvider,
            @Value("${spring.ai.anthropic.chat.options.model:unset}") String configuredModel,
            @Value("${spring.ai.anthropic.api-key:}") String apiKey) {
        this.chatModelProvider = chatModelProvider;
        this.configuredModel = configuredModel;
        this.apiKey = apiKey;
    }

    @Override
    public String label() {
        return "llm:" + configuredModel;
    }

    @Override
    public boolean available() {
        return chatModelProvider.getIfAvailable() != null
                && apiKey != null && !apiKey.isBlank() && !"not-set".equals(apiKey);
    }

    @Override
    public Submission extract(String submissionId, DealId dealId, List<UploadedDoc> documents, Instant at) {
        ChatModel model = chatModelProvider.getIfAvailable();
        if (!available() || model == null) {
            throw new ExtractionUnavailableException(
                    "Live AI extraction is not configured — set ANTHROPIC_API_KEY (or swap the Spring AI "
                    + "starter). Ops can continue on deterministic rules.");
        }
        String text = documents.stream()
                .map(d -> "[" + d.type() + " · " + d.filename() + "]\n" + d.text())
                .reduce((a, b) -> a + "\n\n---\n\n" + b).orElse("");

        Extraction r;
        try {
            r = ChatClient.create(model).prompt()
                    .system(SYSTEM)
                    .user("Extract the fields from these documents:\n\n" + text)
                    .call()
                    .entity(Extraction.class);
        } catch (Exception e) {
            throw new ExtractionUnavailableException("Live extraction call failed: " + e.getMessage(), e);
        }
        if (r == null) {
            throw new ExtractionUnavailableException("Live extraction returned no structured result.");
        }

        AdapterVersion version = new AdapterVersion("EXT-LLM/" + configuredModel);
        UploadedDoc src = documents.isEmpty() ? null : documents.get(0);
        DocumentId srcId = src == null ? new DocumentId("DOC-NONE") : new DocumentId(src.id());
        String srcHash = src == null ? "" : src.contentHash();

        var facts = new java.util.ArrayList<ExtractedFact>();
        if (r.eblPresent()) {
            facts.add(new ExtractedFact(FactKey.EBL_PRESENT, "true", clamp(r.eblConfidence()), srcId, srcHash, version));
            facts.add(new ExtractedFact(FactKey.EVIDENCED_QUANTITY, Long.toString(r.evidencedQuantity()), clamp(r.quantityConfidence()), srcId, srcHash, version));
            facts.add(new ExtractedFact(FactKey.GOODS_DESCRIPTION, safe(r.goodsDescription()), clamp(r.goodsConfidence()), srcId, srcHash, version));
            facts.add(new ExtractedFact(FactKey.SHIPMENT_DATE, isoDate(r.shipmentDate()), clamp(r.dateConfidence()), srcId, srcHash, version));
        }
        if (r.certificatePresent()) {
            facts.add(new ExtractedFact(FactKey.CERTIFICATE_PRESENT, "true", clamp(r.certificateConfidence()), srcId, srcHash, version));
            facts.add(new ExtractedFact(FactKey.COMPLETION_PERCENT, Long.toString(r.completionPercent()), clamp(r.completionConfidence()), srcId, srcHash, version));
            if (r.objectionRaised()) {
                facts.add(new ExtractedFact(FactKey.OBJECTION_RAISED, "true", clamp(r.objectionConfidence()), srcId, srcHash, version));
            }
        }

        List<Document> docs = documents.stream()
                .map(d -> new Document(new DocumentId(d.id()), d.type(), d.contentHash()))
                .toList();
        return new Submission(new SubmissionId(submissionId), dealId, docs, facts, at);
    }

    private static double clamp(double c) { return c < 0 ? 0 : (c > 1 ? 1 : c); }

    private static String safe(String s) { return s == null ? "" : s.trim(); }

    private static String isoDate(String raw) {
        String v = safe(raw);
        try {
            return LocalDate.parse(v).toString();
        } catch (DateTimeParseException ignored) {
            for (String pattern : new String[]{"d MMMM yyyy", "dd MMMM yyyy", "d MMM yyyy"}) {
                try {
                    return LocalDate.parse(v, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)).toString();
                } catch (DateTimeParseException ignored2) {
                    // try next
                }
            }
            return v;
        }
    }

    public record Extraction(
            boolean eblPresent, double eblConfidence,
            long evidencedQuantity, double quantityConfidence,
            String goodsDescription, double goodsConfidence,
            String shipmentDate, double dateConfidence,
            boolean certificatePresent, double certificateConfidence,
            long completionPercent, double completionConfidence,
            boolean objectionRaised, double objectionConfidence) {}

    public static final class ExtractionUnavailableException extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        public ExtractionUnavailableException(String message) { super(message); }
        public ExtractionUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}
