package com.sc.verdict.app;

import com.sc.verdict.evidence.EvidencePack;
import com.sc.verdict.orchestrator.Graphs;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The REST surface (HLD §9), narrowed to what the two-screen demo drives. Every state-changing call
 * runs the real engine, ledger, journal and reconciliation via {@link DealService}; the controller
 * only maps HTTP to those operations.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final DealService service;

    public ApiController(DealService service) {
        this.service = service;
    }

    @GetMapping("/deal")
    public Views.DealView deal() {
        return service.dealView();
    }

    @GetMapping("/graphs")
    public List<Views.GraphView> graphs() {
        return Graphs.all().stream().map(Mapper::graph).toList();
    }

    /** Current extraction mode (FIXTURE / LIVE) and whether the live model is available. */
    @GetMapping("/extraction")
    public Views.ExtractionView extraction() {
        return service.extractionStatus();
    }

    /** The ops team's fixture/live toggle: {mode} is FIXTURE or LIVE. */
    @PostMapping("/extraction/{mode}")
    public Views.ExtractionView setExtraction(@PathVariable String mode) {
        return service.setExtractionMode(mode);
    }

    @PostMapping("/reset")
    public Map<String, String> reset() {
        service.reset();
        return Map.of("status", "reset");
    }

    /** Run one evidence pack: CLEAN, COSMETIC_VARIANCE, SHORT_SHIPMENT, LATE_SHIPMENT. */
    @PostMapping("/packs/{pack}")
    public Views.DeterminationView runPack(@PathVariable String pack) {
        return service.runPack(EvidencePack.valueOf(pack.toUpperCase()));
    }

    /** Run the Pack 4 approval loop on a standing HOLD_PENDING_APPROVAL. */
    @PostMapping("/approve")
    public Views.ApprovalView approve() {
        return service.approve();
    }

    /** The determination awaiting the counterparty's approval, or {pending:false}. */
    @GetMapping("/pending")
    public Object pending() {
        Views.DeterminationView v = service.pendingView();
        return v == null ? Map.of("pending", false) : v;
    }

    @GetMapping("/ledger")
    public Views.LedgerView ledger() {
        return service.ledgerView();
    }

    @GetMapping("/journal")
    public Views.JournalView journal() {
        return service.journalView();
    }

    @PostMapping("/replay/{determinationId}")
    public Views.ReplayView replay(@PathVariable String determinationId) {
        return service.replay(determinationId);
    }

    // ---- error mapping: stable, machine-readable, per HLD §9 ----

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> badRequest(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }
}
