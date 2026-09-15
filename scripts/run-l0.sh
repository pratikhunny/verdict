#!/usr/bin/env bash
# Compile and run the L0 authority scenarios (no build tool required).
set -euo pipefail
cd "$(dirname "$0")/.."
rm -rf out && mkdir -p out
javac -d out $(find src -name '*.java')
java -cp out com.sc.verdict.party.AuthorityScenarios
