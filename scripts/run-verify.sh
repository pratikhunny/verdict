#!/usr/bin/env bash
# Compile everything and run the full verification suite (no build tool required):
#   - architecture rules (three-plane import boundaries)
#   - L0 authority scenarios (12)
#   - the four evidence packs, replay, and reconciliation (43 expectations)
set -euo pipefail
cd "$(dirname "$0")/.."
rm -rf out && mkdir -p out
# The domain core must compile and run as PLAIN JAVA — no Spring. The com.sc.verdict.app package is
# the only Spring code; excluding it here proves the core has no framework dependency. Build the full
# service (app included) with Maven: `mvn -DskipTests package`.
javac -d out $(find src -name '*.java' -not -path '*/com/sc/verdict/app/*')

echo "===== ARCHITECTURE RULES ====="
java -cp out com.sc.verdict.arch.ArchitectureRules
echo
echo "===== L0 AUTHORITY (12 scenarios) ====="
java -cp out com.sc.verdict.party.AuthorityScenarios
echo
echo "===== EVIDENCE PACKS (4 determinations · replay · reconciliation) ====="
java -cp out com.sc.verdict.demo.EvidencePackScenarios
