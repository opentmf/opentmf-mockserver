#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Integration smoke-test: dynamic callbacks WITH token enforcement.
#
# Verifies that:
#  - Unauthenticated requests are rejected (401)
#  - Role-based access is enforced (reader/writer/admin → 403 where needed)
#  - Valid tokens allow the expected operations
#  - CRUD lifecycle works end-to-end with tokens
#
# Usage:  ./test-with-token.sh
# Requires: docker, curl, jq
# ---------------------------------------------------------------------------
set -Eeuo pipefail

IMAGE="local/opentmf-mockserver:1.1.2-SNAPSHOT"
CONTAINER_NAME="opentmf-test-with-token"
PORT=11081
BASE="http://localhost:${PORT}"

PASS=0
FAIL=0
ERRORS=()

# ── helpers ──────────────────────────────────────────────────────────────────

red()   { printf '\033[1;31m%s\033[0m\n' "$*"; }
green() { printf '\033[1;32m%s\033[0m\n' "$*"; }
cyan()  { printf '\033[1;36m%s\033[0m\n' "$*"; }

assert_status() {
  local label="$1" expected="$2" actual="$3"
  if [[ "$actual" == "$expected" ]]; then
    green "  PASS  $label (HTTP $actual)"
    PASS=$((PASS + 1))
  else
    red "  FAIL  $label — expected HTTP $expected, got HTTP $actual"
    FAIL=$((FAIL + 1))
    ERRORS+=("$label")
  fi
}

assert_json() {
  local label="$1" jq_expr="$2" expected="$3" body="$4"
  local actual
  actual=$(echo "$body" | jq -r "$jq_expr" 2>/dev/null || echo "__jq_error__")
  if [[ "$actual" == "$expected" ]]; then
    green "  PASS  $label → $jq_expr = $expected"
    PASS=$((PASS + 1))
  else
    red "  FAIL  $label → $jq_expr expected \"$expected\", got \"$actual\""
    FAIL=$((FAIL + 1))
    ERRORS+=("$label ($jq_expr)")
  fi
}

assert_json_not_null() {
  local label="$1" jq_expr="$2" body="$3"
  local actual
  actual=$(echo "$body" | jq -r "$jq_expr" 2>/dev/null || echo "null")
  if [[ "$actual" != "null" && -n "$actual" ]]; then
    green "  PASS  $label → $jq_expr is present"
    PASS=$((PASS + 1))
  else
    red "  FAIL  $label → $jq_expr is null or missing"
    FAIL=$((FAIL + 1))
    ERRORS+=("$label ($jq_expr)")
  fi
}

http() {
  local method="$1" url="$2" data="${3:-}"
  local content_type="${CONTENT_TYPE:-application/json}"
  local auth_header="${AUTH:-}"
  local -a extra_args=()
  if [[ -n "$auth_header" ]]; then
    extra_args+=(-H "Authorization: Bearer $auth_header")
  fi
  if [[ -n "$data" ]]; then
    curl -s -w '\n%{http_code}' -X "$method" "$url" \
      -H "Content-Type: $content_type" -H "Accept: application/json" \
      "${extra_args[@]}" -d "$data"
  else
    curl -s -w '\n%{http_code}' -X "$method" "$url" \
      -H "Accept: application/json" "${extra_args[@]}"
  fi
}

parse_body()   { echo "$1" | sed '$d'; }
parse_status() { echo "$1" | tail -1; }

get_token() {
  # Usage: get_token <form_body>
  local form="$1"
  local resp
  resp=$(curl -s "${BASE}/realms/realm1/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" -d "$form")
  echo "$resp" | jq -r '.access_token'
}

# ── setup ────────────────────────────────────────────────────────────────────

cleanup() {
  cyan "Stopping container..."
  docker rm -f "$CONTAINER_NAME" &>/dev/null || true
}
trap cleanup EXIT

