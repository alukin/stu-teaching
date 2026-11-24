#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Three-node smoke test: one automatic miner + transaction propagation.
set -euo pipefail
cd "$(dirname "$0")/.."
BASE=$(pwd -P)
NODE_JAR="$BASE/blockchain-node/target/quarkus-app/quarkus-run.jar"
CLIENT_JAR="$BASE/blockchain-client/target/quarkus-app/quarkus-run.jar"
if [[ ! -f "$NODE_JAR" || ! -f "$CLIENT_JAR" ]]; then
  echo "Run mvn clean package first" >&2; exit 1
fi
mkdir -p smoke-logs
WALLET_DIR=$(mktemp -d)
pids=()
cleanup() {
  for pid in "${pids[@]}"; do kill "$pid" 2>/dev/null || true; done
  rm -rf "$WALLET_DIR"
}
trap cleanup EXIT
NODE_MINER_ENABLED=true NODE_NAME=A NODE_PORT=8081 NODE_PEERS='B@ws://localhost:8082,C@ws://localhost:8083' java -jar "$NODE_JAR" >smoke-logs/A.log 2>&1 & pids+=("$!")
NODE_NAME=B NODE_PORT=8082 NODE_PEERS='A@ws://localhost:8081,C@ws://localhost:8083' java -jar "$NODE_JAR" >smoke-logs/B.log 2>&1 & pids+=("$!")
NODE_NAME=C NODE_PORT=8083 NODE_PEERS='A@ws://localhost:8081,B@ws://localhost:8082' java -jar "$NODE_JAR" >smoke-logs/C.log 2>&1 & pids+=("$!")
sleep 10
client() { (cd "$WALLET_DIR" && java -jar "$CLIENT_JAR" "$@"); }
client wallet-demo
client wallet-create --name bob
client --node ws://localhost:8081/client miner status
client --node ws://localhost:8081/client send --from alice --to bob --amount 40
# Wait until automatic mining and peer propagation confirm Bob's balance on C.
confirmed=0
for attempt in {1..25}; do
  if client --node ws://localhost:8083/client balance --wallet bob | grep -q "bob: 40 confirmed units"; then
    confirmed=1; break
  fi
  sleep 1
done
client --node ws://localhost:8083/client status
client --node ws://localhost:8081/client peers
if [[ "$confirmed" -ne 1 ]]; then
  echo "Automatic mining / propagation did not confirm Bob's UTXO" >&2; exit 1
fi
printf '\nSmoke passed: Alice -> Bob confirmed through automatic mining.\n'
