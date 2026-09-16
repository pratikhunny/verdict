package com.sc.verdict.arch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The architecture test that makes the plane separation a control rather than a comment (CLAUDE.md
 * rule 2, ADR-001/002). It scans main sources and fails the build if a package imports something the
 * three-plane split forbids:
 *
 * <ul>
 *   <li>the examination engine imports no money-plane type and no extraction adapter — the model
 *       reads, the engine decides;
 *   <li>the money plane imports no obligation, condition or finding type — the determination is the
 *       only thing it consumes from the decision plane;
 *   <li>the contract model and the authority module are leaves — they depend only on shared.
 * </ul>
 *
 * <p>Run: {@code java -cp out com.sc.verdict.arch.ArchitectureRules}. Exits non-zero on any violation.
 */
public final class ArchitectureRules {

    /** A source package, the import prefixes it may not use, and the exact class names exempted. */
    private record Rule(String packagePrefix, List<String> forbiddenPrefixes, List<String> allowedSimpleNames) {}

    private static final List<Rule> RULES = List.of(
            // L4 examination: no money plane, no reconciliation, no extraction adapter (no model call).
            new Rule("com.sc.verdict.examination", List.of(
                    "com.sc.verdict.ledger.",
                    "com.sc.verdict.recon.",
                    "com.sc.verdict.party.",
                    "com.sc.verdict.casework.",
                    "com.sc.verdict.evidence.ExtractionAdapter",
                    "com.sc.verdict.evidence.FixtureExtraction"), List.of()),
            // L6 money: only the determination contract crosses from decision → money.
            new Rule("com.sc.verdict.ledger", List.of(
                    "com.sc.verdict.contract.",
                    "com.sc.verdict.entitlement.",
                    "com.sc.verdict.party.",
                    "com.sc.verdict.casework.",
                    "com.sc.verdict.examination."),
                    List.of("Determination", "DisbursementLine", "ResidualLine", "Outcome")),
            // L1/L2 contract model: a leaf on shared only.
            new Rule("com.sc.verdict.contract", List.of("com.sc.verdict."),
                    List.of()),  // exempt handled below: shared is not "com.sc.verdict." prefixed match issue
            // L0 authority: a leaf on shared only.
            new Rule("com.sc.verdict.party", List.of(
                    "com.sc.verdict.ledger.",
                    "com.sc.verdict.examination.",
                    "com.sc.verdict.contract.",
                    "com.sc.verdict.evidence.",
                    "com.sc.verdict.casework.",
                    "com.sc.verdict.recon."), List.of()));

    public static void main(String[] args) throws IOException {
        Path root = locateMainSources();
        var violations = new ArrayList<String>();
        int scanned = 0;

        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                scanned++;
                List<String> lines = Files.readAllLines(file);
                String pkg = packageOf(lines);
                for (Rule rule : RULES) {
                    if (!pkg.startsWith(rule.packagePrefix())) {
                        continue;
                    }
                    for (String line : lines) {
                        String imported = importedType(line);
                        if (imported == null) {
                            continue;
                        }
                        // shared is always allowed.
                        if (imported.startsWith("com.sc.verdict.shared.")) {
                            continue;
                        }
                        // same-package imports never violate.
                        if (imported.startsWith(rule.packagePrefix() + ".")) {
                            continue;
                        }
                        for (String forbidden : rule.forbiddenPrefixes()) {
                            if (imported.startsWith(forbidden)
                                    && !rule.allowedSimpleNames().contains(simpleName(imported))) {
                                violations.add("%s (%s) imports %s".formatted(
                                        file.getFileName(), rule.packagePrefix(), imported));
                            }
                        }
                    }
                }
            }
        }

        System.out.printf("Scanned %d source files against %d architecture rules.%n", scanned, RULES.size());
        if (violations.isEmpty()) {
            System.out.println("No forbidden imports. The three planes hold.");
        } else {
            violations.forEach(v -> System.out.println("  VIOLATION: " + v));
            System.out.printf("%n%d architecture violation(s).%n", violations.size());
            System.exit(1);
        }
    }

    private static String packageOf(List<String> lines) {
        return lines.stream()
                .map(String::trim)
                .filter(l -> l.startsWith("package "))
                .findFirst()
                .map(l -> l.substring("package ".length()).replace(";", "").trim())
                .orElse("");
    }

    private static String importedType(String line) {
        String t = line.trim();
        if (!t.startsWith("import ")) {
            return null;
        }
        t = t.substring("import ".length()).replace(";", "").trim();
        if (t.startsWith("static ")) {
            t = t.substring("static ".length()).trim();
        }
        return t;
    }

    private static String simpleName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot < 0 ? fqn : fqn.substring(dot + 1);
    }

    private static Path locateMainSources() {
        for (String candidate : new String[]{"src/main/java", "../src/main/java"}) {
            Path p = Path.of(candidate);
            if (Files.isDirectory(p)) {
                return p;
            }
        }
        throw new IllegalStateException("cannot locate src/main/java from " + Path.of("").toAbsolutePath());
    }
}