cyan "Building project and Docker image via 'mvn -P docker clean package'..."
mvn -B -P docker -Dmaven.test.skip=true -Dmaven.javadoc.skip=true -Dmaven.source.skip=true \
  -Dgpg.skip=true clean package -q

cyan "Starting MockServer (ENFORCE_TOKEN=true) on port $PORT..."
docker rm -f "$CONTAINER_NAME" &>/dev/null || true
docker run -d --name "$CONTAINER_NAME" -p "${PORT}:1080" \
  -e ENFORCE_TOKEN=true \
  "$IMAGE" >/dev/null

cyan "Waiting for MockServer to be ready..."
for i in $(seq 1 30); do
  if curl -s -X PUT "${BASE}/mockserver/status" &>/dev/null; then break; fi
  sleep 1
done
sleep 2

# ── define expectations ──────────────────────────────────────────────────────

cyan "Registering expectations..."

ORDER_PATH="/tmf-api/serviceOrdering/v4/serviceOrder"
INV_PATH="/tmf-api/serviceInventory/v4/service"

for exp in \
  '{"httpRequest":{"method":"POST","path":"'"$ORDER_PATH"'"},"httpResponseClassCallback":{"callbackClass":"org.opentmf.mockserver.callback.DynamicPostCallback"}}' \
  '{"httpRequest":{"method":"GET","path":"'"$ORDER_PATH"'/.*"},"httpResponseClassCallback":{"callbackClass":"org.opentmf.mockserver.callback.DynamicGetCallback"}}' \
  '{"httpRequest":{"method":"GET","path":"'"$ORDER_PATH"'.*"},"httpResponseClassCallback":{"callbackClass":"org.opentmf.mockserver.callback.DynamicGetListCallback"}}' \
  '{"httpRequest":{"method":"PATCH","path":"'"$ORDER_PATH"'/.*","headers":{"Content-Type":["application/json-patch+json"]}},"httpResponseClassCallback":{"callbackClass":"org.opentmf.mockserver.callback.DynamicJsonPatchCallback"}}' \
  '{"httpRequest":{"method":"PATCH","path":"'"$ORDER_PATH"'/.*","headers":{"Content-Type":["application/merge-patch+json"]}},"httpResponseClassCallback":{"callbackClass":"org.opentmf.mockserver.callback.DynamicMergePatchCallback"}}' \
  '{"httpRequest":{"method":"DELETE","path":"'"$ORDER_PATH"'/.*"},"httpResponseClassCallback":{"callbackClass":"org.opentmf.mockserver.callback.DynamicDeleteCallback"}}' \
  '{"httpRequest":{"method":"POST","path":"'"$INV_PATH"'"},"httpResponseClassCallback":{"callbackClass":"org.opentmf.mockserver.callback.DynamicPostCallback"}}' \
  '{"httpRequest":{"method":"GET","path":"'"$INV_PATH"'/.*"},"httpResponseClassCallback":{"callbackClass":"org.opentmf.mockserver.callback.DynamicGetCallback"}}' \
  '{"httpRequest":{"method":"GET","path":"'"$INV_PATH"'.*"},"httpResponseClassCallback":{"callbackClass":"org.opentmf.mockserver.callback.DynamicGetListCallback"}}' \
  '{"httpRequest":{"method":"DELETE","path":"'"$INV_PATH"'/.*"},"httpResponseClassCallback":{"callbackClass":"org.opentmf.mockserver.callback.DynamicDeleteCallback"}}'; do
  curl -s -X PUT "${BASE}/mockserver/expectation" -H "Content-Type: application/json" -d "$exp" >/dev/null
done

# ── obtain tokens ────────────────────────────────────────────────────────────

cyan "Obtaining tokens for admin, writer, reader..."

