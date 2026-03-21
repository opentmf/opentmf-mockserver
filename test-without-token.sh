#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Integration smoke-test: dynamic callbacks WITHOUT token enforcement.
#
# Tests POST, GET by ID, GET List, JSON Patch, Merge Patch, DELETE across
# three TMF path types (Order, Inventory, Catalog) to exercise different
# lifecycle status field decisions.
#
# Usage:  ./test-without-token.sh
# Requires: docker, curl, jq
# ---------------------------------------------------------------------------
set -Eeuo pipefail

IMAGE="local/opentmf-mockserver:1.1.2-SNAPSHOT"
CONTAINER_NAME="opentmf-test-no-token"
PORT=11080
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

assert_header() {
  local label="$1" header="$2" expected_pattern="$3" headers="$4"
  local value
  value=$(echo "$headers" | grep -i "^${header}:" | head -1 | sed 's/^[^:]*: *//' | tr -d '\r')
  if [[ "$value" == *"$expected_pattern"* ]]; then
    green "  PASS  $label → $header contains \"$expected_pattern\""
    PASS=$((PASS + 1))
  else
    red "  FAIL  $label → $header expected to contain \"$expected_pattern\", got \"$value\""
    FAIL=$((FAIL + 1))
    ERRORS+=("$label ($header)")
  fi
}

http() {
  # Usage: http METHOD URL [data]
  # Returns: status_code\n<body>   (status in first line, body in rest)
  local method="$1" url="$2" data="${3:-}"
  local content_type="${CONTENT_TYPE:-application/json}"
  if [[ -n "$data" ]]; then
    curl -s -w '\n%{http_code}' -X "$method" "$url" \
      -H "Content-Type: $content_type" -H "Accept: application/json" \
      -d "$data"
  else
    curl -s -w '\n%{http_code}' -X "$method" "$url" -H "Accept: application/json"
  fi
}

http_with_headers() {
  # Returns full response with headers; status code appended as last line
  local method="$1" url="$2"
  curl -s -i -w '\n%{http_code}' -X "$method" "$url" -H "Accept: application/json"
}

parse_body()   { echo "$1" | sed '$d'; }
parse_status() { echo "$1" | tail -1; }

# ── setup ────────────────────────────────────────────────────────────────────

cleanup() {
  cyan "Stopping container..."
  docker rm -f "$CONTAINER_NAME" &>/dev/null || true
}
trap cleanup EXIT

cyan "Building project and Docker image via 'mvn -P docker clean package'..."
mvn -B -P docker -Dmaven.test.skip=true -Dmaven.javadoc.skip=true -Dmaven.source.skip=true \
  -Dgpg.skip=true clean package -q

cyan "Starting MockServer (no token enforcement) on port $PORT..."
docker rm -f "$CONTAINER_NAME" &>/dev/null || true
docker run -d --name "$CONTAINER_NAME" -p "${PORT}:1080" "$IMAGE" >/dev/null

cyan "Waiting for MockServer to be ready..."
for i in $(seq 1 30); do
  if curl -s -X PUT "${BASE}/mockserver/status" &>/dev/null; then break; fi
  sleep 1
done
sleep 2

# ── define expectations ──────────────────────────────────────────────────────

cyan "Registering expectations..."

# Order endpoints (state: acknowledged → completed)
ORDER_PATH="/tmf-api/serviceOrdering/v4/serviceOrder"
for exp in \
  '{"httpRequest":{"method":"POST","path":"'"$ORDER_PATH"'"},"httpResponseClassCallback":{"callbackClass":"org.opentmf.mockserver.callback.DynamicPostCallback"}}' \
  '{"httpRequest":{"method":"GET","path":"'"$ORDER_PATH"'/.*"},"httpResponseClassCallback":{"callbackClass":"org.opentmf.mockserver.callback.DynamicGetCallback"}}' \
  '{"httpRequest":{"method":"GET","path":"'"$ORDER_PATH"'.*"},"httpResponseClassCallback":{"callbackClass":"org.opentmf.mockserver.callback.DynamicGetListCallback"}}' \
  '{"httpRequest":{"method":"PATCH","path":"'"$ORDER_PATH"'/.*","headers":{"Content-Type":["application/json-patch+json"]}},"httpResponseClassCallback":{"callbackClass":"org.opentmf.mockserver.callback.DynamicJsonPatchCallback"}}' \
  '{"httpRequest":{"method":"PATCH","path":"'"$ORDER_PATH"'/.*","headers":{"Content-Type":["application/merge-patch+json"]}},"httpResponseClassCallback":{"callbackClass":"org.opentmf.mockserver.callback.DynamicMergePatchCallback"}}' \
  '{"httpRequest":{"method":"DELETE","path":"'"$ORDER_PATH"'/.*"},"httpResponseClassCallback":{"callbackClass":"org.opentmf.mockserver.callback.DynamicDeleteCallback"}}'; do
  curl -s -X PUT "${BASE}/mockserver/expectation" -H "Content-Type: application/json" -d "$exp" >/dev/null
