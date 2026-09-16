package com.sc.verdict.app;

import com.sc.verdict.evidence.Document;
import com.sc.verdict.evidence.ExtractedFact;
import com.sc.verdict.evidence.FactKey;
import com.sc.verdict.evidence.Submission;
import com.sc.verdict.shared.Ids.DealId;
import com.sc.verdict.shared.Ids.DocumentId;
import com.sc.verdict.shared.Ids.SubmissionId;
import com.sc.verdict.shared.Versions.AdapterVersion;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic extraction: real regexes over the uploaded document text. It genuinely reads the
 * document — it is not a lookup — but the rules give the identical answer every time, which is what
 * makes the scripted demo reproducible. Confidence is high and fixed: a rule is either certain of
 * what the text says or it does not assert the fact at all.
 *
 * <p>This is the "deterministic code always extracts the same way" half of the toggle; the LLM
 * extractor reads the same text when live mode is on.
 */
@Component
public class DeterministicDocumentExtractor implements DocumentExtractor {

    private static final AdapterVersion VERSION = new AdapterVersion("EXT-RULES-v1");
    private static final Pattern QTY = Pattern.compile("([\\d,]+)\\s*units", Pattern.CASE_INSENSITIVE);
    private static final Pattern GOODS = Pattern.compile("units,?\\s*([^.\\n]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHIPPED = Pattern.compile("shipped on board:?\\s*([^\\n]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern GOODS_LABEL = Pattern.compile("goods:?\\s*([^.\\n]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPLETION = Pattern.compile("completion:?\\s*(\\d+)\\s*%", Pattern.CASE_INSENSITIVE);

    @Override
    public String label() {
        return "deterministic-rules";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public Submission extract(String submissionId, DealId dealId, List<UploadedDoc> documents, Instant at) {
        String all = documents.stream().map(UploadedDoc::text).reduce((a, b) -> a + "\n" + b).orElse("");
        UploadedDoc source = documents.isEmpty() ? null : documents.get(0);
        DocumentId srcId = source == null ? new DocumentId("DOC-NONE") : new DocumentId(source.id());
        String srcHash = source == null ? "" : source.contentHash();

        var facts = new ArrayList<ExtractedFact>();
        String lower = all.toLowerCase(Locale.ROOT);

        // Marketplace facts — emitted only when the document is a transport/shipping document.
        if (lower.contains("bill of lading")) {
            facts.add(fact(FactKey.EBL_PRESENT, "true", 1.0, srcId, srcHash));
            Matcher q = QTY.matcher(all);
            if (q.find()) {
                facts.add(fact(FactKey.EVIDENCED_QUANTITY, Long.toString(Long.parseLong(q.group(1).replace(",", ""))), 0.99, srcId, srcHash));
            }
            String goods = firstGroup(GOODS, all);
            if (goods == null) goods = firstGroup(GOODS_LABEL, all);
            if (goods != null) facts.add(fact(FactKey.GOODS_DESCRIPTION, goods.trim(), 0.98, srcId, srcHash));
            String shipped = firstGroup(SHIPPED, all);
            if (shipped != null) facts.add(fact(FactKey.SHIPMENT_DATE, isoDate(shipped.trim()), 0.99, srcId, srcHash));
        }

        // Construction facts — emitted only when the document is a completion certificate.
        if (lower.contains("completion certificate")) {
            facts.add(fact(FactKey.CERTIFICATE_PRESENT, "true", 1.0, srcId, srcHash));
            String completion = firstGroup(COMPLETION, all);
            if (completion != null) facts.add(fact(FactKey.COMPLETION_PERCENT, completion.trim(), 0.97, srcId, srcHash));
            if (lower.contains("objection") || lower.contains("lien")) {
                facts.add(fact(FactKey.OBJECTION_RAISED, "true", 0.96, srcId, srcHash));
            }
        }

        List<Document> docs = documents.stream()
                .map(d -> new Document(new DocumentId(d.id()), d.type(), d.contentHash()))
                .toList();
        return new Submission(new SubmissionId(submissionId), dealId, docs, facts, at);
    }

    private static ExtractedFact fact(FactKey key, String value, double conf, DocumentId doc, String hash) {
        return new ExtractedFact(key, value, conf, doc, hash, VERSION);
    }

    private static String firstGroup(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static String isoDate(String raw) {
        try {
            return LocalDate.parse(raw).toString();
        } catch (DateTimeParseException ignored) {
            for (String pattern : new String[]{"d MMMM yyyy", "dd MMMM yyyy", "d MMM yyyy"}) {
                try {
                    return LocalDate.parse(raw, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)).toString();
                } catch (DateTimeParseException ignored2) {
                    // try next
                }
            }
            return raw; // leave as-is; the engine will fail closed if it cannot parse a date
        }
    }
}