ADMIN_TOKEN=$(get_token "grant_type=password&client_id=uiClient&username=admin_usr&password=admin_pwd")
WRITER_TOKEN=$(get_token "grant_type=password&client_id=uiClient&username=writer_usr&password=writer_pwd")
READER_TOKEN=$(get_token "grant_type=password&client_id=uiClient&username=reader_usr&password=reader_pwd")
CLIENT_TOKEN=$(get_token "grant_type=client_credentials&client_id=client1&client_secret=client1Secret")

if [[ "$ADMIN_TOKEN" == "null" || -z "$ADMIN_TOKEN" ]]; then
  red "FATAL: Could not obtain admin token"; exit 1
fi
green "Tokens obtained successfully."

# ════════════════════════════════════════════════════════════════════════════
#  TEST SUITE 1 — Unauthenticated requests (no token → 401)
# ════════════════════════════════════════════════════════════════════════════

cyan "\n══ Unauthenticated Requests (expect 401) ═══════════════════════════"

resp=$(http GET "${BASE}${ORDER_PATH}")
assert_status "GET List without token" 401 "$(parse_status "$resp")"

resp=$(http POST "${BASE}${ORDER_PATH}" '{"name":"test"}')
assert_status "POST without token" 401 "$(parse_status "$resp")"

resp=$(http DELETE "${BASE}${ORDER_PATH}/some-id")
assert_status "DELETE without token" 401 "$(parse_status "$resp")"

# ════════════════════════════════════════════════════════════════════════════
#  TEST SUITE 2 — Reader role: can read, cannot write or delete
# ════════════════════════════════════════════════════════════════════════════

cyan "\n══ Reader Role (read-only) ═════════════════════════════════════════"

# First create data as admin
resp=$(AUTH=$ADMIN_TOKEN http POST "${BASE}${ORDER_PATH}" '{"description":"Reader test"}')
ORDER_ID=$(echo "$(parse_body "$resp")" | jq -r '.id')

# Reader can GET
resp=$(AUTH=$READER_TOKEN http GET "${BASE}${ORDER_PATH}/${ORDER_ID}")
assert_status "Reader GET by ID" 200 "$(parse_status "$resp")"

# Reader can GET List
resp=$(AUTH=$READER_TOKEN http GET "${BASE}${ORDER_PATH}")
assert_status "Reader GET List" 200 "$(parse_status "$resp")"

# Reader cannot POST
resp=$(AUTH=$READER_TOKEN http POST "${BASE}${ORDER_PATH}" '{"description":"nope"}')
assert_status "Reader POST (expect 403)" 403 "$(parse_status "$resp")"
assert_json "Reader POST error" '.error' "insufficient_scope" "$(parse_body "$resp")"

# Reader cannot JSON Patch
resp=$(AUTH=$READER_TOKEN CONTENT_TYPE="application/json-patch+json" http PATCH \
  "${BASE}${ORDER_PATH}/${ORDER_ID}" '[{"op":"replace","path":"/description","value":"nope"}]')
assert_status "Reader JSON-PATCH (expect 403)" 403 "$(parse_status "$resp")"

# Reader cannot Merge Patch
resp=$(AUTH=$READER_TOKEN CONTENT_TYPE="application/merge-patch+json" http PATCH \
  "${BASE}${ORDER_PATH}/${ORDER_ID}" '{"description":"nope"}')
assert_status "Reader MERGE-PATCH (expect 403)" 403 "$(parse_status "$resp")"

# Reader cannot DELETE
resp=$(AUTH=$READER_TOKEN http DELETE "${BASE}${ORDER_PATH}/${ORDER_ID}")
assert_status "Reader DELETE (expect 403)" 403 "$(parse_status "$resp")"

# ════════════════════════════════════════════════════════════════════════════
#  TEST SUITE 3 — Writer role: can read/write, cannot delete
# ════════════════════════════════════════════════════════════════════════════

cyan "\n══ Writer Role (read + write, no delete) ═══════════════════════════"

