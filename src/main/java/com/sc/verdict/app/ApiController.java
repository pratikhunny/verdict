package com.sc.verdict.app;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * The REST surface. Every state-changing call runs the real engine, ledger, journal and
 * reconciliation via {@link TransactionService}; the controller only maps HTTP to those operations.
 * One master contract, many transactions, each its own fund → upload → extract → examine → pay cycle.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final TransactionService service;
    private final DealCatalog catalog;
    private final GraphCatalog graphs;
    private final SampleDocuments samples;

    public ApiController(TransactionService service, DealCatalog catalog, GraphCatalog graphs, SampleDocuments samples) {
        this.service = service;
        this.catalog = catalog;
        this.graphs = graphs;
        this.samples = samples;
    }

    // ---- deal types, contract, graph, samples ----

    @GetMapping("/dealtypes")
    public List<String> dealTypes() {
        return catalog.dealTypes();
    }

    @GetMapping("/contract")
    public Views.ContractView contract(@RequestParam(value = "dealType", required = false) String dealType) {
        return catalog.contractView(dealType == null ? DealCatalog.MARKETPLACE : dealType);
    }

    @GetMapping("/graph")
    public Views.GraphView2 graph(@RequestParam(value = "dealType", required = false) String dealType) {
        return graphs.forDealType(dealType);
    }

    @GetMapping("/samples")
    public List<SampleDocuments.Sample> samples(@RequestParam(value = "dealType", required = false) String dealType) {
        return samples.scenariosFor(dealType);
    }

    /** The per-transaction fields an operator fills to open a transaction under this deal type. */
    @GetMapping("/orderform")
    public Views.OrderFormView orderForm(@RequestParam(value = "dealType", required = false) String dealType) {
        return catalog.orderForm(dealType);
    }

    // ---- extraction mode toggle ----

    @GetMapping("/extraction")
    public Views.ExtractionModeView extractionMode() {
        return service.extractionMode();
    }

    /** Ops toggle: {mode} is RULES (deterministic) or LLM (live AI). */
    @PostMapping("/extraction/{mode}")
    public Views.ExtractionModeView setExtractionMode(@PathVariable String mode) {
        return service.setExtractionMode(mode);
    }

    // ---- transactions ----

    @GetMapping("/transactions")
    public List<Views.TransactionView> transactions() {
        return service.allTransactionViews();
    }

    @PostMapping("/transactions")
    public Views.TransactionView newTransaction(@RequestParam(value = "dealType", required = false) String dealType,
                                                @RequestBody(required = false) Map<String, String> inputs) {
        return service.transactionView(service.newTransaction(dealType, inputs));
    }

    @GetMapping("/transactions/{id}")
    public Views.TransactionView transaction(@PathVariable String id) {
        return service.transactionView(id);
    }

    /** COLLECT_FUNDS — fund the wallet. */
    @PostMapping("/transactions/{id}/fund")
    public Views.LedgerView fund(@PathVariable String id) {
        service.fund(id);
        return service.ledgerView(id);
    }

    /** EARMARK — reserve into per-payee wallet buckets. */
    @PostMapping("/transactions/{id}/earmark")
    public Views.LedgerView earmark(@PathVariable String id) {
        service.earmark(id);
        return service.ledgerView(id);
    }

    /** SPLIT + DISBURSE — pay the payees from the earmarks. */
    @PostMapping("/transactions/{id}/disburse")
    public Views.LedgerView disburse(@PathVariable String id) {
        return service.disburse(id);
    }

    // ---- documents ----

    /** Upload a real document (text file). PDF text extraction is a fast-follow. */
    @PostMapping("/transactions/{id}/documents")
    public Views.ExtractionResultView upload(@PathVariable String id,
                                             @RequestParam("file") MultipartFile file) throws IOException {
        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        service.upload(id, file.getOriginalFilename() == null ? "document.txt" : file.getOriginalFilename(), text);
        return service.extractionView(id);
    }

    /** Paste document text (for when you don't have a file handy). */
    @PostMapping("/transactions/{id}/documents/text")
    public Views.ExtractionResultView uploadText(@PathVariable String id, @RequestBody TextUpload body) {
        service.upload(id, body.filename() == null ? "pasted.txt" : body.filename(), body.text());
        return service.extractionView(id);
    }

    /** Load a ready-made sample document set: CLEAN, COSMETIC_VARIANCE, SHORT_SHIPMENT, LATE_SHIPMENT. */
    @PostMapping("/transactions/{id}/samples/{scenario}")
    public Views.ExtractionResultView loadSample(@PathVariable String id, @PathVariable String scenario) {
        service.loadSample(id, scenario);
        return service.extractionView(id);
    }

    @DeleteMapping("/transactions/{id}/documents")
    public Views.ExtractionResultView clearDocuments(@PathVariable String id) {
        service.clearDocuments(id);
        return service.extractionView(id);
    }

    @GetMapping("/transactions/{id}/extraction")
    public Views.ExtractionResultView extractionView(@PathVariable String id) {
        return service.extractionView(id);
    }

    // ---- extract → examine → approve ----

    @PostMapping("/transactions/{id}/extract")
    public Views.ExtractionResultView extract(@PathVariable String id) {
        service.extract(id);
        return service.extractionView(id);
    }

    @PostMapping("/transactions/{id}/examine")
    public Views.DeterminationView examine(@PathVariable String id) {
        return Mapper.determination(service.examine(id));
    }

    @PostMapping("/transactions/{id}/approve")
    public Views.ApprovalView approve(@PathVariable String id) {
        return service.approve(id);
    }

    @PostMapping("/transactions/{id}/replay/{determinationId}")
    public Views.ReplayView replay(@PathVariable String id, @PathVariable String determinationId) {
        return service.replay(id, determinationId);
    }

    // ---- per-transaction views ----

    @GetMapping("/transactions/{id}/ledger")
    public Views.LedgerView ledger(@PathVariable String id) {
        return service.ledgerView(id);
    }

    @GetMapping("/transactions/{id}/journal")
    public Views.JournalView journal(@PathVariable String id) {
        return service.journalView(id);
    }

    @GetMapping("/transactions/{id}/pending")
    public Object pending(@PathVariable String id) {
        Views.DeterminationView v = service.pendingView(id);
        return v == null ? Map.of("pending", false) : v;
    }

    @GetMapping("/transactions/{id}/determination")
    public Object determination(@PathVariable String id) {
        Views.DeterminationView v = service.determinationView(id);
        return v == null ? Map.of("determined", false) : v;
    }

    public record TextUpload(String filename, String text) {}

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> conflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }
}
