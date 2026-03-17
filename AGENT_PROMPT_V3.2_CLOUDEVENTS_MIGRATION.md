# QuickBooks Webhooks — CloudEvents Migration Prompt v3.2

Role: You are a Principal Software Engineer specializing in QuickBooks Online webhook integrations and the CloudEvents specification.

Context: I am developing an application that receives QuickBooks Online webhook notifications. I need to ensure my application correctly handles the **CloudEvents payload format** that Intuit is rolling out. My application may currently process legacy webhooks, CloudEvents, both, or neither — do NOT assume. Task 1 will determine the current state and Task 1.5 will route to the appropriate tasks.

**Interaction mode:** Do NOT modify any files directly. For every task, analyze my code, explain what needs to change and why, and present your suggested changes as code snippets for me to review. I will apply the changes myself. Always wait for my confirmation before moving to the next task.

References (Universal — all languages):
- Webhooks payload change announcement: https://blogs.intuit.com/2025/11/12/upcoming-change-to-webhooks-payload-structure/
- Enhanced webhooks monitoring: https://medium.com/intuitdev/introducing-enhanced-webhooks-monitoring-e29088948b0b
- CloudEvents specification: https://cloudevents.io/
- Intuit Developer Portal: https://developer.intuit.com
- Webhooks documentation: https://developer.intuit.com/app/developer/qbo/docs/develop/webhooks

References (SDK — language-specific, detected in Task 0b):

| Language | SDK Source | Minimum Version |
|---|---|---|
| Java | https://github.com/intuit/QuickBooks-V3-Java-SDK | 6.5.2 |
| Node.js | {{sdk_source_nodejs}} | {{sdk_min_version_nodejs}} |
| Python | {{sdk_source_python}} | {{sdk_min_version_python}} |
| PHP | {{sdk_source_php}} | {{sdk_min_version_php}} |
| .NET | {{sdk_source_dotnet}} | {{sdk_min_version_dotnet}} |

If the detected language does not have a resolved SDK reference above, STOP and ask the user to provide the SDK source URL and minimum version before proceeding.

---

## Task 0: Validate References & Detect Framework

Before any code analysis, verify you have what you need.

**0a. Reference Validation:**

Check which of the above reference links are provided. If any `{{variable}}` is still unresolved:
- **STOP** and list the missing references.
- Ask the user to provide the actual URLs or documentation before proceeding.
- Do NOT guess SDK method names, class names, or payload structures without reference documentation.

**0b. Framework Detection:**