done

# Inventory endpoints (status: created → active)
INV_PATH="/tmf-api/serviceInventory/v4/service"
for exp in \
  '{"httpRequest":{"method":"POST","path":"'"$INV_PATH"'"},"httpResponseClassCallback":{"callbackClass":"org.opentmf.mockserver.callback.DynamicPostCallback"}}' \
  '{"httpRequest":{"method":"GET","path":"'"$INV_PATH"'/.*"},"httpResponseClassCallback":{"callbackClass":"org.opentmf.mockserver.callback.DynamicGetCallback"}}' \
  '{"httpRequest":{"method":"GET","path":"'"$INV_PATH"'.*"},"httpResponseClassCallback":{"callbackClass":"org.opentmf.mockserver.callback.DynamicGetListCallback"}}' \
  '{"httpRequest":{"method":"DELETE","path":"'"$INV_PATH"'/.*"},"httpResponseClassCallback":{"callbackClass":"org.opentmf.mockserver.callback.DynamicDeleteCallback"}}'; do
  curl -s -X PUT "${BASE}/mockserver/expectation" -H "Content-Type: application/json" -d "$exp" >/dev/null
done

# Catalog endpoints (lifecycleStatus: In design → Launched, versioned)
CAT_PATH="/tmf-api/serviceCatalog/v4/serviceCatalog"
for exp in \
  '{"httpRequest":{"method":"POST","path":"'"$CAT_PATH"'"},"httpResponseClassCallback":{"callbackClass":"org.opentmf.mockserver.callback.DynamicPostCallback"}}' \
  '{"httpRequest":{"method":"GET","path":"'"$CAT_PATH"'/.*"},"httpResponseClassCallback":{"callbackClass":"org.opentmf.mockserver.callback.DynamicGetCallback"}}' \
  '{"httpRequest":{"method":"GET","path":"'"$CAT_PATH"'.*"},"httpResponseClassCallback":{"callbackClass":"org.opentmf.mockserver.callback.DynamicGetListCallback"}}' \
  '{"httpRequest":{"method":"PATCH","path":"'"$CAT_PATH"'/.*","headers":{"Content-Type":["application/merge-patch+json"]}},"httpResponseClassCallback":{"callbackClass":"org.opentmf.mockserver.callback.DynamicMergePatchCallback"}}' \
  '{"httpRequest":{"method":"DELETE","path":"'"$CAT_PATH"'/.*"},"httpResponseClassCallback":{"callbackClass":"org.opentmf.mockserver.callback.DynamicDeleteCallback"}}'; do
  curl -s -X PUT "${BASE}/mockserver/expectation" -H "Content-Type: application/json" -d "$exp" >/dev/null
done

# ════════════════════════════════════════════════════════════════════════════
#  TEST SUITE 1 — Orders (state: acknowledged → completed)
# ════════════════════════════════════════════════════════════════════════════

cyan "\n══ Orders (state field) ════════════════════════════════════════════"

# POST two orders
resp=$(http POST "${BASE}${ORDER_PATH}" '{"description":"Order A"}')
status=$(parse_status "$resp"); body=$(parse_body "$resp")
assert_status "POST Order A" 201 "$status"
ORDER_A_ID=$(echo "$body" | jq -r '.id')
assert_json_not_null "POST Order A" '.id' "$body"
assert_json "POST Order A initial state" '.state' "acknowledged" "$body"
assert_json_not_null "POST Order A" '.createdDate' "$body"
assert_json_not_null "POST Order A" '.href' "$body"

resp=$(http POST "${BASE}${ORDER_PATH}" '{"description":"Order B"}')
status=$(parse_status "$resp"); body=$(parse_body "$resp")
assert_status "POST Order B" 201 "$status"
ORDER_B_ID=$(echo "$body" | jq -r '.id')

# POST duplicate ID should fail
resp=$(http POST "${BASE}${ORDER_PATH}" '{"id":"'"$ORDER_A_ID"'","description":"Dup"}')
status=$(parse_status "$resp")
assert_status "POST duplicate Order A" 400 "$status"

# GET by ID — first GET transitions state
resp=$(http GET "${BASE}${ORDER_PATH}/${ORDER_A_ID}")
status=$(parse_status "$resp"); body=$(parse_body "$resp")
assert_status "GET Order A" 200 "$status"
assert_json "GET Order A final state" '.state' "completed" "$body"
assert_json_not_null "GET Order A" '.updatedDate' "$body"

