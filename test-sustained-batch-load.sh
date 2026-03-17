#!/bin/bash
# =============================================================================
# Test: Sustained batch-20 webhook traffic over time
#
# Simulates what developers experience:
#   - Continuous batch-20 CloudEvents hitting their webhook endpoint
#   - Measures response time degradation over time
#   - Detects when failures start occurring
#   - Tests rate limiting behavior
#
# Splunk observation: failures appear after ~20 minutes of batch-20 traffic
# =============================================================================

WEBHOOKS_VERIFIER_TOKEN="${WEBHOOKS_VERIFIER_TOKEN:-d691402a-9b97-4836-9989-938628cc2927}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
DURATION_MINUTES="${1:-5}"        # Default 5 min (use 20 for full reproduction)
INTERVAL_SECONDS="${2:-10}"       # Seconds between batches
BATCH_SIZE=20
TARGET_PER_EVENT_BYTES=1983       # Match Splunk failure avg

echo "============================================================"
echo "Sustained Batch-20 Load Test"
echo "============================================================"
echo "Duration:        $DURATION_MINUTES minutes"
echo "Interval:        every ${INTERVAL_SECONDS}s"
echo "Batch size:      $BATCH_SIZE events"
echo "Per-event size:  ~${TARGET_PER_EVENT_BYTES} bytes"
echo "Target:          $BASE_URL/webhooks"
echo ""
echo "Expected batches: $(( (DURATION_MINUTES * 60) / INTERVAL_SECONDS ))"
echo "Expected events:  $(( (DURATION_MINUTES * 60) / INTERVAL_SECONDS * BATCH_SIZE ))"
echo ""
echo "  Batch# | Time     | HTTP | Response ms | Total bytes | Events so far | Status"
echo "  ------ | -------- | ---- | ----------- | ----------- | ------------- | ------"

# Counters
TOTAL_SUCCESS=0
TOTAL_FAILURE=0
TOTAL_EVENTS=0
START_EPOCH=$(date +%s)
END_EPOCH=$((START_EPOCH + DURATION_MINUTES * 60))
BATCH_NUM=0