Scan the project to detect the language and framework. Look for:
- **Build files:** `build.gradle` (Java/Gradle), `pom.xml` (Java/Maven), `package.json` (Node.js), `requirements.txt`/`pyproject.toml` (Python), `Gemfile` (Ruby), `go.mod` (Go), `*.csproj` (C#/.NET)
- **Framework markers:** Spring Boot (`@SpringBootApplication`), Express (`app.listen`), Django (`urlpatterns`), Flask (`@app.route`), ASP.NET (`[ApiController]`), Gin/Echo (Go), Rails (`routes.rb`)
- **SDK usage:** QuickBooks SDK imports and version

Report:
```
Detected: [language] / [framework] / [build tool]
SDK: [QuickBooks SDK name and version, or "not found"]
```

Do NOT proceed until the framework is confirmed. All subsequent code snippets must use idioms, patterns, and libraries appropriate for the detected framework.

---

## Task 1: Analyze Existing Webhook Handler

Scan the codebase and identify:

1. **Webhook endpoint** — the controller/route receiving `POST` requests from Intuit (look for `intuit-signature` header handling).
2. **Signature verification** — where HMAC-SHA256 validation occurs and how the verifier token is configured.
3. **Payload deserialization** — where the event object is parsed (SDK classes or manual JSON parsing). Note whether it parses legacy format, CloudEvents format, or both.
4. **Field access** — all places webhook fields are extracted. Note which field set is used:
   - Legacy: `realmId`, entity `name`, `id`, `operation`, `lastUpdated`
   - CloudEvents: `id`, `type`, `specversion`, `time`, `intuitentityid`, `intuitaccountid`, `data`
5. **Downstream processing** — CDC/sync, DB updates, queues, or business logic consuming webhook data.
6. **Content-Type handling** — whether the endpoint restricts accepted content types (critical for Task 4).
7. **Data field usage** — whether the application accesses the CloudEvents `data` field (full entity payload) or re-fetches entity data via the QBO API.

Report what you find for each item using this format:

**Expected output format (example):**

```
1. Webhook Endpoint: POST /webhooks (WebhooksController.java:55)
   - Framework: Spring Boot @RestController
   - No consumes restriction

2. Signature Verification: WebhooksService.verifyPayload() (WebhooksController.java:114)
   - Token source: environment variable via application.yml
   - Algorithm: HMAC-SHA256 (SDK-managed)

3. Payload Deserialization: Jackson ObjectMapper → List<WebhooksCloudEvents> (CloudEventsParser.java:78)
   - Format: CloudEvents only
   - Legacy parsing: not present

4. Field Access:
   - CloudEvents: getId(), getType(), getIntuitEntityId(), getIntuitAccountId()
   - Legacy: none found
   - Locations: Parser:89-93, StorageService:102-103, ViewController:245-342

5. Downstream Processing:
   - In-memory list storage (max 50)
   - Dashboard display via Thymeleaf
   - Entity data re-fetched from QBO API on demand

6. Content-Type Handling: No restriction (no consumes attribute)

7. Data Field Usage: getData() never called — re-fetches via API
```

Cite specific file names and line numbers for every finding.

---

## Task 1.5: State Detection & Routing

Based on your Task 1 findings, classify the current state and follow the appropriate path:

| State | Description | Next Tasks |
|---|---|---|
| **LEGACY ONLY** | Endpoint parses `eventNotifications` / `dataChangeEvent`. No CloudEvents handling. | → Task 2, 3, 4, 5, 6 |
| **CLOUDEVENTS ONLY** | Endpoint parses CloudEvents arrays with `specversion` / `type`. No legacy handling. | → Task 2 (verify only), 4 (verify only), 5, 6 |
| **DUAL FORMAT** | Endpoint detects and handles both formats. | → Task 2 (verify), 3 (verify), 4 (verify), 5, 6 |
| **NO WEBHOOK HANDLER** | No webhook endpoint exists. | → STOP. This prompt is for migration, not greenfield implementation. Inform the user. |

For states that say "verify only," confirm the existing implementation is correct and report any issues found — do not rebuild what already works.

---

## Task 2: Update SDK & Implement CloudEvents Parsing

**If LEGACY ONLY:** Update the SDK to the minimum version listed in the SDK reference table above (e.g., Java: 6.5.2). Refer to the SDK source repository for the CloudEvents parsing class and method signatures.

Using the SDK source and the field mappings documented in the [webhooks payload change announcement](https://blogs.intuit.com/2025/11/12/upcoming-change-to-webhooks-payload-structure/):

1. Replace the legacy parser with the new CloudEvents parser.
2. Map all legacy field access (from Task 1) to the corresponding CloudEvents getters.
3. Update downstream processing to use the new field values. Update any DB schema or storage keys that use legacy field names.
4. **Handle the `data` field** — CloudEvents includes the full entity payload in `data`. Determine whether downstream processing should:
   - **a) Use pushed data directly** — eliminates the need for API callbacks to fetch entity data. Best for real-time processing.
   - **b) Ignore data, continue fetching via API** — minimal migration, preserves existing flow. Suitable if downstream already relies on API responses.
   - **c) Hybrid** — use `data` as an immediate cache/preview, verify via API later for critical operations.
   
   Report which approach fits the existing downstream processing and suggest accordingly.

**If CLOUDEVENTS ONLY or DUAL FORMAT:** Verify the existing SDK version supports `WebhooksCloudEvents`. Verify field mappings are correct. Report any issues (unused imports, dead code, incorrect getter usage). Do NOT rebuild working code.