# GET List
resp_full=$(http_with_headers GET "${BASE}${ORDER_PATH}")
status=$(parse_status "$resp_full")
headers=$(echo "$resp_full" | sed '/^\r*$/q')
body_lines=$(echo "$resp_full" | sed '1,/^\r*$/d' | sed '$d')
assert_status "GET List Orders" 200 "$status"
assert_header "GET List Orders" "X-Total-Count" "2" "$headers"

# JSON Patch
resp=$(CONTENT_TYPE="application/json-patch+json" http PATCH \
  "${BASE}${ORDER_PATH}/${ORDER_A_ID}" \
  '[{"op":"replace","path":"/description","value":"Order A patched"}]')
status=$(parse_status "$resp"); body=$(parse_body "$resp")
assert_status "JSON-PATCH Order A" 200 "$status"
assert_json "JSON-PATCH result" '.description' "Order A patched" "$body"
assert_json_not_null "JSON-PATCH" '.updatedDate' "$body"

# Merge Patch
resp=$(CONTENT_TYPE="application/merge-patch+json" http PATCH \
  "${BASE}${ORDER_PATH}/${ORDER_B_ID}" '{"description":"Order B merged"}')
status=$(parse_status "$resp"); body=$(parse_body "$resp")
assert_status "MERGE-PATCH Order B" 200 "$status"
assert_json "MERGE-PATCH result" '.description' "Order B merged" "$body"

# DELETE
resp=$(http DELETE "${BASE}${ORDER_PATH}/${ORDER_A_ID}")
status=$(parse_status "$resp")
assert_status "DELETE Order A" 204 "$status"

# GET after delete → 404
resp=$(http GET "${BASE}${ORDER_PATH}/${ORDER_A_ID}")
status=$(parse_status "$resp")
assert_status "GET deleted Order A" 404 "$status"

# GET List after delete → only 1
resp_full=$(http_with_headers GET "${BASE}${ORDER_PATH}")
headers=$(echo "$resp_full" | sed '/^\r*$/q')
assert_header "GET List after delete" "X-Total-Count" "1" "$headers"

# Clean up order B
http DELETE "${BASE}${ORDER_PATH}/${ORDER_B_ID}" >/dev/null

# ════════════════════════════════════════════════════════════════════════════
#  TEST SUITE 2 — Inventory (status: created → active)
# ════════════════════════════════════════════════════════════════════════════

cyan "\n══ Inventory (status field) ════════════════════════════════════════"

resp=$(http POST "${BASE}${INV_PATH}" '{"name":"Service X"}')
status=$(parse_status "$resp"); body=$(parse_body "$resp")
assert_status "POST Inventory" 201 "$status"
INV_ID=$(echo "$body" | jq -r '.id')
assert_json "POST Inventory initial status" '.status' "created" "$body"

# GET → transitions to active
resp=$(http GET "${BASE}${INV_PATH}/${INV_ID}")
status=$(parse_status "$resp"); body=$(parse_body "$resp")
assert_status "GET Inventory" 200 "$status"
assert_json "GET Inventory final status" '.status' "active" "$body"

# Second GET should stay at active
resp=$(http GET "${BASE}${INV_PATH}/${INV_ID}")
body=$(parse_body "$resp")
assert_json "GET Inventory stable status" '.status' "active" "$body"

# DELETE
resp=$(http DELETE "${BASE}${INV_PATH}/${INV_ID}")
status=$(parse_status "$resp")
assert_status "DELETE Inventory" 204 "$status"

# ════════════════════════════════════════════════════════════════════════════
#  TEST SUITE 3 — Catalog (lifecycleStatus: In design → Launched, versioned)
# ════════════════════════════════════════════════════════════════════════════

cyan "\n══ Catalog (lifecycleStatus field, versioned) ════════════════════"

resp=$(http POST "${BASE}${CAT_PATH}" '{"name":"My Catalog"}')
status=$(parse_status "$resp"); body=$(parse_body "$resp")
assert_status "POST Catalog" 201 "$status"
CAT_ID=$(echo "$body" | jq -r '.id')
CAT_VERSION=$(echo "$body" | jq -r '.version')
assert_json "POST Catalog initial lifecycleStatus" '.lifecycleStatus' "In design" "$body"
assert_json_not_null "POST Catalog has version" '.version' "$body"

# GET by ID with version
resp=$(http GET "${BASE}${CAT_PATH}/${CAT_ID}?version=${CAT_VERSION}")
status=$(parse_status "$resp"); body=$(parse_body "$resp")
assert_status "GET Catalog" 200 "$status"
assert_json "GET Catalog final lifecycleStatus" '.lifecycleStatus' "Launched" "$body"

# Merge Patch
resp=$(CONTENT_TYPE="application/merge-patch+json" http PATCH \
  "${BASE}${CAT_PATH}/${CAT_ID}?version=${CAT_VERSION}" '{"name":"Updated Catalog"}')
