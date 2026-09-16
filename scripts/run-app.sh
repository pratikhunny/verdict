#!/usr/bin/env bash
# Build and run the Verdict service (Spring Boot). Opens on http://localhost:8080
#   Ops console        → http://localhost:8080/index.html
#   Counterparty portal → http://localhost:8080/portal.html
set -euo pipefail
cd "$(dirname "$0")/.."
mvn -q -DskipTests spring-boot:run