# Writer can GET
resp=$(AUTH=$WRITER_TOKEN http GET "${BASE}${ORDER_PATH}/${ORDER_ID}")
assert_status "Writer GET by ID" 200 "$(parse_status "$resp")"

# Writer can POST
resp=$(AUTH=$WRITER_TOKEN http POST "${BASE}${ORDER_PATH}" '{"description":"Writer order"}')
status=$(parse_status "$resp"); body=$(parse_body "$resp")
assert_status "Writer POST" 201 "$status"
WRITER_ORDER_ID=$(echo "$body" | jq -r '.id')

# Writer can JSON Patch
resp=$(AUTH=$WRITER_TOKEN CONTENT_TYPE="application/json-patch+json" http PATCH \
  "${BASE}${ORDER_PATH}/${WRITER_ORDER_ID}" \
  '[{"op":"replace","path":"/description","value":"Writer patched"}]')
assert_status "Writer JSON-PATCH" 200 "$(parse_status "$resp")"
assert_json "Writer JSON-PATCH result" '.description' "Writer patched" "$(parse_body "$resp")"

# Writer can Merge Patch
resp=$(AUTH=$WRITER_TOKEN CONTENT_TYPE="application/merge-patch+json" http PATCH \
  "${BASE}${ORDER_PATH}/${WRITER_ORDER_ID}" '{"description":"Writer merged"}')
assert_status "Writer MERGE-PATCH" 200 "$(parse_status "$resp")"

# Writer cannot DELETE
resp=$(AUTH=$WRITER_TOKEN http DELETE "${BASE}${ORDER_PATH}/${WRITER_ORDER_ID}")
assert_status "Writer DELETE (expect 403)" 403 "$(parse_status "$resp")"

# ════════════════════════════════════════════════════════════════════════════
#  TEST SUITE 4 — Admin role: full access
# ════════════════════════════════════════════════════════════════════════════

cyan "\n══ Admin Role (full access) ════════════════════════════════════════"

# Admin can GET
resp=$(AUTH=$ADMIN_TOKEN http GET "${BASE}${ORDER_PATH}/${ORDER_ID}")
assert_status "Admin GET by ID" 200 "$(parse_status "$resp")"

# Admin can GET List
resp=$(AUTH=$ADMIN_TOKEN http GET "${BASE}${ORDER_PATH}")
assert_status "Admin GET List" 200 "$(parse_status "$resp")"

# Admin can POST
resp=$(AUTH=$ADMIN_TOKEN http POST "${BASE}${ORDER_PATH}" '{"description":"Admin order"}')
status=$(parse_status "$resp"); body=$(parse_body "$resp")
assert_status "Admin POST" 201 "$status"
ADMIN_ORDER_ID=$(echo "$body" | jq -r '.id')

# Admin can JSON Patch
resp=$(AUTH=$ADMIN_TOKEN CONTENT_TYPE="application/json-patch+json" http PATCH \
  "${BASE}${ORDER_PATH}/${ADMIN_ORDER_ID}" \
  '[{"op":"replace","path":"/description","value":"Admin patched"}]')
assert_status "Admin JSON-PATCH" 200 "$(parse_status "$resp")"

# Admin can Merge Patch
resp=$(AUTH=$ADMIN_TOKEN CONTENT_TYPE="application/merge-patch+json" http PATCH \
  "${BASE}${ORDER_PATH}/${ADMIN_ORDER_ID}" '{"description":"Admin merged"}')
assert_status "Admin MERGE-PATCH" 200 "$(parse_status "$resp")"

# Admin can DELETE
resp=$(AUTH=$ADMIN_TOKEN http DELETE "${BASE}${ORDER_PATH}/${ADMIN_ORDER_ID}")
assert_status "Admin DELETE" 204 "$(parse_status "$resp")"