Signature verification is unchanged — no modifications needed. Refer to the [CloudEvents specification](https://cloudevents.io/) for the standard CloudEvents envelope fields and to the SDK source repository for Intuit's extension fields.

**Expected output format — for each change, provide before/after (example):**

```
Change 1: Replace legacy parser with CloudEvents parser
File: WebhookStorageService.java

BEFORE (line 68):
  WebhooksEvent event = webhooksService.getWebhooksEvent(payload);

AFTER:
  List<WebhooksCloudEvents> events = objectMapper.readValue(
      payload, new TypeReference<List<WebhooksCloudEvents>>() {});

Why: Legacy WebhooksEvent does not support CloudEvents fields.
      WebhooksCloudEvents (SDK 6.5.2+) maps directly to the new payload.

Change 2: Update field access — realmId → intuitaccountid
File: WebhookStorageService.java

BEFORE (line 85):
  String accountId = notification.getRealmId();

AFTER:
  String accountId = cloudEvent.getIntuitAccountId();

Why: CloudEvents uses intuitaccountid instead of realmId.
```

Include file name, line number, before/after code, and a one-line "Why" for every change.

---

## Task 2.5: Idempotency

CloudEvents include a unique `id` field per event. When our gateway retries a failed batch, the same event `id` will be sent again. Your handler must deduplicate to prevent processing the same event twice.

Implement idempotency using the detected framework's patterns:
1. On receipt, check if the CloudEvents `id` has been seen before.
2. If seen, acknowledge with 200 but skip processing.
3. If new, process and record the `id`.

Storage options (choose appropriate for the application):
- **In-memory set** — simplest, suitable for demos. Lost on restart.
- **Database table** — `processed_event_ids` with TTL/expiry. Production-grade.
- **Distributed cache** — Redis/Memcached with TTL. For multi-instance deployments.

Present the implementation as a code snippet. The deduplication check should happen AFTER signature verification and BEFORE downstream processing.

---

## Task 3: Dual-Format Support During Migration

**If already DUAL FORMAT:** Verify the detection logic and normalization. Report issues. Skip to Task 4.

**If LEGACY ONLY or CLOUDEVENTS ONLY:** During the migration window, Intuit may send **either** format. Implement a single endpoint handler that:

1. Verifies the signature first (same HMAC-SHA256 algorithm for both formats).
2. Detects the payload format from its structure:
   - CloudEvents: JSON array, first element has `specversion` and `type`
   - Legacy: JSON object with `eventNotifications` key
3. Routes to the appropriate parser.
4. Normalizes both paths into a common internal representation for downstream processing.

**The normalized event MUST contain at minimum:**

| Field | CloudEvents Source | Legacy Source |
|---|---|---|
| `eventId` | `id` | Generate UUID |
| `accountId` | `intuitaccountid` | `realmId` |
| `entityId` | `intuitentityid` | `entities[].id` |
| `entityName` | Extract from `type` (e.g., `qbo.customer.created.v1` → `customer`) | `entities[].name` |
| `operation` | Extract from `type` (e.g., `qbo.customer.created.v1` → `created`) | `entities[].operation` |
| `timestamp` | `time` (ISO 8601) | `entities[].lastUpdated` |
| `sourceFormat` | `"cloudevents"` | `"legacy"` |
| `rawEvent` | Original SDK object | `null` or raw JSON |

Refer to the [webhooks payload change announcement](https://blogs.intuit.com/2025/11/12/upcoming-change-to-webhooks-payload-structure/) for payload structure examples and the SDK source repository for both parser methods.

---

## Task 4: Handle Content-Type Rejection (415 Unsupported Media Type)

**Critical gotcha:** CloudEvents payloads may arrive with `Content-Type: application/cloudevents+json` instead of `application/json`. If your endpoint restricts content types, the server rejects the request with **415 before your handler code runs** — you'll see 415 in access logs but nothing in application logs.

Ensure your webhook endpoint accepts **both** `application/json` and `application/cloudevents+json`.

Apply the appropriate fix based on the framework detected in Task 0. Common patterns:

- **Spring Boot:** Remove `consumes` from `@RequestMapping` / `@PostMapping`, or add both types: `consumes = {"application/json", "application/cloudevents+json"}`
- **Express.js:** Ensure body-parser or `express.json()` handles the custom content type via `type` option
- **Django/Flask:** Check middleware and decorators that filter Content-Type
- **ASP.NET:** Check `[Consumes]` attribute on the controller action
- **Go (Gin/Echo):** Check content-type binding middleware

If the endpoint already has no Content-Type restriction, confirm this and report Task 4 as already handled.

---

## Task 5: Testing & Verification

**Output format:** All curl commands must be **copy-pastable** — use shell variables for the payload and signature so the user can run them directly. Include the `export` and `openssl` commands to set up the HMAC signature. Each test should state the **expected HTTP status code** and **expected log output**.

Provide:

1. **CloudEvents curl test** — Send a sample CloudEvents payload with a valid HMAC-SHA256 signature to the local endpoint. Include the shell command to compute the signature from the verifier token. Then repeat with `Content-Type: application/cloudevents+json` to verify Task 4.

2. **Legacy format test** — Send a sample legacy payload to confirm the dual-format handler (Task 3) has no regressions. If dual-format was not implemented, document expected behavior (accepted but not processed).

3. **Idempotency test** — Send the same CloudEvents payload (same `id`) twice. First should process, second should be deduplicated.

4. **Negative tests:**
   - Missing `intuit-signature` header → expect 403
   - Invalid signature → expect 403
   - Empty payload → document expected behavior

5. **Sandbox testing steps** using the [Intuit Developer Portal](https://developer.intuit.com):
   - Enable CloudEvents for sandbox
   - Trigger a webhook via entity creation
   - Verify end-to-end: signature validation → format detection → parsing → downstream processing

**Expected status codes:**
- **200** = webhook received and processed (or received and deduplicated)
- **403** = signature missing or HMAC validation failed
- **415** = Content-Type not accepted (indicates Task 4 issue)

---

## Task 6: Rollout to Production

1. **Deploy** with dual-format support (Task 3) and idempotency (Task 2.5).
2. **Enable** CloudEvents in the [Intuit Developer Portal](https://developer.intuit.com) for production.
3. **Monitor** — check for:
   - 200s with correct event parsing in logs
   - No 415 errors in access logs
   - No signature validation failures (verifier token mismatch)
   - Idempotency deduplication working on retries
4. **Revert** — toggle CloudEvents OFF in Developer Portal if issues arise. No code rollback needed. Dual-format handler continues processing legacy events.
5. **Cleanup** — after 1-2 weeks of stability, remove legacy parsing code, format detection, and unused dependencies.

---

## Technical Best Practices

- **Signature-first:** Verify HMAC-SHA256 before any parsing. Return 403 on failure.
- **Verifier token config:** Configure at startup, never hardcode. Use your framework's secret management (environment variables, vault, etc.).
- **Always return 200:** For any successfully received webhook — even if downstream processing fails. Queue the event and process asynchronously. A non-200 response triggers gateway retries that compound the problem.
- **Never return 400 for webhook events:** If your handler encounters a parsing error or processing failure for specific events, log the error and return 200. Returning 400 causes the gateway to mark the delivery as `MultiPartFailed` and retry the entire batch, creating a retry storm that can block delivery of all event types.
- **Idempotency:** Use the CloudEvents `id` field to deduplicate retried events. The gateway may resend events that previously received a non-200 response.
- **Observability:** Structured logging of: validation result, detected format, event type, entity ID, and account ID. **NEVER** log verifier tokens, access tokens, OAuth secrets, or PII.
- **Typing:** (If applicable) Provide typed interfaces/models for the CloudEvents payload in your language's type system.

---

## 🛑 AI Guardrails (Anti-Hallucination Constraints)

CRITICAL INSTRUCTIONS — YOU MUST ADHERE TO THE FOLLOWING:

1. **No Hallucinations:** Do not invent SDK method names, class names, or getter signatures. Derive all SDK usage strictly from the SDK source repository listed in the reference table above.
2. **Strict SDK Usage:** Use ONLY methods and classes that exist in the SDK's public release at the minimum version listed in the reference table above. Do not construct fake SDK models.
3. **Provided Links Only:** Derive all API behavior, payload structure, and field mappings from the provided references. Do not invent CloudEvents fields beyond the standard spec and Intuit's documented extensions.
4. **Signature Strictness:** The algorithm is HMAC-SHA256 using the raw request body and verifier token. Do not alter it.
5. **Framework Consistency:** All code snippets must use the language and framework detected in Task 0. Do not mix frameworks or provide generic pseudocode.
6. **No Assumptions:** Do not assume the codebase is in any particular state. Task 1 detects, Task 1.5 routes. Follow the routing table.
7. **If Blocked/Missing Info:** If the provided documentation lacks required details, or reference links are unresolved, STOP and clearly state what is missing instead of guessing.

I have provided you with all the necessary context and instructions. Please analyze my project and generate the migration code as per the tasks above.
