#!/bin/bash
# Demo script for Document Q&A Assistant
# Demonstrates: upload → poll status → chat → follow-up → streaming → tenant isolation
#
# Prerequisites:
#   1. Docker Desktop running
#   2. OPENAI_API_KEY exported in environment
#   3. System running via: docker compose up -d

set -e

BASE_URL=${BASE_URL:-http://localhost:8080}
TENANT_A="school-alpha"
TENANT_B="school-beta"

echo "============================================"
echo "  Document Q&A Assistant — Live Demo"
echo "============================================"

# Health check
echo ""
echo "1. Health Check"
echo "──────────────────────────────"
curl -s "$BASE_URL/actuator/health" | python3 -m json.tool 2>/dev/null || curl -s "$BASE_URL/actuator/health"
echo ""

# Upload fee policy
echo ""
echo "2. Upload Fee Policy (returns 202 Accepted)"
echo "──────────────────────────────"
FEE_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/v1/documents" \
  -H "X-Tenant-Id: $TENANT_A" \
  -F "file=@demo/fee-policy.md" \
  -F "title=Fee Policy 2024-2025" \
  -F "category=FEES")
FEE_HTTP_CODE=$(echo "$FEE_RESPONSE" | tail -1)
FEE_BODY=$(echo "$FEE_RESPONSE" | sed '$d')
echo "HTTP $FEE_HTTP_CODE"
echo "$FEE_BODY" | python3 -m json.tool 2>/dev/null || echo "$FEE_BODY"
FEE_DOC_ID=$(echo "$FEE_BODY" | grep -o '"documentId":"[^"]*"' | cut -d'"' -f4)
echo ""

# Upload transport policy
echo ""
echo "3. Upload Transport Policy"
echo "──────────────────────────────"
curl -s -X POST "$BASE_URL/api/v1/documents" \
  -H "X-Tenant-Id: $TENANT_A" \
  -F "file=@demo/transport-policy.md" \
  -F "title=Transport Policy 2024-2025" \
  -F "category=TRANSPORT" | python3 -m json.tool 2>/dev/null
echo ""

# Upload exam policy
echo ""
echo "4. Upload Exam Policy"
echo "──────────────────────────────"
curl -s -X POST "$BASE_URL/api/v1/documents" \
  -H "X-Tenant-Id: $TENANT_A" \
  -F "file=@demo/exam-policy.md" \
  -F "title=Examination Policy 2024-2025" \
  -F "category=ACADEMICS" | python3 -m json.tool 2>/dev/null
echo ""

# Wait for ingestion
echo ""
echo "5. Waiting for async ingestion to complete..."
echo "──────────────────────────────"
sleep 10

# Poll document status
echo ""
echo "6. Check Document Status (should be READY)"
echo "──────────────────────────────"
curl -s "$BASE_URL/api/v1/documents/$FEE_DOC_ID" \
  -H "X-Tenant-Id: $TENANT_A" | python3 -m json.tool 2>/dev/null
echo ""

# List all documents
echo ""
echo "7. List All Documents"
echo "──────────────────────────────"
curl -s "$BASE_URL/api/v1/documents" \
  -H "X-Tenant-Id: $TENANT_A" | python3 -m json.tool 2>/dev/null
echo ""

# Chat — answerable question
echo ""
echo "8. Chat — Answerable Question"
echo "──────────────────────────────"
echo "Q: What is the tuition fee for Class 6?"
CHAT_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/chat" \
  -H "X-Tenant-Id: $TENANT_A" \
  -H "Content-Type: application/json" \
  -d '{"question": "What is the tuition fee for Class 6?", "category": "FEES"}')
echo "$CHAT_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$CHAT_RESPONSE"
CONV_ID=$(echo "$CHAT_RESPONSE" | grep -o '"conversationId":"[^"]*"' | cut -d'"' -f4)
echo ""

# Chat — follow-up using conversation context
echo ""
echo "9. Follow-up Question (same conversation)"
echo "──────────────────────────────"
echo "Q: Is there a sibling discount?"
curl -s -X POST "$BASE_URL/api/v1/chat" \
  -H "X-Tenant-Id: $TENANT_A" \
  -H "Content-Type: application/json" \
  -d "{\"conversationId\": \"$CONV_ID\", \"question\": \"Is there a sibling discount?\"}" | python3 -m json.tool 2>/dev/null
echo ""

# Chat — cross-category question
echo ""
echo "10. Cross-Category Question (no category filter)"
echo "──────────────────────────────"
echo "Q: What is the speed limit for school buses?"
curl -s -X POST "$BASE_URL/api/v1/chat" \
  -H "X-Tenant-Id: $TENANT_A" \
  -H "Content-Type: application/json" \
  -d '{"question": "What is the speed limit for school buses?"}' | python3 -m json.tool 2>/dev/null
echo ""

# Chat — REFUSAL (unanswerable question)
echo ""
echo "11. Refusal Test — Question NOT in Documents"
echo "──────────────────────────────"
echo "Q: What is the school's policy on bringing pets to school?"
curl -s -X POST "$BASE_URL/api/v1/chat" \
  -H "X-Tenant-Id: $TENANT_A" \
  -H "Content-Type: application/json" \
  -d '{"question": "What is the school policy on bringing pets to school?"}' | python3 -m json.tool 2>/dev/null
echo ""

# Tenant isolation test
echo ""
echo "12. Tenant Isolation — Tenant B sees no documents"
echo "──────────────────────────────"
curl -s "$BASE_URL/api/v1/documents" \
  -H "X-Tenant-Id: $TENANT_B" | python3 -m json.tool 2>/dev/null
echo ""

echo "Q: (Tenant B) What is the tuition fee for Class 6?"
curl -s -X POST "$BASE_URL/api/v1/chat" \
  -H "X-Tenant-Id: $TENANT_B" \
  -H "Content-Type: application/json" \
  -d '{"question": "What is the tuition fee for Class 6?"}' | python3 -m json.tool 2>/dev/null
echo ""

# Conversation history
echo ""
echo "13. Retrieve Conversation History"
echo "──────────────────────────────"
curl -s "$BASE_URL/api/v1/conversations/$CONV_ID" \
  -H "X-Tenant-Id: $TENANT_A" | python3 -m json.tool 2>/dev/null
echo ""

# Streaming chat
echo ""
echo "14. Streaming Chat (SSE)"
echo "──────────────────────────────"
echo "Q: Explain the late fee payment policy"
curl -s -N -X POST "$BASE_URL/api/v1/chat/stream" \
  -H "X-Tenant-Id: $TENANT_A" \
  -H "Content-Type: application/json" \
  -d '{"question": "Explain the late fee payment policy"}' 2>/dev/null | head -30
echo ""
echo ""

echo "============================================"
echo "  Demo Complete ✓"
echo "============================================"
echo ""
echo "Swagger UI: $BASE_URL/swagger-ui.html"
echo "Prometheus: $BASE_URL/actuator/prometheus"
echo "Health:     $BASE_URL/actuator/health"
