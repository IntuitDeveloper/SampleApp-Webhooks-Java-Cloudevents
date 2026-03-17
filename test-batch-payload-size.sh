#!/bin/bash
# =============================================================================
# Test: CloudEvents batch-20 payload size threshold
# 
# Splunk data shows:
#   - Batch-20 success avg: 1867 bytes/event
#   - Batch-20 failure avg: 1983 bytes/event  
#   - All batch sizes < 20: 0% failure rate
#
# This script tests batch-20 at various per-event byte sizes to find the
# failure threshold.
# =============================================================================

WEBHOOKS_VERIFIER_TOKEN="${WEBHOOKS_VERIFIER_TOKEN:-d691402a-9b97-4836-9989-938628cc2927}"
BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "============================================================"
echo "CloudEvents Batch-20 Payload Size Threshold Test"
echo "============================================================"
echo "Target: $BASE_URL/webhooks"
echo ""

# Function to generate a data blob of approximately N bytes
generate_data_blob() {
  local target_bytes=$1
  # Base data fields are ~100 bytes, so we pad with description text
  local pad_size=$((target_bytes - 150))
  if [ $pad_size -lt 0 ]; then pad_size=0; fi
  # Generate a repeating string of the right length
  local padding=$(python3 -c "
import string
base = 'Lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor incididunt ut labore et dolore magna aliqua Ut enim ad minim veniam quis nostrud exercitation ullamco '
result = (base * (($pad_size // len(base)) + 1))[:$pad_size]
print(result)
")
  echo "{\"Id\":\"123\",\"DisplayName\":\"Test Entity\",\"Description\":\"$padding\"}"
}

# Function to build and send a batch-20 payload with target per-event size
test_batch() {
  local target_per_event_bytes=$1
  local label=$2
  
  # Build 20-event payload
  PAYLOAD="["
  for i in $(seq 1 20); do
    EVENT_ID="size-test-$(printf '%03d' $i)"
    DATA_BLOB=$(generate_data_blob $target_per_event_bytes)
    
    EVENT="{\"specversion\":\"1.0\",\"id\":\"$EVENT_ID\",\"type\":\"qbo.customer.updated.v1\",\"source\":\"intuit.com/qbo\",\"time\":\"2026-03-17T13:00:00.000Z\",\"datacontenttype\":\"application/json\",\"intuitentityid\":\"$i\",\"intuitaccountid\":\"9876543210\",\"data\":$DATA_BLOB}"
    
    if [ $i -lt 20 ]; then
      PAYLOAD="$PAYLOAD$EVENT,"
    else
      PAYLOAD="$PAYLOAD$EVENT"
    fi
  done
  PAYLOAD="$PAYLOAD]"
  
  TOTAL_BYTES=${#PAYLOAD}
  ACTUAL_PER_EVENT=$((TOTAL_BYTES / 20))
  
  SIGNATURE=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "$WEBHOOKS_VERIFIER_TOKEN" -binary | base64)
  
  # Time the request
  START_MS=$(python3 -c 'import time; print(int(time.time()*1000))')
  
  HTTP_CODE=$(curl -s -o /tmp/size-test-response.txt -w "%{http_code}" \
    -X POST "$BASE_URL/webhooks" \
    -H "Content-Type: application/json" \
    -H "intuit-signature: $SIGNATURE" \
    -d "$PAYLOAD")
  
  END_MS=$(python3 -c 'import time; print(int(time.time()*1000))')
  DURATION=$((END_MS - START_MS))
  
  RESPONSE=$(cat /tmp/size-test-response.txt 2>/dev/null)
  
  if [ "$HTTP_CODE" = "200" ]; then
    STATUS="✅ PASS"
  else
    STATUS="❌ FAIL"
  fi
  
  printf "  %-20s | %6d bytes/event | %7d total | HTTP %s | %4dms | %s\n" \
    "$label" "$ACTUAL_PER_EVENT" "$TOTAL_BYTES" "$HTTP_CODE" "$DURATION" "$STATUS"
}

echo ""
echo "  Test                 | Per-Event Size     | Total Payload | Status  | Time   | Result"
echo "  -------------------- | ------------------ | ------------- | ------- | ------ | ------"

# Test 1: Small events (baseline - our previous test size)
test_batch 300 "Small (baseline)"

# Test 2: Medium events (~1500 bytes/event - under threshold)
test_batch 1500 "Medium (1500)"

# Test 3: At success boundary (~1867 bytes/event - Splunk success avg)
test_batch 1867 "Success avg (1867)"

# Test 4: Between success and failure (~1925 bytes/event)
test_batch 1925 "Threshold (1925)"

# Test 5: At failure boundary (~1983 bytes/event - Splunk failure avg)
test_batch 1983 "Failure avg (1983)"

# Test 6: Over failure boundary (~2100 bytes/event)
test_batch 2100 "Over threshold (2100)"

# Test 7: Large events (~2500 bytes/event)
test_batch 2500 "Large (2500)"

# Test 8: Very large events (~3000 bytes/event) 
test_batch 3000 "XL (3000)"

echo ""
echo "============================================================"
echo "Analysis"
echo "============================================================"
echo ""
echo "If ALL tests pass with HTTP 200:"
echo "  → The failure is NOT in your app's webhook handler."
echo "  → The issue is upstream: Intuit's gateway, network, or delivery."
echo "  → The batch-20 + large payload combination may be hitting a"
echo "    gateway timeout or payload size limit on Intuit's side."
echo ""
echo "If tests fail above a certain size:"
echo "  → Check the app logs for the specific error."
echo "  → Possible causes: Jackson deserialization timeout,"
echo "    Spring Boot request body limit, or slow synchronous processing."
echo ""

# Cleanup
rm -f /tmp/size-test-response.txt
