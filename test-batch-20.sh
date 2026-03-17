#!/bin/bash
# =============================================================================
# Test: CloudEvents batch of 20 events in a single webhook POST
# Simulates the max batch size observed in Splunk
# =============================================================================

# Configuration
WEBHOOKS_VERIFIER_TOKEN="${WEBHOOKS_VERIFIER_TOKEN:-d691402a-9b97-4836-9989-938628cc2927}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
BATCH_SIZE="${1:-20}"

echo "============================================="
echo "CloudEvents Batch Test"
echo "============================================="
echo "Batch size:     $BATCH_SIZE"
echo "Target:         $BASE_URL/webhooks"
echo "Verifier token: ${WEBHOOKS_VERIFIER_TOKEN:0:8}..."
echo ""

# Entity types to cycle through (realistic mix)
ENTITY_TYPES=(
  "qbo.customer.created.v1"
  "qbo.customer.updated.v1"
  "qbo.invoice.created.v1"
  "qbo.invoice.updated.v1"
  "qbo.payment.created.v1"
  "qbo.vendor.created.v1"
  "qbo.bill.created.v1"
  "qbo.journalentry.created.v1"
  "qbo.purchase.created.v1"
  "qbo.customer.deleted.v1"
)

# Build the JSON array of N CloudEvents
PAYLOAD="["
for i in $(seq 1 $BATCH_SIZE); do
  TYPE_INDEX=$(( (i - 1) % ${#ENTITY_TYPES[@]} ))
  EVENT_TYPE="${ENTITY_TYPES[$TYPE_INDEX]}"
  EVENT_ID="batch-test-$(printf '%03d' $i)"
  ENTITY_ID=$((100 + i))
  TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")
  
  EVENT=$(cat <<EOF
{
    "specversion": "1.0",
    "id": "$EVENT_ID",
    "type": "$EVENT_TYPE",
    "source": "intuit.com/qbo",
    "time": "$TIMESTAMP",
    "datacontenttype": "application/json",
    "intuitentityid": "$ENTITY_ID",
    "intuitaccountid": "9876543210",
    "data": {"Id": "$ENTITY_ID"}
  }
EOF
)
  
  if [ $i -lt $BATCH_SIZE ]; then
    PAYLOAD="$PAYLOAD$EVENT,"
  else
    PAYLOAD="$PAYLOAD$EVENT"
  fi
done
PAYLOAD="$PAYLOAD]"

# Calculate payload size
PAYLOAD_SIZE=${#PAYLOAD}
echo "Payload size:   $PAYLOAD_SIZE bytes"

# Generate HMAC-SHA256 signature
SIGNATURE=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "$WEBHOOKS_VERIFIER_TOKEN" -binary | base64)
echo "Signature:      ${SIGNATURE:0:20}..."
echo ""

# ---- Test 1: Batch POST ----
echo "============================================="
echo "TEST 1: Batch of $BATCH_SIZE CloudEvents"
echo "============================================="
START_TIME=$(date +%s%N 2>/dev/null || python3 -c 'import time; print(int(time.time()*1000000000))')

HTTP_CODE=$(curl -s -o /tmp/batch-response.txt -w "%{http_code}" \
  -X POST "$BASE_URL/webhooks" \
  -H "Content-Type: application/json" \
  -H "intuit-signature: $SIGNATURE" \
  -d "$PAYLOAD")

END_TIME=$(date +%s%N 2>/dev/null || python3 -c 'import time; print(int(time.time()*1000000000))')

# Calculate duration (macOS compatible)
if [ -n "$START_TIME" ] && [ -n "$END_TIME" ]; then
  DURATION_MS=$(( (END_TIME - START_TIME) / 1000000 ))
  echo "Response time:  ${DURATION_MS}ms"
fi

RESPONSE=$(cat /tmp/batch-response.txt)
echo "HTTP status:    $HTTP_CODE"
echo "Response body:  $RESPONSE"

if [ "$HTTP_CODE" = "200" ]; then
  echo "Result:         ✅ PASS"
else
  echo "Result:         ❌ FAIL (expected 200, got $HTTP_CODE)"
fi
echo ""

# ---- Test 2: Verify event count via dashboard ----
echo "============================================="
echo "TEST 2: Verify stored event count"
echo "============================================="
echo "Check your app logs for:"
echo "  - 'Parsing $BATCH_SIZE CloudEvents webhook(s)'"
echo "  - 'Successfully processed CloudEvents webhook with $BATCH_SIZE event(s)'"
echo "  - $BATCH_SIZE individual 'Stored CloudEvent:' lines"
echo ""
echo "Check dashboard at: $BASE_URL/dashboard"
echo ""

# ---- Test 3: Rapid-fire batches (simulate real traffic) ----
echo "============================================="
echo "TEST 3: Rapid-fire — 3 batch-$BATCH_SIZE posts back-to-back"
echo "============================================="
for round in 1 2 3; do
  # Regenerate with unique IDs per round
  ROUND_PAYLOAD="["
  for i in $(seq 1 $BATCH_SIZE); do
    TYPE_INDEX=$(( (i - 1) % ${#ENTITY_TYPES[@]} ))
    EVENT_TYPE="${ENTITY_TYPES[$TYPE_INDEX]}"
    EVENT_ID="rapid-r${round}-$(printf '%03d' $i)"
    ENTITY_ID=$((200 + i + (round * 100)))
    TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")
    
    EVENT="{\"specversion\":\"1.0\",\"id\":\"$EVENT_ID\",\"type\":\"$EVENT_TYPE\",\"source\":\"intuit.com/qbo\",\"time\":\"$TIMESTAMP\",\"datacontenttype\":\"application/json\",\"intuitentityid\":\"$ENTITY_ID\",\"intuitaccountid\":\"9876543210\",\"data\":{\"Id\":\"$ENTITY_ID\"}}"
    
    if [ $i -lt $BATCH_SIZE ]; then
      ROUND_PAYLOAD="$ROUND_PAYLOAD$EVENT,"
    else
      ROUND_PAYLOAD="$ROUND_PAYLOAD$EVENT"
    fi
  done
  ROUND_PAYLOAD="$ROUND_PAYLOAD]"
  
  ROUND_SIG=$(echo -n "$ROUND_PAYLOAD" | openssl dgst -sha256 -hmac "$WEBHOOKS_VERIFIER_TOKEN" -binary | base64)
  
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$BASE_URL/webhooks" \
    -H "Content-Type: application/json" \
    -H "intuit-signature: $ROUND_SIG" \
    -d "$ROUND_PAYLOAD")
  
  echo "  Round $round: HTTP $HTTP_CODE $([ "$HTTP_CODE" = "200" ] && echo "✅" || echo "❌")"
done

echo ""
echo "============================================="
echo "After rapid-fire, storage should contain 50 events (MAX_WEBHOOKS cap)."
echo "First batch-20 events should be evicted by the 3rd round."
echo "Check: $BASE_URL/dashboard"
echo "============================================="

# Cleanup
rm -f /tmp/batch-response.txt
