package com.sc.verdict.app;

import com.sc.verdict.evidence.EvidencePack;
import com.sc.verdict.evidence.ExtractionAdapter;
import com.sc.verdict.evidence.FixtureExtraction;
import com.sc.verdict.evidence.Submission;
import com.sc.verdict.shared.Ids.DealId;
import com.sc.verdict.shared.Versions.AdapterVersion;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * The extraction seam the rest of the service talks to. It routes each extraction to either the
 * deterministic {@link FixtureExtraction} or the live {@link LlmExtractionAdapter}, and the mode is a
 * runtime switch — the ops team flips it with a button, no restart, no redeploy.
 *
 * <p>Default is {@link Mode#FIXTURE}: the four-pack determinism the demo depends on is bulletproof,
 * and live AI is opt-in for the one moment you want to show a genuine model call. If a live call
 * fails, it surfaces the error rather than silently substituting a canned answer — so "we are really
 * calling the model" stays honest, and the fallback stays a deliberate ops decision.
 */
@Component
public class RoutingExtractionAdapter implements ExtractionAdapter {

    public enum Mode { FIXTURE, LIVE }

    private final FixtureExtraction fixture = new FixtureExtraction();
    private final LlmExtractionAdapter llm;
    private volatile Mode mode = Mode.FIXTURE;

    public RoutingExtractionAdapter(LlmExtractionAdapter llm) {
        this.llm = llm;
    }

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    /** Whether live extraction can actually run (model wired and key configured). */
    public boolean liveAvailable() {
        return llm.available();
    }

    public String modelLabel() {
        return llm.modelLabel();
    }

    @Override
    public AdapterVersion version() {
        return active().version();
    }

    @Override
    public Submission extract(EvidencePack pack, DealId dealId, Instant at) {
        return active().extract(pack, dealId, at);
    }

    private ExtractionAdapter active() {
        return mode == Mode.LIVE ? llm : fixture;
    }
}