# Admin deletes remaining orders
resp=$(AUTH=$ADMIN_TOKEN http DELETE "${BASE}${ORDER_PATH}/${ORDER_ID}")
assert_status "Admin DELETE (reader test order)" 204 "$(parse_status "$resp")"
resp=$(AUTH=$ADMIN_TOKEN http DELETE "${BASE}${ORDER_PATH}/${WRITER_ORDER_ID}")
assert_status "Admin DELETE (writer test order)" 204 "$(parse_status "$resp")"

# ════════════════════════════════════════════════════════════════════════════
#  TEST SUITE 5 — Client credentials token (service account with all roles)
# ════════════════════════════════════════════════════════════════════════════

cyan "\n══ Service Account Token (client_credentials) ═══════════════════"

resp=$(AUTH=$CLIENT_TOKEN http POST "${BASE}${ORDER_PATH}" '{"description":"Service account order"}')
status=$(parse_status "$resp"); body=$(parse_body "$resp")
assert_status "Service POST" 201 "$status"
SVC_ID=$(echo "$body" | jq -r '.id')

resp=$(AUTH=$CLIENT_TOKEN http GET "${BASE}${ORDER_PATH}/${SVC_ID}")
assert_status "Service GET" 200 "$(parse_status "$resp")"

resp=$(AUTH=$CLIENT_TOKEN http DELETE "${BASE}${ORDER_PATH}/${SVC_ID}")
assert_status "Service DELETE" 204 "$(parse_status "$resp")"

# ════════════════════════════════════════════════════════════════════════════
#  TEST SUITE 6 — Lifecycle with tokens (Inventory: status field)
# ════════════════════════════════════════════════════════════════════════════

cyan "\n══ Inventory lifecycle with tokens ═════════════════════════════════"

resp=$(AUTH=$ADMIN_TOKEN http POST "${BASE}${INV_PATH}" '{"name":"Svc1"}')
status=$(parse_status "$resp"); body=$(parse_body "$resp")
assert_status "POST Inventory" 201 "$status"
INV_ID=$(echo "$body" | jq -r '.id')
assert_json "Inventory initial status" '.status' "created" "$body"

resp=$(AUTH=$READER_TOKEN http GET "${BASE}${INV_PATH}/${INV_ID}")
body=$(parse_body "$resp")
assert_status "GET Inventory (reader)" 200 "$(parse_status "$resp")"
assert_json "Inventory final status" '.status' "active" "$body"

resp=$(AUTH=$ADMIN_TOKEN http DELETE "${BASE}${INV_PATH}/${INV_ID}")
assert_status "DELETE Inventory" 204 "$(parse_status "$resp")"

# ════════════════════════════════════════════════════════════════════════════
#  TEST SUITE 7 — Malformed / expired tokens
# ════════════════════════════════════════════════════════════════════════════

cyan "\n══ Bad Tokens ══════════════════════════════════════════════════════"

resp=$(AUTH="not.a.valid.jwt" http GET "${BASE}${ORDER_PATH}")
assert_status "Garbage token" 401 "$(parse_status "$resp")"

# Tamper with a valid token (flip last character of signature)
TAMPERED="${ADMIN_TOKEN%?}X"
resp=$(AUTH="$TAMPERED" http GET "${BASE}${ORDER_PATH}")
assert_status "Tampered token" 401 "$(parse_status "$resp")"

resp=$(AUTH="" http GET "${BASE}${ORDER_PATH}")
assert_status "Empty bearer" 401 "$(parse_status "$resp")"

# ── summary ──────────────────────────────────────────────────────────────────

echo ""
cyan "════════════════════════════════════════════════════════════════════"
if [[ $FAIL -eq 0 ]]; then
  green "ALL $PASS TESTS PASSED"
else
  red "$FAIL FAILED, $PASS passed"
  red "Failures:"
  for e in "${ERRORS[@]}"; do
    red "  - $e"
  done
fi
cyan "════════════════════════════════════════════════════════════════════"

exit "$FAIL"
