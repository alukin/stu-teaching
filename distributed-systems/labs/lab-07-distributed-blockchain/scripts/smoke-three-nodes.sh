#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Example manual integration smoke: requires JDK 25, Maven and localhost ports 8081-8083.
set -euo pipefail
cd "$(dirname "$0")/.."
NODE_JAR=blockchain-node/target/quarkus-app/quarkus-run.jar
CLIENT_JAR=blockchain-client/target/quarkus-app/quarkus-run.jar
if [[ ! -f "$NODE_JAR" || ! -f "$CLIENT_JAR" ]]; then
  echo "Run mvn clean package first" >&2; exit 1
fi
mkdir -p smoke-logs
pids=()
cleanup() { for pid in "${pids[@]}"; do kill "$pid" 2>/dev/null || true; done; }
trap cleanup EXIT
NODE_NAME=A NODE_PORT=8081 NODE_PEERS='B@ws://localhost:8082,C@ws://localhost:8083' java -jar "$NODE_JAR" >smoke-logs/A.log 2>&1 & pids+=("$!")
NODE_NAME=B NODE_PORT=8082 NODE_PEERS='A@ws://localhost:8081,C@ws://localhost:8083' java -jar "$NODE_JAR" >smoke-logs/B.log 2>&1 & pids+=("$!")
NODE_NAME=C NODE_PORT=8083 NODE_PEERS='A@ws://localhost:8081,B@ws://localhost:8082' java -jar "$NODE_JAR" >smoke-logs/C.log 2>&1 & pids+=("$!")
sleep 10
client() { java -jar "$CLIENT_JAR" "$@"; }
client --node ws://localhost:8081/client status
client --node ws://localhost:8082/client mine
sleep 5
client --node ws://localhost:8083/client status
client --node ws://localhost:8081/client peers
printf '
Smoke finished. Inspect smoke-logs/*.log; test convergence using tip / utxoFingerprint.
'
