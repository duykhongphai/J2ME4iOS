#!/usr/bin/env bash
# Several VMs at once (LangTest in 4 instances) and TCP through SOCKS5 / HTTP CONNECT proxies.
#   usage: tests/run_multi_test.sh [path/to/j4i-headless]   (run run_lang_test.sh and run_io_test.sh first)
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if command -v cygpath >/dev/null 2>&1; then ROOT="$(cygpath -m "$ROOT")"; fi
B="$ROOT/build"
J4I="${1:-$B/j4i-headless.exe}"
fail=0
cd "$B/tests"
rm -f multi.*.txt
"$J4I" --runtime "$B/j4i-runtime.jar" --instances 4 --log-prefix multi --data "$B/tests/multidata" --main LangTest langtest.jar >/dev/null
for i in 1 2 3 4; do
  sed -E 's/^\[ *[0-9.]+\] //' multi.$i.txt | grep -v '^J2ME4iOS:' | tr -d '\r' > multi.$i.clean
  if diff -q LangTest.expected multi.$i.clean >/dev/null; then echo "PASS instance $i"; else echo "FAIL instance $i"; fail=1; fi
done
# proxies: a local Java proxy tunnels to a local Java echo-free target (the proxy itself); only needs loopback
mkdir -p "$B/tests/net"
javac -d "$B/tests/net" "$ROOT/tests/net/TestProxy.java"
rm -f "$B/tests/net/count.txt"
java -cp "$B/tests/net" TestProxy 18081 18082 "$B/tests/net/count.txt" 120 &
PROXY=$!
trap 'kill $PROXY 2>/dev/null || true' EXIT
for _ in 1 2 3 4 5 6 7 8 9 10; do [ -s "$B/tests/net/count.txt" ] && break; sleep 0.5; done
for p in "socks5h://u:p@127.0.0.1:18081" "socks5://u:p@127.0.0.1:18081" "http://127.0.0.1:18082"; do
  before=$(cat "$B/tests/net/count.txt")
  out="$("$J4I" --runtime "$B/j4i-runtime.jar" --proxy "$p" --data "$B/tests/iodata" --main IoTest --args net "$B/tests/iotest.jar" 2>&1 || true)"
  after=$(cat "$B/tests/net/count.txt")
  if echo "$out" | grep -q "ALL OK" && [ "$after" -gt "$before" ]; then echo "PASS proxy $p"; else echo "FAIL proxy $p"; echo "$out" | tail -5; fail=1; fi
done
exit $fail