status=$(parse_status "$resp"); body=$(parse_body "$resp")
assert_status "MERGE-PATCH Catalog" 200 "$status"
assert_json "MERGE-PATCH Catalog name" '.name' "Updated Catalog" "$body"

# GET List
resp_full=$(http_with_headers GET "${BASE}${CAT_PATH}")
status=$(parse_status "$resp_full")
assert_status "GET List Catalog" 200 "$status"

# DELETE
resp=$(http DELETE "${BASE}${CAT_PATH}/${CAT_ID}?version=${CAT_VERSION}")
status=$(parse_status "$resp")
assert_status "DELETE Catalog" 204 "$status"

# ════════════════════════════════════════════════════════════════════════════
#  TEST SUITE 4 — Edge cases
# ════════════════════════════════════════════════════════════════════════════

cyan "\n══ Edge Cases ══════════════════════════════════════════════════════"

# GET non-existent → 404
resp=$(http GET "${BASE}${ORDER_PATH}/does-not-exist")
status=$(parse_status "$resp")
assert_status "GET non-existent" 404 "$status"

# DELETE non-existent → 404
resp=$(http DELETE "${BASE}${ORDER_PATH}/does-not-exist")
status=$(parse_status "$resp")
assert_status "DELETE non-existent" 404 "$status"

# GET List on empty domain → 200 with empty array
resp=$(http GET "${BASE}${ORDER_PATH}")
status=$(parse_status "$resp"); body=$(parse_body "$resp")
assert_status "GET List empty" 200 "$status"
count=$(echo "$body" | jq 'length')
if [[ "$count" == "0" ]]; then
  green "  PASS  GET List empty → 0 items"
  PASS=$((PASS + 1))
else
  red "  FAIL  GET List empty → expected 0 items, got $count"
  FAIL=$((FAIL + 1))
  ERRORS+=("GET List empty count")
fi

# POST with explicit state (should be preserved)
resp=$(http POST "${BASE}${ORDER_PATH}" '{"state":"custom-state"}')
status=$(parse_status "$resp"); body=$(parse_body "$resp")
assert_status "POST with custom state" 201 "$status"
assert_json "POST custom state preserved" '.state' "custom-state" "$body"
CUS_ID=$(echo "$body" | jq -r '.id')
http DELETE "${BASE}${ORDER_PATH}/${CUS_ID}" >/dev/null

# ════════════════════════════════════════════════════════════════════════════
#  TEST SUITE 5 — Keycloak mock endpoints (always available)
# ════════════════════════════════════════════════════════════════════════════

cyan "\n══ Keycloak Mock Endpoints ═════════════════════════════════════════"

# OIDC discovery
resp=$(http GET "${BASE}/realms/realm1/.well-known/openid-configuration")
status=$(parse_status "$resp"); body=$(parse_body "$resp")
assert_status "OIDC discovery" 200 "$status"
assert_json_not_null "OIDC discovery" '.jwks_uri' "$body"
assert_json_not_null "OIDC discovery" '.token_endpoint' "$body"

# JWKS
resp=$(http GET "${BASE}/realms/realm1/protocol/openid-connect/certs")
status=$(parse_status "$resp"); body=$(parse_body "$resp")
assert_status "JWKS endpoint" 200 "$status"
assert_json_not_null "JWKS" '.keys[0].kid' "$body"

# Global JWKS
resp=$(http GET "${BASE}/.well-known/jwks.json")
status=$(parse_status "$resp")
assert_status "Global JWKS" 200 "$status"

# Token: client_credentials
resp=$(CONTENT_TYPE="application/x-www-form-urlencoded" http POST \
  "${BASE}/realms/realm1/protocol/openid-connect/token" \
  "grant_type=client_credentials&client_id=client1&client_secret=client1Secret")
status=$(parse_status "$resp"); body=$(parse_body "$resp")
assert_status "Token client_credentials" 200 "$status"
assert_json_not_null "Token" '.access_token' "$body"
assert_json "Token type" '.token_type' "Bearer" "$body"

# Token: password grant
resp=$(CONTENT_TYPE="application/x-www-form-urlencoded" http POST \
  "${BASE}/realms/realm1/protocol/openid-connect/token" \
  "grant_type=password&client_id=uiClient&username=admin_usr&password=admin_pwd")
status=$(parse_status "$resp"); body=$(parse_body "$resp")
assert_status "Token password grant" 200 "$status"
assert_json_not_null "Token password" '.access_token' "$body"
assert_json_not_null "Token password" '.refresh_token' "$body"

# Token: bad credentials
resp=$(CONTENT_TYPE="application/x-www-form-urlencoded" http POST \
  "${BASE}/realms/realm1/protocol/openid-connect/token" \
  "grant_type=password&client_id=uiClient&username=admin_usr&password=WRONG")
status=$(parse_status "$resp")
assert_status "Token bad password" 401 "$status"

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
