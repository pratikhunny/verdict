package com.sc.verdict.app;

import com.sc.verdict.evidence.Document;
import com.sc.verdict.evidence.EvidenceCorpus;
import com.sc.verdict.evidence.EvidencePack;
import com.sc.verdict.evidence.ExtractedFact;
import com.sc.verdict.evidence.ExtractionAdapter;
import com.sc.verdict.evidence.FactKey;
import com.sc.verdict.evidence.Submission;
import com.sc.verdict.shared.Hashing;
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
 * L3 extraction backed by a real LLM via Spring AI — this is where AI does its job: <em>reading</em>
 * documents into asserted facts, with a confidence per field (ADR-002). It never decides; it returns
 * {@link ExtractedFact}s exactly as the fixture does, so the examination engine cannot tell a live
 * extraction from a canned one, and the architecture test still forbids the engine from importing it.
 *
 * <p>It depends on Spring AI's {@link ChatModel} abstraction, not a vendor, so the backend model is
 * adopted "as per availability" by swapping the starter. The model is injected via
 * {@link ObjectProvider} and treated as optional: with no key configured the app still boots, and
 * live extraction reports itself unavailable rather than failing — which is what lets the ops team
 * fall back to fixtures with a button.
 */
@Component
public class LlmExtractionAdapter implements ExtractionAdapter {

    private static final String SYSTEM = """
            You are a trade-finance document extraction service. Read the shipping documents and
            extract only what they state — never infer or complete missing information. For each field
            return a confidence in [0,1] reflecting how clearly the documents support it. Dates must be
            ISO-8601 (YYYY-MM-DD).
            """;

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final EvidenceCorpus corpus = new EvidenceCorpus();
    private final String configuredModel;
    private final String apiKey;

    public LlmExtractionAdapter(
            ObjectProvider<ChatModel> chatModelProvider,
            @Value("${spring.ai.anthropic.chat.options.model:unset}") String configuredModel,
            @Value("${spring.ai.anthropic.api-key:}") String apiKey) {
        this.chatModelProvider = chatModelProvider;
        this.configuredModel = configuredModel;
        this.apiKey = apiKey;
    }

    /** Whether a backend model is wired and a real key is configured. */
    public boolean available() {
        return chatModelProvider.getIfAvailable() != null
                && apiKey != null && !apiKey.isBlank() && !"not-set".equals(apiKey);
    }

    public String modelLabel() {
        return configuredModel;
    }

    @Override
    public AdapterVersion version() {
        return new AdapterVersion("EXT-LLM/" + configuredModel);
    }

    @Override
    public Submission extract(EvidencePack pack, DealId dealId, Instant at) {
        ChatModel model = chatModelProvider.getIfAvailable();
        if (!available() || model == null) {
            throw new ExtractionUnavailableException(
                    "Live extraction is not configured — set ANTHROPIC_API_KEY (or swap the Spring AI "
                    + "starter and set its key). Ops can continue on fixtures.");
        }

        List<EvidenceCorpus.DocText> docs = corpus.documentsFor(pack);
        String documentText = docs.stream()
                .map(d -> "[" + d.type() + "]\n" + d.content())
                .reduce((a, b) -> a + "\n\n---\n\n" + b).orElse("");

        Extraction result;
        try {
            result = ChatClient.create(model).prompt()
                    .system(SYSTEM)
                    .user("Extract the fields from these documents:\n\n" + documentText)
                    .call()
                    .entity(Extraction.class);
        } catch (Exception e) {
            throw new ExtractionUnavailableException("Live extraction call failed: " + e.getMessage(), e);
        }
        if (result == null) {
            throw new ExtractionUnavailableException("Live extraction returned no structured result.");
        }

        EvidenceCorpus.DocText eblDoc = docs.get(0);
        String eblHash = Hashing.sha256Hex(eblDoc.content());
        DocumentId eblId = new DocumentId(eblDoc.id());
        AdapterVersion version = version();

        List<ExtractedFact> facts = List.of(
                fact(FactKey.EBL_PRESENT, Boolean.toString(result.eblPresent()), result.eblConfidence(), eblId, eblHash, version),
                fact(FactKey.EVIDENCED_QUANTITY, Long.toString(result.evidencedQuantity()), result.quantityConfidence(), eblId, eblHash, version),
                fact(FactKey.GOODS_DESCRIPTION, safe(result.goodsDescription()), result.goodsConfidence(), eblId, eblHash, version),
                fact(FactKey.SHIPMENT_DATE, isoDate(result.shipmentDate()), result.dateConfidence(), eblId, eblHash, version));

        List<Document> documents = docs.stream()
                .map(d -> new Document(new DocumentId(d.id()), d.type(), Hashing.sha256Hex(d.content())))
                .toList();
        return new Submission(new SubmissionId("SUB-LIVE-" + pack.name()), dealId, documents, facts, at);
    }

    private static ExtractedFact fact(FactKey key, String value, double confidence,
                                      DocumentId doc, String hash, AdapterVersion version) {
        return new ExtractedFact(key, value, clamp(confidence), doc, hash, version);
    }

    private static double clamp(double c) {
        return c < 0 ? 0 : (c > 1 ? 1 : c);
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    /** Normalise the model's date to ISO, tolerating a couple of common prose forms. */
    private static String isoDate(String raw) {
        String v = safe(raw);
        try {
            return LocalDate.parse(v).toString();
        } catch (DateTimeParseException ignored) {
            for (String pattern : new String[]{"d MMMM yyyy", "dd MMMM yyyy", "d MMM yyyy"}) {
                try {
                    return LocalDate.parse(v, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)).toString();
                } catch (DateTimeParseException ignored2) {
                    // try next format
                }
            }
            throw new ExtractionUnavailableException("Live extraction returned an unparseable date: " + raw);
        }
    }

    /** The structured shape the model fills; Spring AI derives the schema and binds the response. */
    public record Extraction(
            boolean eblPresent, double eblConfidence,
            long evidencedQuantity, double quantityConfidence,
            String goodsDescription, double goodsConfidence,
            String shipmentDate, double dateConfidence) {}

    /** Signals that live extraction cannot run; surfaced to ops, who fall back to fixtures. */
    public static final class ExtractionUnavailableException extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        public ExtractionUnavailableException(String message) { super(message); }
        public ExtractionUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}
