package com.sc.verdict.app;

import com.sc.verdict.evidence.EvidenceCorpus;
import com.sc.verdict.evidence.EvidencePack;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Ready-made document sets for the demo, per deal type, so you can load a realistic document into a
 * transaction and extract it for real. Uploading your own document works identically; these just make
 * the scripted run fast. Backed by {@link EvidenceCorpus}, so the samples are the same documents the
 * tests use.
 */
@Component
public class SampleDocuments {

    private final EvidenceCorpus corpus = new EvidenceCorpus();

    private static boolean isConstruction(String dealType) {
        return dealType != null && dealType.toLowerCase().contains("construction");
    }

    /** The scenario keys available for a deal type, for the UI's "load a sample" menu. */
    public List<Sample> scenariosFor(String dealType) {
        return isConstruction(dealType)
                ? List.of(new Sample("CLEAN", "Certified 45% · clean"),
                          new Sample("INCOMPLETE", "Certified 30% · short of milestone"),
                          new Sample("OBJECTION", "Certified 45% · objection filed"),
                          new Sample("MISSING_CERT", "No engineer certificate"))
                : List.of(new Sample("CLEAN", "Clean shipment"),
                          new Sample("COSMETIC_VARIANCE", "Cosmetic variance (\"tablet PCs\")"),
                          new Sample("SHORT_SHIPMENT", "Short shipment (800/1,000)"),
                          new Sample("LATE_SHIPMENT", "Late shipment (3 days)"));
    }

    /** The documents of one scenario, as if freshly uploaded. */
    public List<UploadedDoc> forScenario(String dealType, String scenario) {
        List<EvidenceCorpus.DocText> docs = isConstruction(dealType)
                ? corpus.constructionDocumentsFor(scenario)
                : corpus.documentsFor(EvidencePack.valueOf(scenario.toUpperCase()));
        return docs.stream()
                .map(d -> new UploadedDoc(d.id(), filename(d.type(), scenario), d.type(), d.content(),
                        d.content().getBytes(StandardCharsets.UTF_8).length))
                .toList();
    }

    private static String filename(String type, String scenario) {
        return type + "-" + scenario.toLowerCase() + ".txt";
    }

    public record Sample(String key, String label) {}
}
