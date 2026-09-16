package com.sc.verdict.app;

import com.sc.verdict.evidence.Submission;
import com.sc.verdict.shared.Ids.DealId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Routes extraction to the deterministic parser or the live LLM — the ops team's runtime toggle, no
 * restart. Default is {@link Mode#RULES}: the scripted demo stays reproducible; live AI is opt-in for
 * the moment you want a genuine model read. A live failure surfaces to ops rather than silently
 * falling back, so "we are really calling the model" stays honest.
 */
@Component
public class ExtractionRouter {

    public enum Mode { RULES, LLM }

    private final DeterministicDocumentExtractor rules;
    private final LlmDocumentExtractor llm;
    private volatile Mode mode = Mode.RULES;

    public ExtractionRouter(DeterministicDocumentExtractor rules, LlmDocumentExtractor llm) {
        this.rules = rules;
        this.llm = llm;
    }

    public Mode mode() { return mode; }

    public void setMode(Mode mode) { this.mode = mode; }

    public boolean liveAvailable() { return llm.available(); }

    public String modelLabel() { return llm.label(); }

    public String activeLabel() { return active().label(); }

    public Submission extract(String submissionId, DealId dealId, List<UploadedDoc> docs, Instant at) {
        return active().extract(submissionId, dealId, docs, at);
    }

    private DocumentExtractor active() {
        return mode == Mode.LLM ? llm : rules;
    }
}