# Generate padding for target per-event size
PADDING=$(python3 -c "
base = 'Lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor incididunt ut labore et dolore magna aliqua Ut enim ad minim veniam quis nostrud exercitation ullamco '
pad_size = $TARGET_PER_EVENT_BYTES - 250
result = (base * ((pad_size // len(base)) + 1))[:pad_size]
print(result)
")

while [ $(date +%s) -lt $END_EPOCH ]; do
  BATCH_NUM=$((BATCH_NUM + 1))
  ELAPSED=$(( $(date +%s) - START_EPOCH ))
  ELAPSED_MIN=$((ELAPSED / 60))
  ELAPSED_SEC=$((ELAPSED % 60))
  TIMESTAMP_LABEL=$(printf "%02d:%02d" $ELAPSED_MIN $ELAPSED_SEC)
  
  # Build batch-20 payload with unique IDs per batch
  PAYLOAD="["
  for i in $(seq 1 $BATCH_SIZE); do
    EVENT_ID="sustained-b${BATCH_NUM}-e$(printf '%03d' $i)"
    ENTITY_ID=$((BATCH_NUM * 100 + i))
    TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")
    
    # Cycle through entity types
    TYPES=("qbo.customer.created.v1" "qbo.customer.updated.v1" "qbo.invoice.created.v1" "qbo.invoice.updated.v1" "qbo.payment.created.v1" "qbo.vendor.created.v1" "qbo.bill.created.v1" "qbo.journalentry.created.v1" "qbo.purchase.created.v1" "qbo.customer.deleted.v1")
    TYPE_INDEX=$(( (i - 1) % ${#TYPES[@]} ))
    EVENT_TYPE="${TYPES[$TYPE_INDEX]}"
    
    EVENT="{\"specversion\":\"1.0\",\"id\":\"$EVENT_ID\",\"type\":\"$EVENT_TYPE\",\"source\":\"intuit.com/qbo\",\"time\":\"$TIMESTAMP\",\"datacontenttype\":\"application/json\",\"intuitentityid\":\"$ENTITY_ID\",\"intuitaccountid\":\"9876543210\",\"data\":{\"Id\":\"$ENTITY_ID\",\"DisplayName\":\"Entity $ENTITY_ID\",\"Description\":\"$PADDING\"}}"
    
    if [ $i -lt $BATCH_SIZE ]; then
      PAYLOAD="$PAYLOAD$EVENT,"
    else
      PAYLOAD="$PAYLOAD$EVENT"
    fi
  done
  PAYLOAD="$PAYLOAD]"
  
  TOTAL_BYTES=${#PAYLOAD}
  
  # Sign the payload
  SIGNATURE=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "$WEBHOOKS_VERIFIER_TOKEN" -binary | base64)
  
  # Send and time the request
  REQ_START=$(python3 -c 'import time; print(int(time.time()*1000))')
  
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
    --max-time 30 \
    -X POST "$BASE_URL/webhooks" \
    -H "Content-Type: application/json" \
    -H "intuit-signature: $SIGNATURE" \
    -d "$PAYLOAD")
  
  REQ_END=$(python3 -c 'import time; print(int(time.time()*1000))')
  DURATION_MS=$((REQ_END - REQ_START))
  
  TOTAL_EVENTS=$((TOTAL_EVENTS + BATCH_SIZE))
  
  if [ "$HTTP_CODE" = "200" ]; then
    TOTAL_SUCCESS=$((TOTAL_SUCCESS + 1))
    STATUS="✅"
  elif [ "$HTTP_CODE" = "000" ]; then
    TOTAL_FAILURE=$((TOTAL_FAILURE + 1))
    STATUS="⏱️ TIMEOUT"
  elif [ "$HTTP_CODE" = "429" ]; then
    TOTAL_FAILURE=$((TOTAL_FAILURE + 1))
    STATUS="🚫 RATE LIMITED"
  elif [ "$HTTP_CODE" = "500" ]; then
    TOTAL_FAILURE=$((TOTAL_FAILURE + 1))
    STATUS="💥 SERVER ERROR"
  elif [ "$HTTP_CODE" = "503" ]; then
    TOTAL_FAILURE=$((TOTAL_FAILURE + 1))
    STATUS="🔧 SERVICE UNAVAILABLE"
  else
    TOTAL_FAILURE=$((TOTAL_FAILURE + 1))
    STATUS="❌ HTTP $HTTP_CODE"
  fi
  
  # Flag slow responses (potential timeout candidates)
  SLOW_FLAG=""
  if [ $DURATION_MS -gt 5000 ]; then
    SLOW_FLAG=" ⚠️ SLOW"
  fi
  
  printf "  %6d | %s | %4s | %7dms   | %11d | %13d | %s%s\n" \
    "$BATCH_NUM" "$TIMESTAMP_LABEL" "$HTTP_CODE" "$DURATION_MS" "$TOTAL_BYTES" "$TOTAL_EVENTS" "$STATUS" "$SLOW_FLAG"
  
  # Wait before next batch
  sleep $INTERVAL_SECONDS
done

echo ""
echo "============================================================"
echo "Results"
echo "============================================================"
echo "Duration:        $DURATION_MINUTES minutes"
echo "Total batches:   $BATCH_NUM"
echo "Total events:    $TOTAL_EVENTS"
echo "Successes:       $TOTAL_SUCCESS"
echo "Failures:        $TOTAL_FAILURE"
if [ $BATCH_NUM -gt 0 ]; then
  FAILURE_RATE=$(python3 -c "print(f'{($TOTAL_FAILURE / $BATCH_NUM) * 100:.1f}%')")
  echo "Failure rate:    $FAILURE_RATE"
fi
echo ""
echo "============================================================"
echo "Interpretation"
echo "============================================================"
echo ""
if [ $TOTAL_FAILURE -eq 0 ]; then
  echo "✅ Zero failures over $DURATION_MINUTES minutes of sustained batch-20 traffic."
  echo ""
  echo "This means the developer's failure is likely caused by:"
  echo ""
  echo "  1. THEIR rate limiter (AWS API Gateway default: 10K req/s,"
  echo "     but custom limits are common — check if they use WAF/ALB/nginx)"
  echo ""
  echo "  2. THEIR API callback rate limit (if they call QBO API per event):"
  echo "     - QBO throttle: ~500 req/min/realm"
  echo "     - 20 events/batch × 6 batches/min = 120 API calls/min"
  echo "     - If they fan out or retry: can exceed 500/min"
  echo ""
  echo "  3. THEIR synchronous processing timeout:"
  echo "     - If processing 20 events + API callbacks takes > 10s"
  echo "     - Our gateway may time out and retry"
  echo "     - Retry doubles their load → cascade failure at ~20 min"
  echo ""
  echo "  4. THEIR connection pool exhaustion:"
  echo "     - 20 concurrent outbound connections per batch"
  echo "     - Sustained over 20 min → pool fills → requests queue → timeout"
else
  echo "❌ Failures detected! Check app logs for the specific error pattern."
  echo "   The failure started at batch #$(( TOTAL_SUCCESS + 1 ))."
fi
