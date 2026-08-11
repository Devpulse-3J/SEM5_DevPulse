#!/usr/bin/env bash
# Smoke-test the API gateway after the Docker Compose stack is running.
#
# Usage:
#   ./src/test/test-gateway.sh
#   TEST_EMAIL=you@example.com TEST_PASSWORD=secret ./src/test/test-gateway.sh
#
# Optional environment variables:
#   GATEWAY_URL=http://localhost:8080
#   FRONTEND_ORIGIN=http://localhost:3000

set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
FRONTEND_ORIGIN="${FRONTEND_ORIGIN:-http://localhost:3000}"

pass_count=0

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

pass() {
  echo "PASS: $1"
  pass_count=$((pass_count + 1))
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "'$1' is required"
}

expect_status() {
  local expected_status="$1"
  shift
  local actual_status

  actual_status="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' "$@")"
  [[ "$actual_status" == "$expected_status" ]] || fail "expected HTTP $expected_status, got $actual_status for: curl $*"
}

require_command curl

echo "Testing gateway at $GATEWAY_URL"

expect_status 200 "$GATEWAY_URL/actuator/health"
pass "gateway health endpoint is available"

cors_headers="$(curl --silent --show-error --include --request OPTIONS \
  --header "Origin: $FRONTEND_ORIGIN" \
  --header 'Access-Control-Request-Method: POST' \
  "$GATEWAY_URL/api/auth/login")"
grep -qi "access-control-allow-origin: $FRONTEND_ORIGIN" <<<"$cors_headers" \
  || fail "gateway did not allow the configured frontend origin"
pass "CORS allows $FRONTEND_ORIGIN"

expect_status 401 "$GATEWAY_URL/api/auth/me"
pass "protected auth endpoint rejects requests without a JWT"

if [[ -n "${TEST_EMAIL:-}" || -n "${TEST_PASSWORD:-}" ]]; then
  [[ -n "${TEST_EMAIL:-}" && -n "${TEST_PASSWORD:-}" ]] \
    || fail "set both TEST_EMAIL and TEST_PASSWORD to run the authenticated checks"

  login_response="$(curl --silent --show-error --request POST \
    --header 'Content-Type: application/json' \
    --data "{\"email\":\"$TEST_EMAIL\",\"password\":\"$TEST_PASSWORD\"}" \
    "$GATEWAY_URL/api/auth/login")"
  access_token="$(sed -n 's/.*\"accessToken\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p' <<<"$login_response")"
  [[ -n "$access_token" ]] || fail "login did not return an accessToken"
  pass "public login route reaches auth-service"

  expect_status 200 --header "Authorization: Bearer $access_token" "$GATEWAY_URL/api/auth/me"
  pass "gateway accepts the issued JWT and forwards /api/auth/me"
else
  echo "SKIP: authenticated login check (set TEST_EMAIL and TEST_PASSWORD to enable it)"
fi

echo "Gateway smoke test passed ($pass_count checks)."
